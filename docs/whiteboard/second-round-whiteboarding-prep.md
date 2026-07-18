# Sinch 第二轮 Whiteboarding 准备：SMS Router 扩展场景

来源：结合已提交的 [sinch-sms-router](C:\Users\hy120\Downloads\Coding\company\Sinch\sinch-sms-router) 代码复核，以及 [lessons/lesson-14-sinch-sms-router-system-design.md](questions/lessons/lesson-14-sinch-sms-router-system-design.md)、[message-system-one-page-story-summary.md](questions/message-system-one-page-story-summary.md)。

## 为什么这轮大概率不是纯 greenfield 系统设计

- Take-home PRD 原文写明："Usage of AI tools are highly encouraged. **You are expected to extend the solution during the interview.**"
- [interview-process.md](../interview-process.md) 里 Nicole 的描述是"给一个 scenario，考察实时解决问题的方式，包括会用什么 AI 工具、怎么处理 inefficiency"。
- 结合起来看，白板环节更可能是"在你已有的 SMS router 代码上做 live 扩展或修 bug"，而不是从零画一个大型分布式系统。Lesson 14 的完整设计仍然有用，但应该作为"如果被问到怎么生产化/怎么扩展"的知识储备，不是照搬的答案模板。

## 当前实现的真实状态（复核代码后）

- 存储：`ConcurrentHashMap`，纯内存，重启即丢失（README 已明确写出这个 limitation）。
- AU 轮转：`CarrierRouter` 用 `AtomicReference<Carrier>.getAndUpdate`，单 JVM 实例内线程安全。
- Opt-out：`OptOutService` 用 `ConcurrentHashMap.newKeySet()`，`isOptedOut` 和 `optOut` 各自原子，但两者组合使用时存在竞态（见场景 3）。
- 发送逻辑是纯同步内存操作，`CarrierRouter.route()` 只是选一个运营商名字，**没有真实网络调用**，所以当前代码天然不会遇到超时/失败。
- `SendMessageRequest` 没有 idempotency key 字段，重复 POST 会被当成全新消息处理。
- 没有重试、没有 DLQ、没有 provider adapter 抽象——这些都是"当前范围内没做"，而不是"做错了"，白板环节大概率是让你现场加上其中一两项。

## 场景 1：防止重复发送（Idempotency）

### 可能的问法

"如果客户端网络抖动，同一个 `POST /messages` 因为超时重试了两次，会发生什么？你会怎么改？"

### 现状分析

`MessageService.send()` 每次调用都会：生成新 UUID → 判断 opt-out → 如果是 AU 号码且未 opt-out，消耗一次轮转 slot → 存为新记录。

结果：重复请求 = 两条不同 `id` 的独立 `SENT` 记录，AU 轮转还被多消耗一次，业务上等于重复发了两条短信。

### 完善设计

1. `SendMessageRequest` 增加可选的 `Idempotency-Key`（推荐放 HTTP header，比放 body 字段更符合惯例）。
2. 用 `idempotencyKey`（可选再加上 destinationNumber 做租户/号码维度隔离）建唯一索引。
3. 首次请求：正常处理，把 `idempotencyKey -> messageId` 的映射和请求 fingerprint 一起存下来。
4. 相同 key、相同 payload 的重复请求：直接返回已有的 `Message`，不重新执行 send 逻辑，不消耗 carrier 轮转。
5. 相同 key、不同 payload：返回明确冲突（`409`），不能悄悄合并成一条。

### 对应课程知识点

- [lesson-04-duplicate-messages-and-business-idempotency.md](questions/lessons/lesson-04-duplicate-messages-and-business-idempotency.md)：幂等不是"代码只跑一次"，是"重复执行产生相同可观察结果"，靠稳定 operation key + 唯一约束实现。
- One-liner：不能因为它是"一条新消息"就重新执行一次有副作用的操作。

### 讲解要点

先说现状（完全没有幂等保护），再说风险（重复发送 + AU 轮转序列被污染），再给方案（idempotency key + fingerprint 校验 + 幂等表/唯一索引），最后强调"重复执行副作用 vs 返回已有结果"的区别——这是 Lesson 4 的核心判断。

## 场景 2：把"选运营商"换成真实 provider 调用

### 可能的问法

"现在 `CarrierRouter.route()` 只是选了个名字，如果这是真实调用 Telstra/Optus API，网络超时了怎么办？"

### 现状分析

当前是同步内存操作，没有网络 I/O，所以不存在超时/失败的概念。一旦换成真实 HTTP 调用，会引入：网络延迟、超时、5xx、429，以及最棘手的"请求发出去了，但响应没收到"（provider 可能已经接受了，也可能没有）。

### 完善设计

