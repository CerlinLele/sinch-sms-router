# Sinch SMS Router Live Coding 扩展方向

结合 take-home PRD 和当前实现，live coding 最可能不是从零开发，而是在现有结构上增加一条业务规则、一个状态流转或一个接口。

## 第一梯队：最值得优先准备

| 扩展方向 | 面试官可能怎么问 | 当前代码切入点 | 关键测试 |
| --- | --- | --- | --- |
| 消息状态流转 | 发送后先是 `PENDING`，然后更新成 `SENT` / `DELIVERED` | `MessageService`、`MessageRepository`、`MessageController` | 合法/非法状态转换、未知 ID、终态不可修改 |
| Delivery callback | 运营商通过 webhook 通知 delivery result | 新增 `POST /messages/{id}/delivery` | `SENT -> DELIVERED`、重复 callback 幂等 |
| Opt-in / 取消退订 | 用户能重新订阅吗？ | `OptOutService`、`OptOutController` | 重复 opt-in、未退订号码、恢复后可以发送 |
| 扩展路由规则 | 新增 Vodafone，或者按权重分流 | `CarrierRouter`、`Carrier` | 顺序、权重边界、非 AU 不影响 AU 状态 |
| Carrier 故障切换 | Telstra 不可用时走 Optus | `CarrierRouter` 或新增 carrier availability/gateway 抽象 | primary failure、fallback、全部失败 |
| AU/NZ 精确号码校验 | AU 和 NZ 格式需要真正不同 | `PhoneNumberValidator` | 国家码、长度、移动号前缀、非法本地格式 |

其中状态流转是最明显的扩展点：`MessageStatus` 已经定义了 `PENDING` 和 `DELIVERED`，但 `MessageService` 目前发送时直接落为 `SENT` 或 `BLOCKED`。README 也明确说 delivery simulation 是未来扩展，因此这很可能被拿来现场实现。

推荐准备这样的状态机：

```text
PENDING -> SENT -> DELIVERED
    \----------> FAILED（如果现场要求新增）

PENDING -> BLOCKED
BLOCKED、DELIVERED、FAILED 为终态
```

不要允许任意 status 覆盖，否则面试官很可能追问为什么 `DELIVERED` 可以退回 `PENDING`。

## 第二梯队：适合考察 Senior 设计能力

### 1. 引入真实 Carrier gateway

可能要求把“选择 carrier”和“调用 carrier”分开：

```java
interface SmsGateway {
    SendResult send(Message message);
}
```

例如 `TelstraGateway`、`OptusGateway`、`SparkGateway`。流程变成：

```text
校验 -> 检查 opt-out -> 保存 PENDING -> 选择 carrier
-> 调用 gateway -> 更新 SENT / FAILED
```

这能考察依赖倒置、异常处理和测试替身。现场不需要真的请求外部 API，通常 fake gateway 就够。

### 2. Retry 和 fallback

常见要求：

- 运营商超时重试两次。
- Telstra 失败后尝试 Optus。
- 业务拒绝不重试，网络错误才重试。
- 不允许重复发送已经成功的消息。

需要区分：

- transient failure：可重试。
- permanent failure：不可重试。
- provider 已接收但客户端超时：存在重复发送风险，需要 idempotency key。

### 3. 幂等发送

可能增加 `Idempotency-Key` header，防止客户端重试产生两条短信。

最小实现可以维护：

```text
idempotency key -> message ID
```

相同 key 和相同请求返回原消息；相同 key 但请求内容不同返回 `409 Conflict`。还要考虑“检查 key”和“创建消息”必须是原子操作。

### 4. 并发安全

现有实现已经用了 `ConcurrentHashMap` 和 `AtomicReference`，面试官可能进一步问：

- 100 个并发 AU 请求是否仍严格交替？
- opt-out 和 send 同时发生时，语义是什么？
- 多实例部署后 `AtomicReference` 是否还有效？
- 服务重启后路由顺序和 opt-out 是否丢失？

`CarrierRouter` 对单实例并发交替是安全的，但多实例并不保证全局轮询；这时通常需要数据库、Redis，或接受“每实例轮询”的明确语义。

### 5. 持久化

将 in-memory repository 换成 JPA/H2/PostgreSQL。当前已有 `MessageRepository` 接口，所以替换存储的边界比较清楚。

可能要求：

- 服务重启后消息仍存在。
- opt-out 也持久化。
- optimistic locking，防止并发状态覆盖。
- 数据库唯一约束保证幂等。

现场如果时间短，不建议主动大改 JPA；只有题目明确要求时再做。

## 第三梯队：API 和产品功能扩展

