# RETROSPECT-SLICE-002

Phase 5 回顧。對象：SLICE-002（Admin 身份驗證 / JWT 基礎建設），Phase 1–4 完整一輪。

---

## 卡點清單

### 卡點 1：ArchUnit `..web..` pattern 誤匹配 Spring 框架包

**發生位置**：Phase 4 第一次 `mvnw test` 後，ARCH-RULE-003/004 紅燈。

**症狀**：`noLayerShouldDependOnPresentation` 規則違反 18 次——`JwtAuthFilter` 繼承 `OncePerRequestFilter` 被判定為「依賴 Presentation 層」。

**根因**：ArchUnit 的 `..web..` pattern 匹配「任何路徑段含有 `web` 的包」，包括 `org.springframework.web.*`，不限於我們自己的 `com.example.backend.*.web`。SLICE-001 時規則沒有問題，是因為當時沒有任何 class 依賴 Spring web framework 的底層類別。SLICE-002 引入 `OncePerRequestFilter`（在 `org.springframework.web.filter`）才引爆。

**修法**：ARCH-RULE-003/004 的 target pattern 從 `..web..` 改為 `com.example.backend..web..`，把規則限縮到我們自己的包命名空間。

**教訓**：ArchUnit 的 `..X..` pattern 是全局匹配，不限於專案自身的包。任何 target 指向「我們定義的層」的規則，都應該加上 `com.example.backend` 前綴，否則引入新的 Spring 模組就可能爆。這個問題在 SLICE-001 因為沒有引入橫切框架類別而隱藏，SLICE-002 才引爆。

---

### 卡點 2：AC-002（400 Bad Request）需要 @ControllerAdvice 才能完成

**發生位置**：Phase 4 後 AC 測試補寫，AC-002 標 @Disabled。

**問題**：`RegistrationApplicationService` 在 email 空白時拋 `IllegalArgumentException`。Spring MVC 對 unhandled exception 預設回傳 500，不是 400。AC-002 要求「空白 email → 400 ProblemDetail」，但目前沒有 `@ControllerAdvice` 把 `IllegalArgumentException` 對應到 400。

**這是設計缺口，不是卡點**：Controller 目前也回傳 `ResponseEntity<Void>`（無 body），client 無法從 201 回應中拿到申請 ID。這兩個問題都在「Presentation 層的完整性」範疇，超出 SLICE-001/002 的最小範圍，是已知欠債。

**狀態**：AC-002 @Disabled，備註原因，不讓缺口靜默。SLICE-003 前需決定是否補 `@ControllerAdvice`。

---

### 卡點 3：ARCH-RULE-006 API 不存在（`haveFullyQualifiedClassName`）

**發生位置**：Phase 3 Layer 2，ARCH-RULE-006 初次寫好後編譯失敗。

**症狀**：`cannot find symbol: method haveFullyQualifiedClassName(java.lang.String)`。

**根因**：`dependOnClassesThat()` 後面的鏈式 API 跟 `that()` 子句不一樣。`haveFullyQualifiedClassName` 不存在於 `ClassesThat<ClassesShouldConjunction>` 介面，是憑記憶寫出的錯誤 API。

**修法**：改用 `resideInAPackage("org.springframework.security.web..")` 取代精確類別名稱，範圍稍寬（整個 security.web 包）但語意合理。

**教訓**：ArchUnit API 在 `that()` 和 `should()` 鏈的兩側不對稱，不可憑記憶推斷。需要查官方文件或以編譯驗證。

---

### 卡點 4：`haveFullyQualifiedClassName` 錯誤屬於哪類問題

這條卡點特別值得記錄，因為它反映的是 **copilot-instructions.md 規定「不憑記憶答工具版本與 API」的典型違規場景**。當時沒有 web search，直接寫出不存在的 API，靠編譯才抓到。這個問題在 ArchUnit 文件查閱成本低，是可以避免的。

---

### 方法論新發現：D2 的「橫切關注點」處理方式

**發生位置**：Phase 2 D2 協商，SLICE-002.md「方法論張力挑戰 2」。

**問題**：filter chain 不是「誰呼叫誰」的同步呼叫關係，D2 的節點-邊模型難以直接表達「攔截所有請求」這件事。

**實際解法**：建立獨立的 `cross-cutting-jwt.d2`，把橫切關注點的拓撲（PUBLIC 放行 / JWT 驗證 / 401/403）放進去，不汙染業務流程 D2。業務 D2 只畫 happy path（登入 → JWT），filter chain 行為用另一張圖處理。

**這是值得寫進 AI_COLLAB_GUIDE.md 的規則**：橫切關注點（filter、interceptor、AOP、event）不適合畫在業務 SLICE D2 中。有需要時建立獨立的 `cross-cutting-<concern>.d2`，在 SLICE 文件的關聯節標記。

---

### Actor 一致性問題（Phase 2 Deceiver 未抓到的斷層）

**發生位置**：Phase 5 討論中，Joe 提出「admin 也是要透過 frontend 登入的」。

**問題**：SLICE-001 的 D2 畫了 `visitor → registrationForm（Angular）→ registrationController`，actor 透過 frontend 進入系統。SLICE-002 的 D2 畫了 `admin → adminAuthController`，actor 直接打 backend。兩者路徑不一致，但 Phase 2 的 Deceiver check 沒有抓到這個跨 SLICE 的不一致。

**合理的暫時解釋**：SLICE-002 是 Infrastructure SLICE，admin login frontend 頁面還不存在，繞過 frontend 是合理的臨時設計。問題是這個決定沒有被明確標記為「暫時」。

