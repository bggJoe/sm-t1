# SLICE-003：Admin 審核申請

## 三句話定義

- **誰用**：管理者（Admin），已透過 SLICE-002 取得有效 JWT
- **做什麼**：查看待審核申請清單，對單筆 PENDING 申請執行核准或拒絕，系統更新申請狀態並記錄操作者
- **成功的判準**：PENDING 申請可被 Admin 核准（→ APPROVED）或拒絕（→ REJECTED）；非 PENDING 狀態的申請不可再審核；審核動作需帶有效 JWT 才能執行

## 架構立場（DEC-019）

`admin` 模組 = **薄 Portal 層**：只放 HTTP controller（adapter）+ Admin 身份認證。  
業務語意由 domain 模組擁有，以 UseCase interface 對外暴露，`admin` 只呼叫。  
URL 前綴 `/admin/` 是路由慣例，不等於模組所有權。

## 範圍邊界

- **In scope**：
  - `GET /admin/applications?status=PENDING` — controller 在 `admin`，查詢邏輯在 `registration`（`ListPendingApplicationsUseCase`）
  - `PATCH /admin/applications/{id}/review` — controller 在 `admin`，業務邏輯在 `registration`（`ReviewApplicationUseCase`）
  - `RegistrationApplication` 狀態機新增兩條轉換：PENDING→APPROVED、PENDING→REJECTED
  - `registration` 模組暴露 `ReviewApplicationUseCase` + `ListPendingApplicationsUseCase` 兩個 public UseCase interface
  - `admin` 模組新增 `allowedDependencies = {"security", "registration"}`
- **Out of scope**：
  - APPROVED 後觸發帳號建立（→ 未來 SLICE，event 驅動）
  - REJECTED 後寄通知信（→ 未來 SLICE）
  - 分頁、排序（→ 未來迭代）
  - 審核歷史紀錄（approvedBy/rejectedBy 欄位記錄，但無查詢 API）

## 候選 INV-### 條款

- **INV-008**：只有狀態為 PENDING 的申請可以被審核；APPROVED 或 REJECTED 的申請不可再次被核准或拒絕
  - 條件：`reviewApplication(id, decision)` 被呼叫時
  - 斷言：`application.getStatus() == PENDING`；否則拋出例外（→ 409 Conflict）
  - 反證方式：對已 APPROVED 的申請再次呼叫 approve，若系統接受則此不變式失效
  - **對應測試**：`ReviewApplicationServiceTest#inv008_cannotReviewNonPendingApplication`

- **INV-009**：審核動作必須記錄操作者身份（approvedBy / rejectedBy = JWT sub claim）
  - 條件：狀態成功轉換為 APPROVED 或 REJECTED 後
  - 斷言：`application.getReviewedBy()` 不為 null/空白，且等於執行操作的 Admin username
  - 反證方式：不帶 username 的 review 動作若成功執行，則此不變式失效
  - **對應測試**：`ReviewApplicationServiceTest#inv009_reviewerIdentityIsRecorded`

- **INV-010**：reject 動作必須提供非空白的原因；空白原因不得建立 REJECTED 紀錄
  - 條件：`reject(id, reason, rejectedBy)` 被呼叫時
  - 斷言：`reason` 不為 null/空白
  - 反證方式：送出空白 reason 的 reject 請求，系統若成功變更狀態則此不變式失效
  - **對應測試**：`ReviewApplicationServiceTest#inv010_rejectReasonMustNotBeBlank`

## AC 條款

- **AC-007**：Admin 持有有效 JWT 可查詢待審申請清單
  - **Given** DB 中存在 2 筆 PENDING 申請、1 筆 APPROVED 申請；Admin 已持有有效 JWT
  - **When** GET `/admin/applications?status=PENDING` 帶 JWT
  - **Then** HTTP 回應 `200 OK`，body 為陣列，含 2 筆紀錄，每筆含 id、email、name、status
  - **And** APPROVED 申請不出現在結果中
  - **對應測試**：`AdminReviewIntegrationTest#ac007_adminCanListPendingApplications`

- **AC-008**：Admin 核准 PENDING 申請後狀態變 APPROVED
  - **Given** DB 中存在一筆 PENDING 申請；Admin 已持有有效 JWT
  - **When** PATCH `/admin/applications/{id}/review` 帶 `{"decision": "APPROVE"}` + JWT
  - **Then** HTTP 回應 `200 OK`
  - **And** DB 中該申請狀態為 `APPROVED`，`reviewedBy` 等於 JWT sub claim
  - **對應測試**：`AdminReviewIntegrationTest#ac008_adminApprovesApplication`

- **AC-009**：Admin 拒絕 PENDING 申請後狀態變 REJECTED
  - **Given** DB 中存在一筆 PENDING 申請；Admin 已持有有效 JWT
  - **When** PATCH `/admin/applications/{id}/review` 帶 `{"decision": "REJECT", "reason": "資料不完整"}` + JWT
  - **Then** HTTP 回應 `200 OK`
  - **And** DB 中該申請狀態為 `REJECTED`，`reviewedBy` 等於 JWT sub claim，`rejectionReason` 不為空
  - **對應測試**：`AdminReviewIntegrationTest#ac009_adminRejectsApplication`

- **AC-010**：無 JWT 的審核請求被拒絕（由 SLICE-002 filter chain 守護）
  - **Given** DB 中存在一筆 PENDING 申請
  - **When** PATCH `/admin/applications/{id}/review` 不帶 Authorization header
  - **Then** HTTP 回應 `401 Unauthorized`，申請狀態不變
  - **對應測試**：`AdminReviewIntegrationTest#ac010_unauthenticatedReviewIsRejected`

## 關聯

- DEC-019：確立 `admin` = 薄 Portal 層；本 SLICE 是此決策的首次落地
- SLICE-001：提供被審核的 `RegistrationApplication` entity 與 `registration` 模組
- SLICE-002：提供 JWT 保護機制；`admin/applications/**` 路徑不在任何 `PublicPathContributor` 中，自動受保護
- 未來 SLICE：`RegistrationApplicationApproved` event 觸發帳號建立（EVENTS-CATALOG.md 第一個真實條目）

## 方法論觸發點

- **STATES-registration-application.md 在本 SLICE 建立**：PENDING→APPROVED、PENDING→REJECTED 是第二、三條轉換，狀態機工件現在有意義了
- **EVENTS-CATALOG.md 在本 SLICE 起草**：`RegistrationApplicationApproved` 是第一個跨模組 event（admin 審核完，未來 Account 模組訂閱），即使這個 SLICE 不實作 event，應在目錄中預告
- **`admin/package-info.java` 更新**：加入 `allowedDependencies = {"security", "registration"}`