### Opt-out 管理

除了取消退订，还可能增加：

- `GET /optout/{phoneNumber}`：查询状态。
- `DELETE /optout/{phoneNumber}`：恢复订阅。
- 保存退订时间、来源和原因。
- 全局退订与 sender-specific 退订。
- 已经 `SENT` 的消息不受后续 opt-out 影响。
- 仍为 `PENDING` 的消息是否应被阻止，需要先澄清。

当前 `OptOutService` 只需增加 `remove` / `isOptedOut` API，就能做一个很合适的 15-20 分钟现场题。

### 查询和分页

可能增加：

```http
GET /messages?status=BLOCKED&carrier=Telstra&page=0&size=20
```

需要考虑：

- repository 增加查询能力。
- 稳定排序，例如创建时间倒序。
- 非法 status/carrier 返回 `400`。
- 空结果返回 `200 []`。
- 不应返回短信 content 等敏感数据，除非明确需要。

### 批量发送

例如：

```http
POST /messages/batch
```

主要设计问题：

- 一个号码失败是否导致整个 batch 失败。
- 返回 `207 Multi-Status`，还是每项独立结果。
- AU carrier alternation 是否按有效且未退订消息计算。
- batch 大小限制。
- 是否异步处理。

### Scheduled messages

增加 `send_at`：

- 未来时间保存为 `PENDING`。
- 到期后再检查 opt-out，还是创建时检查。
- 时区统一使用 UTC。
- 测试中注入 `Clock`，避免依赖真实时间。

### 短信长度与分段

可能要求：

- GSM-7：160 字符。
- Unicode/UCS-2：70 字符。
- 超长内容拆成多段。
- 限制最大 segment 数或计算费用。

重点不是背完整 GSM 字符表，而是先确认题目希望“简单字符长度”还是“真实短信编码规则”。

## 路由方面还能怎样扩展

当前 AU 是简单全局交替。可能变化为：

1. 加 carrier：Telstra -> Optus -> Vodafone。
2. 加权轮询：例如 50% / 30% / 20%。
3. 按号码前缀选择 carrier。
4. 按成本选择最便宜 carrier。
5. 按 carrier 健康状态排除故障节点。
6. 按消息类型或客户等级选择线路。
7. 配置文件驱动规则，而不是写死在 Java 中。
8. 每个国家独立维护轮询状态。
9. sticky routing：同一号码始终选择相同 carrier。
10. capacity limit：carrier 达到速率限制后选择下一个。

现场实现时，不建议一开始就建立复杂规则引擎。先提取最小的 `RoutingStrategy` 或配置映射即可。

## 错误处理扩展

现有 `ApiExceptionHandler` 已经统一了错误结构。可能要求：

- 增加 `409 INVALID_STATUS_TRANSITION`。
- carrier timeout 映射为 `503`。
- 增加 `timestamp`、`path`、`trace_id`。
- 多字段校验一次返回所有错误。
- 不向客户端泄露内部 exception message。
- 无效 enum 与 malformed JSON 使用不同错误码。

一个容易被追问的点是：退订号码当前仍返回 `201 Created`，并保存为 `BLOCKED`。这是合理的，因为系统记录了一次发送尝试；如果改成 `403`，就可能无法通过 status API 查询这次被阻止的尝试。需要能够解释这个取舍。

## 测试型 Live Coding

面试官也可能不给新功能，而要求：

- 为并发 AU routing 补测试。
- 为 opt-out 幂等性补测试。
- 为非法状态转换补测试。
- 修一个已有 failing test。
- 把 controller 测试从手工字符串解析改成 `ObjectMapper`。
- 为 repository 写 contract test。
- mock gateway，验证失败时消息状态。
- 使用参数化测试覆盖各种电话号码。

这种题通常在看是否先定义行为，再做最小实现，而不是一次重构整个项目。

## 最推荐的实战准备顺序

如果时间有限，优先练这四道：

1. 增加 `PATCH /messages/{id}/status`，实现合法状态机。
2. 增加 `DELETE /optout/{phoneNumber}`，允许重新订阅。
3. 给 routing 增加 Vodafone 或 weighted round-robin。
4. 引入 fake `SmsGateway`，处理成功、失败和 fallback。

每道都按同一个节奏：

```text
确认业务语义
-> 先补 service test
-> 最小实现
-> 补 controller test
-> 跑相关测试
-> 解释并发、持久化和 production 限制
```

## 总结

状态生命周期、可扩展路由、opt-in、carrier failure/fallback 是这份项目最有可能的四个 live coding 方向，其中状态生命周期概率最高。
