# SLICE-004：帳號建立（Admin 協調跨模組流程）

## 三句話定義

- **誰用**：系統（由 Admin 核准申請觸發）；Admin Portal 作為跨模組流程協調者，不擁有業務規則
- **做什麼**：Admin 審核通過申請後，Portal 依序呼叫 registration 模組標記 APPROVED、再呼叫 account 模組建立帳號；兩個階段分屬各自 transaction，失敗可分開處理
- **成功的判準**：申請狀態為 APPROVED 且對應 Account 已建立（email 唯一，passwordHash 為 BCrypt，初始狀態 ACTIVE）

## 架構立場（DEC-019 延伸 + DEC-021）

`admin` 繼承薄 Portal 定位（DEC-019），在 SLICE-004 新增跨模組協調職責：

- `admin` 協調層依序呼叫兩個 UseCase，自身不含任何 if/else 業務判斷
- 兩個 UseCase 呼叫屬於不同 transaction（BPM 不同業務階段，見 DEC-021）
- `admin` 模組 `allowedDependencies` 加入 `"account"`

SEAM-001（`AccountExistencePort`）在本 SLICE 完成填充：

- `StubAccountExistenceAdapter` → 移除
- `JpaAccountExistenceAdapter`（account 模組）→ Real implementation
- `AccountExistencePort` 介面從 `registration/application/` 促升至 `registration` root（跨模組可見，DEC-020）

## Account Entity 設計

| 欄位 | 型別 | 備註 |
|------|------|------|
| id | UUID | PK，系統產生 |
| email | String | UNIQUE，不可變更 |
| passwordHash | String | BCrypt hash，初始值 = BCrypt("123456") |
| status | AccountStatus | ACTIVE / SUSPENDED；初始 = ACTIVE |
| createdAt | Instant | 建立時自動設定 |
| lastModifiedAt | Instant | 每次狀態變更更新 |

`AccountStatus` enum：`ACTIVE`、`SUSPENDED`（未來 SLICE 使用）

## 範圍邊界

- **In scope**：
  - 新建 `account` 模組：`Account` entity、`AccountStatus` enum、`CreateAccountUseCase` interface（模組 root）、`AccountApplicationService`、`AccountRepository`
  - `CreateAccountUseCase.create(email)` — 建立 ACTIVE 帳號，密碼固定 BCrypt("123456")
  - `admin` 模組新增 `AdminApplicationService`：協調兩階段流程（Stage 1 → Stage 2）、處理部分失敗組合結果；controller 退回純 HTTP adapter
  - `AdminApplicationController` 修改：approve 動作改為呼叫 `AdminApplicationService.approveAndProvisionAccount()`，回應 body 反映兩個階段各自結果
  - SEAM-001 填充：`JpaAccountExistenceAdapter` 實作 `AccountExistencePort`，`StubAccountExistenceAdapter` 移除
  - `AccountExistencePort` 促升至 `registration` root package
  - `admin` 模組 `allowedDependencies` 增加 `"account"`
  - ArchUnit：account 模組四層依賴規則（可擴充現有 ARCH-RULE 或新增）
  - workspace.dsl 更新：account 模組 components、SEAM-001 狀態從 Stub → Real

- **Out of scope**：
  - 帳號登入（→ 未來 SLICE）
  - 帳號管理 API（查詢、停用、修改密碼）（→ 未來 SLICE）
  - 前端 UI（→ 未來 SLICE）
  - 申請 REJECTED 後的對應處理
  - 分頁、排序

## 候選 INV-### 條款

- **INV-011**：同一個 email 在系統中不得有兩筆 Account 紀錄（email 唯一性）
  - 條件：`CreateAccountUseCase.create(email)` 被呼叫時
  - 斷言：DB 中不存在相同 email 的 Account；否則拋出例外（→ 409 Conflict）
  - 反證方式：對已存在帳號的 email 再次呼叫 create，若系統建立第二筆紀錄則此不變式失效
  - **對應測試**：`CreateAccountServiceTest#inv011_emailMustBeUnique`

- **INV-012**：Account 建立時 passwordHash 欄位不得儲存明文密碼（必須為 BCrypt hash）
  - 條件：Account 寫入 DB 後
  - 斷言：`account.getPasswordHash()` 開頭為 `$2a$` 或 `$2b$`（BCrypt 前綴）；不得等於原始明文字串 "123456"
  - 反證方式：建立 Account 後直接讀取 `passwordHash`，若值為 "123456" 則此不變式失效
  - **對應測試**：`CreateAccountServiceTest#inv012_passwordHashMustBeBcrypt`

