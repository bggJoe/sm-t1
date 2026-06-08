# RETROSPECT-SLICE-003

Phase 5 回顧。對象：SLICE-003（Admin 審核申請），Phase 1–4 完整一輪。

---

## 卡點清單

### 卡點 1：Modulith `@NamedInterface` 先引入後撤回

**發生位置**：Phase 3，`ModulithVerifierTest` 紅燈。

**症狀**：`admin` 模組 `allowedDependencies = {"security", "registration"}` 設定後，Modulith verifier 仍然報違反——`admin.web.AdminApplicationController` 依賴 `registration.application.ListPendingApplicationsUseCase` 被視為 internal 類型。

**根因**：Modulith 的 `allowedDependencies = {"registration"}` 只允許存取 registration **root package** 的類型。sub-package（`registration.application`）預設 internal，即使 `allowedDependencies` 包含該模組也不例外。

**第一次修法（錯的方向）**：在 `registration.application/package-info.java` 加 `@NamedInterface`，admin 改用 `"registration::application"` 語法。可以通過 Modulith verifier。

**被質疑**：用戶指出 `@NamedInterface` 只在 `registration` 有，其他模組沒有，這是不一致的規則。讀不同模組的人需要額外認知負擔。

**最終修法（正確方向）**：把 UseCase interfaces、DTO、enum 搬到 registration **root package**，移除 `@NamedInterface`，admin 用 `"registration"` 即可。確立 DEC-020 統一規則：**root package = 公開 API，sub-packages = module-internal，跨模組可見類型一律放 root**。

**教訓**：Modulith public API 的第一直覺是「讓 sub-package 可見」，但正確做法是「把需要公開的類型移到 root package」。`@NamedInterface` 是在 module 有多層 API 分類時的進階選項，不是基本用法。

---

### 卡點 2：`admin` 模組定位從來沒說清楚（God Module 風險）

**發生位置**：Phase 1，用戶在 SLICE-003 Phase 1 確認問題時提出。

**問題**：`admin` 模組在 SLICE-002 建立時定位模糊——若未來有「封鎖帳號」、「審核內容」等功能也全部塞入 `admin` 模組，模組會無限膨脹成 God Module；若分散在各 domain 模組，admin 業務邏輯沒有歸屬點。

**決策**（DEC-019）：`admin` 模組 = 薄 Portal 層，只放 HTTP adapter（controller）+ Admin 身份認證。業務語意永遠在 domain 模組，以 UseCase interface 對外暴露；admin 只呼叫。URL 前綴 `/admin/` 是路由慣例，不是模組所有權。

**為什麼 SLICE-002 沒有抓到這個問題**：SLICE-002 只有登入流程，`AdminCredential` 和 `AdminAuthController` 的語意天然屬於 admin，不需要跨模組協作，問題被掩蓋。SLICE-003 第一次出現跨模組操作，問題才浮現。

**教訓**：模組定位應在模組初次建立時就說清楚（`displayName` 只有 "Admin" 不夠）。「這個模組是什麼、不是什麼」需要在 Phase 1 明確決定，不應等到跨模組需求出現才補充。

---

### 卡點 3：`RegistrationApplicationRepository.findById` 衝突

**潛在問題（未爆發）**：`RegistrationApplicationRepository`（application 層 Port interface）新增了 `findById(UUID)` 方法，但 `JpaRegistrationApplicationRepository` extends `JpaRepository<RegistrationApplication, UUID>`，後者已經提供了 `findById`，回傳型別是 `Optional<RegistrationApplication>`。

**為什麼沒爆**：Spring Data JPA 的 `findById` 回傳 `Optional<T>`，Port interface 中也宣告為 `Optional<RegistrationApplication>`，型別匹配，JPA 自動繼承實作，不需要手寫。

**需要注意**：若 Port interface 的 `findById` 回傳型別與 `JpaRepository` 預設回傳型別不一致（例如 `List` vs `Optional`），就會出現編譯錯誤或歧義。日後新增 Port method 時應先確認是否與 JPA 預設方法衝突。

---

## 四層貢獻評估

| Layer | 貢獻強度 | 說明 |
|-------|---------|------|
| L1（三句話定義） | 高 | 明確表達「誰觸發、誰擁有語意」的邊界，是 DEC-019 的前提 |
| L2（D2 協商） | 中 | Portal vs Domain 的拆分在圖上可見，但跨模組呼叫路徑比 SLICE-001/002 複雜，需要說明 UseCase interface 作為邊界 |
| L3（架構凍結） | 高 | `@NamedInterface` 踩坑 + DEC-020 的確立，架構規則在此次產出實質學習 |
| L4（測試） | 高 | INV-008/009/010 在 entity 層被直接驗證，AC-007/008/009/010 全部 active 且自給自足 |

整體：L3 > L1 > L4 > L2。

---

## AI_COLLAB_GUIDE.md 建議更新

**新增規則：模組建立時宣告定位（防 God Module）**
> Phase 1 三句話定義後，若涉及新模組，必須追加一條明確聲明：「此模組是 X（業務域 / 薄 Portal / 基礎建設）」，並記錄「什麼不屬於此模組」。這個聲明遲到的代價是 SLICE-003 在 Phase 1 才補 DEC-019。

**新增規則：Modulith 公開 API 放置慣例**
> 跨模組可見類型（UseCase interfaces、共享 DTO、enum）一律放在模組 root package。不使用 `@NamedInterface` 除非有明確文件說明分層理由（DEC-020）。

---

## 方法論觀察

**STATES-registration-application.md 應在本 SLICE 建立**

PENDING→APPROVED 和 PENDING→REJECTED 是第一次出現完整的狀態機轉換路徑。`RegistrationApplication.approve()` 和 `reject()` 實作了兩條新邊，但我們沒有建立 STATES 工件。若未來有第四條轉換（例如 REJECTED→PENDING 重新申請），缺少 STATES 文件會造成「哪些狀態是終態」只能看 code 才知道。

**EVENTS-CATALOG.md 起草時機到了**

`RegistrationApplicationApproved` 是第一個在業務語意上明確存在的跨模組 event（admin 審核完，未來 Account 模組需要知道）。這個 event 在本 SLICE 沒有實作，但其概念已在程式碼中隱含。不起草 EVENTS-CATALOG.md，下個 SLICE 才要實作 event 時就沒有依據。

這兩個工件的缺席是本 SLICE 明確的遺債，應在 SLICE-004 前補齊。