**方法論缺口**：Phase 2 Deceiver check 需要新增一個問題：「本 SLICE 的 actor 進入系統的路徑，跟其他 SLICE 的同類 actor 是否一致？若不一致，是有意的設計決策嗎？」

---

### AC 斷層（PROCESS-LAYER-PROPOSAL.md 評估衍生）

**發生位置**：Phase 5 進入前，討論 AC 補強時識別。

**問題**：SLICE-002 完成時，AC-005 只測「缺 JWT → 401」（拒絕路徑），沒有測「有效 JWT → 放行」（放行路徑）。filter chain 讓合法 token 通過的路徑在整合測試層是 untested 的。

**修法**：補寫 AC-006（`ac006_validJwtIsPassedThroughFilterChain`），用 `401 vs 404` 信號區分「Security 攔截」vs「Security 放行但路徑不存在」，不依賴 SLICE-003 的真實 endpoint。

**這個缺口不是靠方法論工件主動發現的**：是在 Retrospect 討論中靠人工推理找到的。方法論本身沒有機制自動要求「每條 AC 都要測到 happy path 和 failure path」。這是 AC 格式的薄弱點，SLICE-003 應該在 AC 草稿時明確自問。

---

## 四層投影強度排名

| 排名 | 層 | 攔截到的事 | 強度說明 |
|------|----|----------|---------|
| 1 | **L2 ArchUnit** | ARCH-RULE-003/004 pattern 範圍錯誤；ARCH-RULE-006 API 不存在；都是真實紅燈 | 唯一在這輪產生多次真實紅燈的層 |
| 2 | **L4 AC Integration Test** | 補 AC-006 前，filter chain 放行路徑在整合測試層無驗證；補完後閉合缺口 | 這輪首次在整合測試層產生真實壓力（Spring Security context 下的行為） |
| 3 | **L3 Modulith verify** | SLICE-002 引入 `security` + `admin` 兩個新模組，verify() 通過代表邊界定義正確 | 這輪開始有實質跨模組邊界，L3 首次有真實壓力（雖然通過了） |
| 4 | **L1 Structurizr DSL** | component 清單成為 Phase 4 上下文錨點，阻止範圍外元件引入 | 軟約束，同 SLICE-001 評估；但 DSL view 三層結構修正（DEC-017）是額外收穫 |

---

## 對方法論的具體回饋

**有效的部分**：

`PublicPathContributor` 介面模式（DEC-015）在 Phase 3 凍結為設計，Phase 4 實作時 `SecurityConfig`、`RegistrationPublicPathContributor`、`AdminPublicPathContributor` 三者分工清楚，沒有需要重新設計的地方。圖形先協商讓這個「各模組宣告自己的 PUBLIC 路徑」機制在代碼之前已經成立。

**AC 補強的方法論效果**：這輪是 AC 第一次有真實 integration test。AC-003/004/005/006 驗證的路徑（全 Spring context，含 Security filter chain）在 INV 單元測試層完全看不到。兩層確實互補，不可替代。

**弱點 1：Deceiver check 缺跨 SLICE actor 一致性問題**

已在卡點中記錄。需要寫進 Phase 2 執行守則。

**弱點 2：橫切關注點在 D2 裡沒有命名規範**

`cross-cutting-jwt.d2` 是這輪自然生長出來的做法，沒有任何事前規則說「橫切關注點要這樣處理」。如果 SLICE-003 或之後的 SLICE 有新的橫切關注點，新的 LLM 不一定會沿用這個做法。需要在 AI_COLLAB_GUIDE.md 或 ARCHITECTURE-LAYERS.md 補一條規則。

**弱點 3：AC 的 happy path / failure path 對稱性沒有被格式強制**

AC-006 缺口是在 Retrospect 才被人工推理發現，不是格式要求的結果。AC 格式目前沒有要求「每條 AC 都應該有對應的負向測試（或明確說明為什麼不需要）」。

---

## 下一輪（SLICE-003）要加強的事

1. **AI_COLLAB_GUIDE.md Phase 2 補一個 Deceiver 問題**：「本 SLICE 的 actor 路徑與其他 SLICE 的同類 actor 是否一致？若不一致，是有意決策嗎？」

2. **ARCHITECTURE-LAYERS.md 補橫切關注點規則**：橫切關注點（filter、interceptor、event）不畫在業務 D2 中，改用 `cross-cutting-<concern>.d2` 處理。

3. **AC 草稿時自問**：「這條 AC 的 happy path 和 failure path 都有對應測試嗎？」

4. **SLICE-003 是 PROCESS-LAYER-PROPOSAL.md 的真實觸發點**：SLICE-003 引入 `PENDING→APPROVED/REJECTED` 是第二條狀態轉換，此時建 `STATES-registration-application.md` 和 `EVENTS-CATALOG.md` 才有意義。

---

## Deceiver 角色：一個刻意保留的問題

SLICE-002 完成後，`AdminCredential` 資料是在 `@BeforeEach` 中用 `credentialRepository.save()` seed 的，測試結束後靠 H2 in-memory 的生命週期清除。但 `@SpringBootTest` 預設不會在每個 test class 之間重建 application context——如果多個 integration test class 在同一個 JVM 跑，它們共享同一個 H2 instance。`testUsername` 用 `System.nanoTime()` 做 suffix 避免衝突，這是足夠的隔離手段，但依賴時間解析度，不是基於 UUID 的強隔離。

在目前的測試數量下不會出問題。如果測試變多、context 被 reuse、或者多線程執行，這個假設需要重新評估。
