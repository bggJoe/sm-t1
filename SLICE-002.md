# SLICE-002：Admin 身份驗證（JWT 基礎建設）

## 三句話定義

- **誰用**：管理者（Admin），系統中預設存在的角色，需要身份識別才能操作受保護資源
- **做什麼**：透過帳號密碼取得 JWT token；後續所有 Admin 操作的 API 請求帶此 token，系統驗證其有效性
- **成功的判準**：合法帳密取得有效 JWT；無效帳密回傳 401；受保護端點在缺少或無效 token 時回傳 401/403

## 範圍邊界

- **In scope**：POST /auth/login（取得 JWT）、Spring Security 全局 filter chain 配置、JWT 簽發與驗證、Admin 帳號資料（最小：帳號 + 密碼 hash）
- **Out of scope**：Admin 管理介面（SLICE-003）、Visitor 登入（SLICE-001 明確排除）、Token refresh 機制、多 role 權限系統（只有 Admin 一個 role）

## 候選 INV-### 條款

- **INV-005**：只有帳密正確的 Admin 才能取得 JWT
  - 條件：`POST /auth/login` 被呼叫時
  - 斷言：`credentialService.verify(username, password) == true` 才回傳 token；否則回傳 401
  - 反證方式：送出錯誤密碼，系統若成功回傳 JWT 則此不變式失效
  - **對應測試**：`AdminAuthServiceTest#inv005_invalidCredentialsRejected`

- **INV-006**：JWT 必須包含 admin 身份識別且設定有效的過期時間
  - 條件：JWT 被簽發時
  - 斷言：payload 含 `sub`（admin username）且 `exp` 為未來時間點
  - 反證方式：解碼回傳的 JWT，若缺少 `sub` 或 `exp`，或 `exp` 已過期，則失效
  - **對應測試**：`AdminAuthServiceTest#inv006_jwtContainsSubjectAndExpiry`

- **INV-007**：受保護端點在 JWT 缺失或無效時必須拒絕請求
  - 條件：任何標記為 `@ProtectedEndpoint` 的 API 被呼叫時，且 Authorization header 缺失或 token 非法
  - 斷言：Spring Security filter chain 回傳 401（缺失）或 403（非法）
  - 反證方式：不帶 token 直接打受保護 API，若系統回傳 200 則失效
  - **對應測試**：`SecurityFilterTest#inv007_protectedEndpointRejectsUnauthenticated`

## 關聯

- SLICE-001：提供受保護端點的定義基準（SLICE-001 的 POST /registrations 是 PUBLIC，是對比參照）
- SLICE-003：Admin 審核 API 將依賴本 SLICE 建立的 JWT 驗證機制

---

## AC 條款

Infrastructure SLICE 的 AC 以「機制在端到端情境下正確運作」為判準，格式與 Business SLICE 相同（Given/When/Then），actor 為 Admin。

- **AC-003**：合法帳密取得有效 JWT
  - **Given** DB 中存在 username=`testadmin`、密碼 bcrypt hash 正確的 AdminCredential
  - **When** POST `/auth/login` 帶 `{"username": "testadmin", "password": "secret"}`
  - **Then** HTTP 回應 `200 OK`，body 含 `token` 欄位，且 token 可被解碼為有效 JWT（sub=testadmin, exp 在未來）
  - **對應測試**：`AdminAuthIntegrationTest#ac003_validCredentialsReturnJwt`

- **AC-004**：無效帳密回傳 401，不簽發 JWT
  - **Given** DB 中存在 username=`testadmin` 的 AdminCredential
  - **When** POST `/auth/login` 帶 `{"username": "testadmin", "password": "wrong"}`
  - **Then** HTTP 回應 `401 Unauthorized`，body 不含 `token` 欄位
  - **對應測試**：`AdminAuthIntegrationTest#ac004_invalidCredentialsReturn401`

- **AC-005**：未帶 JWT 的請求被 filter chain 攔截並回傳 401
  - **Given** 一個未被任何 `PublicPathContributor` 宣告為 PUBLIC 的路徑（使用不存在路徑 `/admin/protected-test`，靠 Spring Security default-protect-all 機制攔截）
  - **When** GET `/admin/protected-test` 不帶 Authorization header
  - **Then** HTTP 回應 `401 Unauthorized`（而非 403 或 404）
  - **Note** 路徑不存在不影響測試語意：Spring Security filter 在 routing 之前執行，401 代表 filter chain 正確攔截
  - **對應測試**：`AdminAuthIntegrationTest#ac005_missingTokenReturns401`

- **AC-006**：帶有效 JWT 的請求被 filter chain 放行（抵達 routing 層）
  - **Given** 合法簽發的 JWT（sub=testadmin, exp 在未來）
  - **When** GET `/admin/protected-test` 帶 `Authorization: Bearer <valid-token>`
  - **Then** HTTP 回應 `404 Not Found`（而非 401）
  - **Note** 401 vs 404 是「filter chain 是否放行」的可觀察信號；路徑不存在刻意為之，不依賴 SLICE-003 的真實 endpoint
  - **對應測試**：`AdminAuthIntegrationTest#ac006_validJwtIsPassedThroughFilterChain`

## 方法論張力（Phase 2 D2 協商前需要先決定）

SLICE-002 是「基礎建設 SLICE」，不是業務功能 SLICE。這對方法論有兩個具體挑戰：

**挑戰 1：Spring Security filter chain 屬於哪個 Modulith 模組？**

Spring Security 的配置（`SecurityFilterChain` bean）是全局的，天然是跨模組的。它不屬於 `registration` 模組，也很難說它屬於某個業務模組。可能的選項：
- 建立 `auth` 模組，放 SecurityConfig + JWT 工具 + AdminCredential entity
- 建立 `security` 模組，只放技術配置，`auth` 業務另立
- SecurityConfig 放在根包（`com.example.backend`），不屬於任何 Modulith 模組

**挑戰 2：D2 / DSL 如何表達 filter chain 這類橫切關注點？**

D2 是節點-邊圖，描述「誰呼叫誰」。但 JWT filter 不是被業務代碼呼叫的，它攔截所有 HTTP 請求。在圖上畫它的位置不直觀——畫在 Presentation 層前？還是 Infrastructure 層？

這兩個問題在 Phase 2 D2 協商時會直接浮現。預計需要一次額外的「架構決策討論」才能開始畫圖。