1. `MessageStatus` 增加中间态：区分 `submission` 状态（`SUBMITTING` -> `PROVIDER_ACCEPTED` / `SUBMISSION_FAILED_RETRYABLE` / `SUBMISSION_UNKNOWN`）和现有的最终态。
2. Provider adapter 把结果规范化成：`ACCEPTED(externalReference)` / `REJECTED(permanentReason)` / `RETRYABLE(errorClass)` / `UNKNOWN(timeout)`。
3. 超时归类为 `UNKNOWN`，**不能自动重试**——因为不知道 provider 是否已经把短信发出去了。应该先查询 provider（如果支持）或等待 callback，再决定要不要重发。
4. 如果 provider 支持幂等 key，重试和原始请求要复用同一个 key，这样即使真的重发也不会在 provider 侧产生第二次真实发送。

### 对应课程知识点

- [lesson-03-message-delivery-semantics.md](questions/lessons/lesson-03-message-delivery-semantics.md)：at-least-once 只覆盖 transport 层，不能保证外部副作用只发生一次。
- [lesson-07-state-machines-and-eventual-consistency.md](questions/lessons/lesson-07-state-machines-and-eventual-consistency.md)：submission 状态和 delivery 状态要分开建模，`PENDING`/`UNKNOWN` 是诚实的中间态，不是失败也不是成功。
- Lesson 14 第 7 节：provider 调用的 exactly-once 边界。

### 讲解要点

先讲清楚现状是"假调用"（没有网络 I/O），一旦换成真实调用就必然要面对超时和不确定性；再讲为什么"超时后自动重试"是危险的操作（可能已经发出短信，重试等于重复发送）；最后讲怎么用 `UNKNOWN` 状态 + 幂等 key + 查询/callback 来收敛这个不确定性。

## 场景 3：Opt-out 和 Send 之间的竞态

### 可能的问法

"如果一个用户在你处理他的发送请求的同时提交了 opt-out，会发生什么？"

### 现状分析

```java
boolean optedOut = optOutService.isOptedOut(validatedNumber);
Carrier carrier = optedOut ? null : carrierRouter.route(validatedNumber);
```

`isOptedOut` 和 `optOut` 各自是原子操作（底层是 `ConcurrentHashMap.newKeySet()`），但"读取 opt-out 状态"和"据此决定要不要发送"这两步之间存在窗口——这是经典的 check-then-act 竞态：

- 线程 A：`isOptedOut(number)` 返回 `false`
- 线程 B：`optOut(number)` 在此刻完成
- 线程 A：继续往下走，把消息路由并存成 `SENT`

结果：号码已经 opt-out 了，但这条消息还是被当成 `SENT` 发出去了。这是能在真实并发下触发的 bug，不是纸上谈兵。

### 完善设计

1. 单机内存场景下，最直接的修法是把"检查 opt-out + 路由 + 落状态"这三步收窄成对同一个 `destinationNumber` 的原子操作，比如对号码做细粒度锁（不是全局锁），或者把 opt-out 状态和消息路由放进同一个受保护的临界区。
2. 如果之后换成真实数据库，可以用条件写代替应用层锁：`INSERT INTO message (...) WHERE NOT EXISTS (SELECT 1 FROM opt_out WHERE number = ? FOR UPDATE)`，让数据库的行锁/唯一约束做最后一道闸。
3. 更重要的是先跟面试官澄清业务容忍度：这种窗口期极短的竞态消息漏发，业务上是否可以接受？如果可以接受，就不需要为了理论上的完美去引入额外锁开销；如果不能接受（比如涉及监管合规的短信），才值得花这个成本。

### 对应课程知识点

- [lesson-05-message-ordering-concurrency-and-race-conditions.md](questions/lessons/lesson-05-message-ordering-concurrency-and-race-conditions.md)：不追求全局锁，而是把顺序保证收窄到具体的业务 key，用细粒度锁或条件更新做最后一道闸。

### 讲解要点

先明确指出这是 check-then-act 竞态，能在高并发下真实触发；再给出"收窄到 per-number 锁或条件写"的方案；最后主动澄清业务容忍度，而不是自己假设"必须做到完美"就过度设计。

## 场景 4：多实例部署后，AU 轮转还对吗

### 可能的问法

"如果这个服务部署了两个实例，AU 号码的 Telstra/Optus 轮转还会正确交替吗？"

### 现状分析

`CarrierRouter` 的 `AtomicReference<Carrier>` 保存在单个 JVM 实例的内存里。两个实例各自维护自己的状态，互相不知道对方刚刚发了什么。结果：两个实例可能同时都认为"下一个该发 Telstra"，全局序列不再严格交替，可能连续出现两次 Telstra。

### 完善设计

1. 先反问面试官：轮转的真实业务目的是什么？如果只是粗略负载均衡（大致 50/50 分给两个 carrier），当前设计在多实例下退化为"近似均衡"，很可能是可以接受的。
2. 如果业务要求严格全局交替，需要把这个状态外置到共享存储（例如 Redis 的原子 `INCR` 取模，或者数据库一行记录配合条件更新），用分布式原子操作替代本地 `AtomicReference`。
3. 权衡要讲清楚：外置状态会引入额外的网络调用和一个新的单点依赖，换来的是严格顺序保证——这个代价是否值得，取决于业务对"严格交替"是不是真实刚需，而不是想当然去做。

