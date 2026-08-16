# 修改报告：Google Meet 建会返回结果安全规范化（避免 PENDING 卡死）

## 基本信息

- 执行者：Claude（代码实现者）
- 时间：2026-08-15
- 修改范围：`backend/src/main/java/com/adproject/application/application/InterviewService.java`、`backend/src/test/java/com/adproject/application/RecruiterInterviewIntegrationTest.java`、`change_report/`
- 明确禁止且未改动：Google OAuth、Google client/transport、port/DTO/OpenAPI、数据库迁移、实体结构、Android、Web、Admin、ML、Agent、认证方式、依赖版本、`web/dist`、`web/node_modules`
- 未提交、未推送（等待 Codex 复核）；未新增接口或 DTO 字段；未使用任何真实 Google 凭据或网络调用

## 背景

上一包实现的「首次建会失败后重试」逻辑，对 `meetingProvisioning.provision(...)` 的正常返回（`READY / PENDING / FAILED`）都能处理。但该端口属于可替换的外部依赖，若意外返回：

- `null`；
- `outcome == null`；
- `READY` 但缺少 event ID 或 Meet URL；
- `PENDING` 但缺少 event ID；

则当前 `InterviewService` 的创建路径会直接对 `result.outcome()` 做 `switch`，重试写回也会直接读取 `result.outcome()` / `result.eventId()` / `result.meetingUrl()`。这会产生 500，并让第一阶段已提交的本地面试永久停留在 `SCHEDULED + PENDING`，没有任何后续恢复路径。这违背了「外部调用失败不能污染本地状态」的要求。

## 修改内容

### `InterviewService`：统一规范化外部返回

将 `provisionMeeting(...)` 作为创建写回与重试写回**共同**的唯一入口，在其内部把端口返回统一规范化为安全的 `ProvisionResult`：

- 新增 `normalizeProvisionResult(result)`：
  - `result == null` 或 `outcome == null` → 降级为安全 `FAILED`。
  - `READY` → 必须**同时**满足「event ID 非空」且「Meet URL 可用」，否则降级。
  - `PENDING` → 必须「event ID 非空」，否则降级。
  - `FAILED` → 错误码非空则原样保留；为空 / 空白则改用通用错误码。
- 新增 `safeProvisionFailure()`：统一返回 `ProvisionOutcome.FAILED + eventId=null + meetingUrl=null + "GOOGLE_MEET_PROVISIONING_UNAVAILABLE"`。
- 新增 `isNotBlank(...)` 与 `isUsableMeetUrl(...)`：`isUsableMeetUrl` 校验 HTTPS + `meet.google.com` 主机（与端口自身的 `isValidMeetLink` 契约一致），任何不合法的 URL 一律视为「缺少 Meet URL」，绝不把伪造或客户端可控的值写入 `locationOrMeetingUrl`。
- 异常路径保持原有日志方式：只记录异常类别 `e.getClass().getSimpleName()`，不记录 Token / Google 原始响应 / event ID / Meet 链接等敏感信息。

`create` 的写回与 `writeBackProvisionRetry` 的写回**均直接复用** `provisionMeeting(...)` 的返回值，因此上述保护同时覆盖首次创建与首次失败后的 `PATCH` 重试两条路径，无需改动两条调用链的既有 `READY / PENDING / FAILED` 业务分支。

## 为何能避免 PENDING 卡死

- 创建 / 重试的第一阶段会在事务内把面试置为 `PENDING + version++` 并提交；随后在事务外调用外部端口；第二阶段写回会按 `result.outcome()` 落库。若端口返回 `null` 或 `outcome == null`，旧代码在读取 `result.outcome()` 时抛 NPE，导致写回事务未执行，面试被永久留在 `PENDING`。
- 规范化后，`provisionMeeting(...)` 返回的永远是「字段完备」的 `ProvisionResult`，其 `outcome` 恒为三者之一且必为合法值，写回事务一定能把面试推进到 `READY / FAILED`（或合法 `PENDING`），**不会再因 null / 缺失字段而抛异常、也不会停留在 `PENDING`**。
- 所有无效结果统一落到 `SCHEDULED + FAILED`，不写 event ID、不写 Meet URL、不伪造链接，也不创建第二条 interview，且不绕过既有的版本 / 所有权 / `PENDING` 冲突校验。

## 涉及模块

- `com.adproject.application.application.InterviewService`（修改）：`provisionMeeting` 增加结果规范化 + 新增 4 个私有辅助方法。
- `com.adproject.application.RecruiterInterviewIntegrationTest`（修改）：新增 5 个回归用例。

## API 与数据库变化

- 无。未新增接口 / DTO 字段 / 数据库字段，未改动 OpenAPI / 数据库迁移 / 实体结构。继续复用既有 `PATCH /recruiter/interviews/{id}` 与既有 `Interview` 字段。

## 测试命令与真实结果

在 `backend/` 执行：

```powershell
$env:JAVA_HOME='C:\Users\14188\.jdks\ms-21.0.8'
& 'C:\Users\14188\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd' -o '-Dtest=RecruiterInterviewIntegrationTest' test
```

真实结果：

```
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（较上一版 34 个用例新增 5 个；既有 34 个 interview 集成用例全部保持通过。）

新增 5 个回归用例：

1. `googleMeetNullProvisionResultFailsSafelyWithout500`：首次创建时 `provision` 返回 `null` → 返回 201（非 500），最终 `SCHEDULED + FAILED`，无链接、无 event ID、`meeting_sync_error=GOOGLE_MEET_PROVISIONING_UNAVAILABLE`。
2. `googleMeetInitialFailureRetryNullProvisionStaysFailedWithoutSecondInterview`：初始失败后重试时 `provision` 返回 `null` → PATCH 返回 200（非 500），最终仍 `SCHEDULED + FAILED`，且 `interviews` 总数仍为 1。
3. `googleMeetReadyProvisionWithoutMeetUrlFailsSafely`：`READY` 但缺 Meet URL → 安全落为 `FAILED`，无链接、无 event ID。
4. `googleMeetPendingProvisionWithoutEventIdFailsSafely`：`PENDING` 但缺 event ID → 安全落为 `FAILED`，无链接、无 event ID，不遗留 `PENDING`。
5. `googleMeetFailedProvisionWithBlankCodeUsesGenericCode`：`FAILED` 且错误码为空白 → 使用通用错误码 `GOOGLE_MEET_PROVISIONING_UNAVAILABLE`。

## 限制

- 本包仅做防御性结果规范化，不改变端口契约，也不修复一个「恶意端口」本身——端口仍应在返回前保证数据有效；这里保证的是「即使端口违反契约，本地面试状态也绝不卡死、绝不伪造链接」。
- 未对 `MeetingSyncResult`（改期 / 取消路径）做额外改动；该路径此前已有 `syncExternal` 的 null / null-outcome 保护，本包未触及，语义不变。

## 下一步建议

- 待 Codex 复核通过后，可在真实两账号演示（Task 5）中顺带用一次「先断网 / 强制端口返回 null」的手工冒烟，确认创建与重试两条路径都落在 `FAILED` 而非 `PENDING`。
- 若后续允许修改端口 / DTO，可考虑把「结果合法性校验」下沉到端口实现层，进一步缩小 `InterviewService` 的防御面。
