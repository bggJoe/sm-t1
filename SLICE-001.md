# SLICE-001：用戶提出帳戶註冊申請

## 三句話定義

- **誰用**：訪客（尚未擁有帳號的人）想成為系統使用者
- **做什麼**：填寫必要的註冊資訊（e.g. email、姓名）並送出申請，系統建立一筆待審核申請紀錄
- **成功的判準**：申請可以被順利建立並持久化，狀態為 PENDING；申請的去重規則詳見 INV-001；申請紀錄可查

## 範圍邊界

- **In scope**：申請表單（Angular）→ API → Application Service → 持久化（DB）
- **Out of scope**：審核流程（→ SLICE-002）、帳號建立（→ SLICE-002 完成後觸發）、通知（email 等）、不引入 auth 基礎建設（JWT → SLICE-002）

## 候選 INV-### 條款（精煉版，Phase 3 轉測試）

- **INV-001**：同一個 email 在系統中不得同時存在狀態為 PENDING 的申請，也不得已有 APPROVED 的帳號；違反任一條件時系統必須拒絕新申請。REJECTED 的歷史紀錄不阻擋重新申請。
  - 條件：`submitApplication(cmd)` 被呼叫時
  - 斷言：`registrationApplicationRepository.findPendingByEmail(email).isEmpty()` AND `accountExistencePort.hasApprovedAccount(email) == false`
  - 反證方式：建立一筆 PENDING 申請後再送出相同 email，系統若成功建立第二筆則此不變式失效
  - **對應測試**：`RegistrationApplicationServiceTest#inv001_duplicatePendingEmailRejected` (TODO Phase 3)
  - **對應測試**：`RegistrationApplicationServiceTest#inv001_alreadyApprovedEmailRejected` (TODO Phase 3)

- **INV-002**：申請建立時狀態必須為 PENDING，不得以其他狀態進入系統。
  - 條件：`RegistrationApplication` 物件被 `save()` 前
  - 斷言：`application.getStatus() == PENDING`
  - 反證方式：若能透過任何路徑建立狀態非 PENDING 的新申請紀錄，此不變式失效
  - **對應測試**：`RegistrationApplicationServiceTest#inv002_newApplicationMustBePending` (TODO Phase 3)

- **INV-003**：申請必須包含非空白的 email 與 name，任一為空白時系統不得建立申請紀錄。
  - 條件：`submitApplication(cmd)` 被呼叫時
  - 斷言：`cmd.email()` 不為 null/空白 AND `cmd.name()` 不為 null/空白
  - 反證方式：送出 email 或 name 為空白的請求，系統若成功建立申請則此不變式失效
  - **對應測試**：`RegistrationApplicationServiceTest#inv003_blankEmailOrNameRejected` (TODO Phase 3)

- **INV-004**：email 必須符合 RFC 5322 基本格式（含 @ 與 domain 部分）。
  - 條件：`submitApplication(cmd)` 被呼叫時；前端（Angular Reactive Form）做 UX 層驗證，後端 Application Service 做屁底驗證，兩層都必須守住此規則
  - 斷言：`cmd.email()` 通過 email 格式驗證
  - 反證方式：直接打 API （繞過前端）送出格式錯誤的 email（如 `notanemail`），後端若成功建立申請則此不變式失效
  - **對應測試**：`RegistrationApplicationServiceTest#inv004_invalidEmailFormatRejected` (TODO Phase 3)

## 關聯

- SLICE-002：管理者審核申請（APPROVED / REJECTED）

---

## AC 條款

- **AC-001**：訪客送出有效申請後，系統建立 PENDING 紀錄且回傳成功
  - **Given** 系統中沒有 email 為 `user@example.com` 的 PENDING 申請，也沒有該 email 的 APPROVED 帳號
  - **When** 訪客 POST `/registrations` 帶 `{"email": "user@example.com", "name": "Joe Wang"}`
  - **Then** HTTP 回應 `201 Created`
  - **And** DB `registration_applications` 表中 email 為 `user@example.com` 的紀錄存在且狀態為 `PENDING`
  - **Note** 同時隱式驗證 `/registrations` 不受 Spring Security 攔截（`RegistrationPublicPathContributor` 在 context 中正確運作）
  - **對應測試**：`RegistrationFlowIntegrationTest#ac001_newSubmissionCreatesPendingApplication`

- **AC-002**：訪客送出空白 email 時收到清楚的 400 錯誤
  - **Given** 系統正在運行
  - **When** 訪客 POST `/registrations` 帶 `{"email": "", "name": "Joe Wang"}`
  - **Then** HTTP 回應 `400 Bad Request`
  - **And** Response body 為 RFC 7807 ProblemDetail，明確指出 email 為必填
  - **Note** 目前 `@Disabled`；需要 `@ControllerAdvice` 將 `IllegalArgumentException` 對應至 400，否則 Spring 預設回傳 500
  - **對應測試**：`RegistrationFlowIntegrationTest#ac002_blankEmailReturnsProblemDetail` (@Disabled)