### 对应课程知识点

- Lesson 5 + Lesson 14 第 20 节的原则："不要一开始就上复杂方案，先问清楚约束再决定要不要引入分布式协调。"

### 讲解要点

先反问轮转的真实目的（负载均衡 vs 严格公平），再说明当前设计在单实例下完全正确、多实例下会退化，最后给出"如果需要严格保证"的外置方案并说明代价，而不是不问就直接给一个重方案。

## 场景 5：如何持久化，重启不丢数据

### 可能的问法

"README 里写了重启会丢失所有状态，如果这是真实生产系统，你会怎么改？"

### 完善设计

1. 换成真实数据库（Postgres/MySQL），拆成 `message` 表和 `opt_out` 表。
2. `message` 表在 `idempotencyKey` 上建唯一约束（复用场景 1 的设计），从数据库层面兜底幂等。
3. 如果之后要接入真实 provider 并做异步处理，要提前想到 dual-write 问题：数据库状态更新和"发一条消息去 provider/队列"是两个独立操作，中间有失败窗口。可以用 transactional outbox：本地事务里同时写业务状态和 `outbox_event`，再由独立 relay 可靠发布，Consumer 侧仍要幂等，outbox 只是保证"意图不丢"，不是免死金牌。

### 对应课程知识点

- [lesson-08-database-message-consistency-and-transactional-outbox.md](questions/lessons/lesson-08-database-message-consistency-and-transactional-outbox.md)：数据库更新和发消息是 dual write，outbox 解决"意图丢失"，不解决"重复发布"。

### 讲解要点

先说这是当前 README 里已经写明的 explicit limitation，说明这是权衡取舍不是遗漏；再给出"如果要生产化"的路径：先做 DB 持久化，只有引入异步 provider 调用时才需要 outbox，不要为了"看起来完整"而在范围内堆砌不需要的组件。

## 场景 6：如何排查"客户说短信没送达"

### 可能的问法

"生产环境里客户投诉说发的短信没收到，你怎么查？"

### 完善设计

1. 拿到 `messageId` 或 `destinationNumber` + 大致时间窗口。
2. 调用 `GET /messages/{id}` 看当前 `status`：`SENT` / `BLOCKED` / 404。
3. 如果是 `BLOCKED`，检查该号码是否被误标为 opt-out（查 opt-out 记录/操作日志，确认是谁、什么时候加进去的）。
4. 如果是 `SENT`，要明确当前系统里 `SENT` 的语义是"我们接受并完成了路由"，不是"手机已经收到"——真实场景下下一步要看 provider 的 delivery receipt 或做对账，这已经超出这个小项目当前范围，但值得主动说出来。
5. 用稳定 ID（`messageId`，如果之后加了 idempotency key 就是 `operationId`）串联日志，而不是靠临时调用栈去猜。

### 对应课程知识点

- [lesson-11-observability-and-production-incidents.md](questions/lessons/lesson-11-observability-and-production-incidents.md)：稳定 ID 串联链路排障。
- [lesson-09-reconciliation.md](questions/lessons/lesson-09-reconciliation.md)：`SENT` 不等于 `DELIVERED`，需要向 provider 核实或走对账。

### 讲解要点

先主动指出当前系统 `SENT` 的诚实语义边界，这本身就是一个好答案（说明你理解"发出去"和"送达"是两个不同事实）；再走排查步骤。

## 关于"会用什么 AI 工具、怎么处理 inefficiency"

[interview-process.md](../interview-process.md) 里明确说白板环节会考察这两点，不只是技术方案本身：

- 用 AI 工具（Claude Code / Copilot）快速定位这类并发/幂等 gap：比如让它扫一遍代码里的 check-then-act 模式，或者生成边界测试用例覆盖并发场景。
- 处理 inefficiency 的方式：先测量再优化，不要凭感觉猜瓶颈。比如如果引入真实 provider 调用后系统变慢，先确认瓶颈是在 provider 侧还是自己的路由逻辑，而不是无脑加并发线程。
- 呼应 Lesson 10 的结论：加并发要看下游最窄的瓶颈在哪，不是越多越快。

## Whiteboard 通用答题节奏（不管问哪个场景）

1. 先复述问题，确认理解——这本身就是面试官在观察的"real-time 解决问题的方式"。
2. 说清楚当前代码的真实行为，不要凭印象猜，直接说"让我先看一下这段逻辑现在是怎么做的"。
3. 指出风险或 gap。
4. 给方案；如果有多个方案，先说 tradeoff 再选。
5. 主动说"这里需要先跟你确认一下业务容忍度/约束"，而不是自己悄悄假设。

## 不要这样答

- 不要一上来就搬 Lesson 14 里 multi-region、Kinesis 那套重型方案——这是一个本地小项目的现场扩展，先解决眼前问题。
- 不要说"当前代码没有这个问题",除非已经真的看过代码确认过。
- 不要把"AI 工具会用"当成走过场敷衍过去，这是白板环节明确会问的维度。
</content>