- **INV-013**：Account 建立時初始狀態必須為 ACTIVE；不得以其他狀態進入系統
  - 條件：`CreateAccountUseCase.create(...)` 成功後
  - 斷言：持久化後 `account.getStatus() == AccountStatus.ACTIVE`
  - 反證方式：create 後查詢回 Account，若 status 不是 ACTIVE 則此不變式失效
  - **對應測試**：`CreateAccountServiceTest#inv013_initialStatusMustBeActive`

- **INV-014**：SEAM-001 填充後，`AccountExistencePort.existsByEmail(email)` 必須精確反映 Account 實際存在狀態
  - 條件：`JpaAccountExistenceAdapter` 作為 Real implementation 啟動後
  - 斷言：存在 Account(email="a@b.com") 時回傳 true；不存在時回傳 false；兩個方向都要覆蓋
  - 反證方式：建立 Account 後 Port 仍回傳 false，或未建立 Account 但 Port 回傳 true，則此不變式失效
  - **對應測試**：`AccountExistenceAdapterTest#inv014_existsByEmailReflectsActualAccountState`

## AC 條款

- **AC-011**：Admin 核准申請後，申請狀態變 APPROVED 且帳號建立成功
  - **Given** DB 中存在一筆 PENDING 申請（email="user@example.com"）；Admin 已持有有效 JWT
  - **When** PATCH `/admin/applications/{id}/review` 帶 `{"decision": "APPROVE"}` + JWT
  - **Then** HTTP 回應 `200 OK`，body 含 `applicationStatus: "APPROVED"` 與 `accountCreated: true`
  - **And** DB 中該申請狀態為 APPROVED，且 Account 存在（email="user@example.com", status=ACTIVE）
  - **And** Account 的 passwordHash 開頭為 `$2`（BCrypt）
  - **對應測試**：`AdminReviewIntegrationTest#ac011_approveCreatesAccount`

- **AC-012**：account 建立失敗時，申請仍保持 APPROVED，回應清楚指出兩個階段各自結果
  - **Given** DB 中存在一筆 PENDING 申請；`CreateAccountUseCase` 被 mock 為拋出例外；Admin 已持有有效 JWT
  - **When** PATCH `/admin/applications/{id}/review` 帶 `{"decision": "APPROVE"}` + JWT
  - **Then** HTTP 回應含 `applicationStatus: "APPROVED"`（stage 1 成功）與 `accountCreated: false`（stage 2 失敗）
  - **And** DB 中申請狀態確為 APPROVED
  - **And** 回應 body 包含足夠資訊讓呼叫者知道 stage 2 失敗，可獨立重試
  - **對應測試**：`AdminReviewIntegrationTest#ac012_accountCreationFailureReturnsPartialResult`

- **AC-013**：對已存在帳號的 email 重複建立帳號時，system 拒絕（INV-011）
  - **Given** Account(email="dup@example.com") 已存在於 DB
  - **When** `CreateAccountUseCase.create("dup@example.com")` 被呼叫
  - **Then** 拋出例外（→ 409 Conflict at API level）
  - **And** DB 中仍只有一筆 email="dup@example.com" 的 Account
  - **對應測試**：`CreateAccountServiceTest#ac013_duplicateEmailRejected`

- **AC-014**：SEAM-001 填充後，INV-001（重複申請防護）改由 JpaAccountExistenceAdapter 回答
  - **Given** Account(email="existing@example.com") 已在 DB；`StubAccountExistenceAdapter` 已移除
  - **When** 送出 email="existing@example.com" 的新申請
  - **Then** HTTP 回應 `409 Conflict`（INV-001 由 Real Adapter 觸發）
  - **And** `StubAccountExistenceAdapter` class 不存在於 codebase
  - **對應測試**：`AccountExistenceAdapterTest#ac014_realAdapterPreventsRegistrationDuplicate`

## 關聯

- DEC-019：admin 薄 Portal 定位不變；本 SLICE 在其範圍內新增協調職責
- DEC-021（本 SLICE 新建）：Admin 作為跨模組協調者；兩階段分離 transaction 模型
- DEC-020：AccountExistencePort 促升至 registration root（跨模組可見規則）
- SEAM-001：本 SLICE 完成填充（Stub → Real）
