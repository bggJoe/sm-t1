# DECISIONS.md

每筆條目格式：
- **假設**：做了什麼假設
- **依據**：為什麼這樣選
- **反證條件**：如果什麼為真，此假設不成立

---

## DEC-022 workspace.dsl 視圖策略：Component group 分組 + 模組拓撲視圖（方案 Y）

- **假設**：在 Component 全貌視圖加 `group` 視覺分組、並新增 `Backend-Module-Topology` 聚焦視圖，足以解決「全貌太複雜」與「模組拓撲不可見」兩個問題，不需要將模組升格為 Container
- **依據**：
  - C4 嚴格定義：Modulith 邏輯模組不是獨立部署單元，升格為 Container 在語義上有瑕疵，需要文字補救
  - Structurizr `group` 語法可在 Component view 渲染模組邊界框（boundary），零 model 層次重構
  - 拓撲視圖（`Backend-Module-Topology`）只 include 各模組公開 API component（UseCase interface、Extension Point），依賴箭頭自然呈現跨模組依賴方向，不暴露內部實作細節
  - 現有 SLICE 凍結視圖（SLICE-001~004）不受影響
  - 漸進升級路徑保留：當複雜度超過 group 可表達的範圍，再將模組升格為 Container（此時 group 直接對映到 Container，重構路徑清楚）
- **反證條件**：如果 Structurizr 的 group 邊界框在超過 5 個模組時仍難以閱讀，或需要在 Container view 層次表達模組間的 API 版本契約，則觸發升格到 Container 的條件

---

## DEC-021 Admin 作為跨模組流程協調者；兩階段分離 transaction 模型；Application Service 承載協調邏輯

- **假設**：Admin Portal 在 SLICE-004 新增跨模組協調職責（approve registration + create account），且兩個動作分屬各自 transaction，不綁定在同一個 `@Transactional`；協調邏輯由 `AdminApplicationService` 承載，`AdminApplicationController` 退回純 HTTP adapter
- **依據**：
  - 跨模組 `@Transactional` 破壞 Spring Modulith 模組隔離精神（共用 persistence context，模組邊界在 infrastructure 層打洞）
  - BPM 視角：「審核通過」與「帳號開立」是不同業務階段，允許各自失敗、各自回滾；狀態紀錄明確即可，不需要原子性
  - 失敗可觀測：HTTP response body 分別回報兩個階段的結果（applicationStatus / accountCreated），呼叫者知道需要重試哪個階段
  - **Application Service 選擇（Clean Architecture / DDD）**：部分失敗處理（stage 2 exception → composite result）是流程協調邏輯，不是 HTTP adapter 的職責。`AdminApplicationService.approveAndProvisionAccount()` 是正統 Application Service 定位（協調多個 UseCase，不含業務規則判斷）；controller 退回純轉換層，可測試性提升
  - Admin 自身不含任何 if/else 業務判斷，只依序呼叫兩個 UseCase，符合 DEC-019 薄 Portal 原則
  - 依賴拓撲：admin → registration（已有）+ admin → account（新增），不產生循環依賴；registration 不依賴 account
- **反證條件**：
  - 如果業務合規性要求「帳號必須在同一個原子操作內建立」（如法規稽核需求），需退回討論分散式 saga 或合併 transaction 策略
  - 如果 `AdminApplicationService` 開始累積判斷邏輯（如根據申請類型呼叫不同 UseCase 組合），則 admin 有轉為 God Module 的風險，需拆出 orchestration layer

---

## DEC-001 Angular CLI 鎖定 20（而非指定的 21）

- **假設**：Angular CLI 20 與 Angular 20 框架可以作為此專案的前端工具鏈
- **依據**：機器已安裝 Angular CLI 20；升級至 21 需要額外確認生態系相容性；Phase 0 不涉及前端業務，差異無實質影響
- **反證條件**：如果後續 Angular 21 引入的 API 或 build behavior 在 CLI 20 下無法使用，此決策需要重審並升級
- **注意**：此決策臨時優先於 copilot-instructions.md 的 Angular 21 規定，待環境升級後重審，不構成對規範的靜默覆寫

---

## DEC-002 Spring Boot 3.5.13 + Spring Modulith 1.3.12 + ArchUnit 1.4.2

- **假設**：三個版本的組合在技術上相容，且能通過所有 Phase 0 DoD 驗證
- **依據**：
  - Spring Boot 3.5.13 是 copilot-instructions.md 指定版本，Maven Central 確認存在
  - Spring Modulith 相容矩陣（官方文件）確認 1.3.x 支援 Spring Boot 3.5.x
  - ArchUnit 1.4.2 是截至 2026-05-09 的最新穩定版，無 Spring Boot 版本耦合
  - 三者合跑 `./mvnw test` 全綠（3 tests, 0 failures）
- **反證條件**：如果出現執行期 ClassLoader 衝突、Modulith BOM 與 Boot parent 版本衝突，或 ArchUnit 對 Java 21 位元組碼解析失敗，此組合需重選

---

## DEC-003 Nexus SSL 憑證匯入 JDK truststore

- **假設**：將 nexusoss.wistron.com 的 SSL 憑證加入 Zulu JDK 21 的 cacerts 是合法且安全的操作
- **依據**：
  - 公司 Maven settings.xml 已設定 mirror 將所有請求導向此 Nexus
  - Nexus 確認有 Spring Modulith 1.3.12（HTTP 200）
  - 問題純為 PKIX 憑證信任鏈，不是 artifact 缺失
  - 匯入後 `./mvnw test` 通過，確認修法有效
  - CA 憑證指紋已由 Joe 確認合法（SHA-256: `2B:6D:92:11:8D:C3:AB:E2:A2:40:43:6E:70:20:7D:A0:68:7E:12:E4:18:E1:56:FF:26:8B:1B:EA:F1:FE:96:6D`）
- **反證條件**：如果 Nexus 憑證被替換或 CA 鏈變更，需重新匯入；如果公司有集中管理的 truststore policy，此本機修法可能被覆蓋

---

## DEC-004 RegistrationApplicationRepository 保留為獨立介面

- **假設**：JPA Repository 作為獨立介面存在（而非直接用 EntityManager）是必要的
- **依據**：Modulith 模組測試慣例需要 Repository 介面作為邊界點；Application Service 的單元測試可以 mock Repository 而不啟動 DB；移除此層會讓 Service 直接依賴 JPA 實作，違反 Application → Infrastructure 的依賴方向規則
- **反證條件**：如果後續測試策略改為全整合測試（無 mock），或 Modulith 提供更輕量的邊界標記方式，此決策可重審

---

## DEC-005 AccountExistencePort 作為 SEAM 存在於 SLICE-001

- **假設**：APPROVED 帳號的存在性檢查必須透過 Port interface，不可在 SLICE-001 中直接實作
- **依據**：Account 模組尚未建立；直接依賴未存在的模組會讓 SLICE-001 無法獨立編譯和測試；Port 是 Modulith 跨模組依賴的標準解法
- **反證條件**：如果 Account 模組在 SLICE-001 完成前就已存在且介面穩定，可以考慮直接依賴，Port 變成多餘的間接層；若 Account 模組在 SLICE-003 完成後仍未出現，需重審 Port 是否已成為永久結構，並評估 stub 是否需要升級為過渡期的真實邏輯
- **時序說明**：SLICE-003 = Admin 審核（PENDING→APPROVED），Account 模組 = 訂閱 RegistrationApplicationApproved event 並建立真實帳號（SLICE-003 之後的 SLICE）。`StubAccountExistenceAdapter` 在 Account 模組建立前保持 stub 是正確行為，不是欠債。

---

## DEC-006 四層架構（Presentation / Application / Domain / Infrastructure）作為本專案層次規範

- **假設**：四層架構足以描述本專案的技術分層，且對應的 ArchUnit 規則可以完整覆蓋跨層依賴的違反
- **依據**：與 Spring Boot 慣例對齊；與 Modulith 的模組邊界概念正交（模組是水平切割，層是垂直切割）；ARCHITECTURE-LAYERS.md 作為人類可讀 SOT，Phase 3 ArchUnit 規則從此翻譯
- **反證條件**：如果出現無法歸類的元件（例如跨層的 event bus、saga coordinator），或 ArchUnit 規則產生大量誤報，需重新審視層次定義

---

## DEC-008 ArchUnit `allowEmptyShould(true)` 骨架期設定

- **假設**：在 production package 尚無任何業務類別的骨架期，ArchUnit 規則加上 `allowEmptyShould(true)` 是正確行為，不是繞過規則
- **依據**：ArchUnit 1.4.x 將 `failOnEmptyShould` 預設改為 `true`——當 `that()` 子句找不到任何符合的類別時，視為規則本身無效並直接失敗。骨架期 `..web..`、`..application..` 等 package 下只有 `package-info.java`，不會產生 `.class` 檔，觸發此行為。`allowEmptyShould(true)` 讓規則在無目標時靜默通過，等 Phase 4 有真實類別後規則才有意義地執行
- **反證條件**：如果 Phase 4 產出的類別進入對應 package 後，規則仍然靜默通過（即沒有任何違規被抓到），需確認 package pattern（`..web..` 等）是否與實際 package 名稱對應正確

---

## DEC-009 ARCH-RULE-005：測試層 Application → Infrastructure 隔離規則

- **假設**：ArchUnit 的 `DO_NOT_INCLUDE_TESTS` 選項使 ARCH-RULE-002 的覆蓋範圍不含測試類別，需要另一條規則補上這個結構性盲點
- **依據**：`RegistrationApplicationServiceTest` 放在 `registration.application` package 下，其測試意圖是 mock Port interface、不直接觸碰 Infrastructure。若不加規則，測試類別裡的 infra import 不會觸發任何紅燈，會靜默進入 CI。ARCH-RULE-005 以 `ONLY_INCLUDE_TESTS` 掃描同一套 package pattern，與 ARCH-RULE-002 形成鏡像，覆蓋測試側
- **反證條件**：如果測試策略改為全整合測試（例如 `@DataJpaTest`），測試類別合法地依賴 Infrastructure，此規則會誤報。屆時需要將整合測試移到獨立 package（例如 `..it..`）並調整規則覆蓋範圍

---

## DEC-010 Phase 3 四層投影設計理由（L1–L4 分工）

- **假設**：四個層次各自獨立捕捉不同類型的約束，且互相不重疊、不矛盾
- **依據**：
  - L1（Structurizr DSL）：架構意圖的人機共識載體。唯一可以讓 Joe 和 AI 對「這個節點是什麼、在哪一層」有共同語言的地方。修改架構必須先改這裡
  - L2（ArchUnit）：package 層級的依賴方向靜態驗證。捕捉「代碼寫錯層」的問題，編譯期前就能抓到，不需要啟動應用程式
  - L3（Modulith verify）：模組間邊界的執行期驗證。捕捉「跨模組直接存取 internal package」的問題，L2 不管這件事（L2 只管層方向，不管模組邊界）
  - L4（INV stubs）：業務不變式的可追溯性錨點。確保 Phase 4 實作代碼必須對應到 SLICE-001.md 中已協商的不變式，不能靜默跳過
  - 四層分工：L1 管「是什麼」、L2 管「怎麼疊」、L3 管「誰能看誰」、L4 管「做了什麼保證」
- **反證條件**：如果 L2 和 L3 的規則出現衝突（例如 ArchUnit 通過但 Modulith 失敗，且原因不是邊界違反而是工具行為差異），需確認兩者的掃描範圍定義是否一致

---

## DEC-014 admin 模組單向依賴 security 模組

- **假設**：`AdminAuthService` 呼叫 `security` 模組的 `JwtTokenProvider` 簽發 token，是合法的跨模組依賴
- **依據**：依賴方向單向（admin → security），security 完全不知道 admin 存在；`JwtTokenProvider` 放在 `security` 根包成為公開 API，admin 透過 Modulith named interface 存取；符合「依賴方向往技術基礎建設」的慣例
- **已知風險（顯式記錄）**：`security` 模組 root package 的所有類型（`JwtTokenProvider`、`PublicPathContributor`）對任何宣告 `allowedDependencies` 包含 `security` 的模組都完全可見。Modulith verifier 不會限制「只能呼叫哪些方法」。目前靠 ARCH-RULE-006 防止 `org.springframework.security.web.*` 的濫用，但 application-level 的呼叫濫用（例如在 Domain 層直接呼叫 `JwtTokenProvider`）需要 Code Review 守門，沒有自動化防護。若 `security` root API 增多，應考慮引入 `@NamedInterface` 分層（參考 DEC-020）。
- **反證條件**：如果 security 模組開始需要反向引用 admin 的類別（例如查詢 admin 角色），則依賴關係變成循環，需要重新設計

---

## DEC-015 PUBLIC 路徑宣告機制：各模組聲明，security 模組收集

- **假設**：預設所有路徑受保護；例外路徑由各業務模組實作 `PublicPathContributor` 介面宣告，`SecurityConfig` 啟動時收集所有 contributor 建立 filter chain
- **依據**：Q2 決策（2026-05-10）。這讓各模組對「自己的哪些端點是 PUBLIC」有自主聲明權，不需要修改 security 模組；`registration` 模組聲明 `POST /registrations`，`admin` 模組聲明 `POST /auth/login`；SecurityConfig 只負責規則引擎，不硬編碼路徑
- **反證條件**：如果路徑宣告需要順序（filter chain 的順序有意義），或不同 contributor 聲明衝突，此機制需要加入優先序或衝突解決策略

---

## DEC-018 JWT 函式庫選用 jjwt 0.12.6，Spring Security 隨 Boot 3.5.13

- **假設**：使用 `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.6 作為 JWT 簽發與解析函式庫；`spring-boot-starter-security` 版本跟隨 Spring Boot 3.5.13 BOM
- **依據**：jjwt 0.12.x 是目前 jjwt 的最新穩定版，API 相較 0.11.x 有重大改變（`Jwts.builder().subject()` 取代 `.setSubject()`）。隨 Boot BOM 鎖定 Spring Security 版本，避免版本不一致問題
- **反證條件**：如果 jjwt 0.12.x 與 Boot 3.5.13 的 jackson databind 版本有衝突，需 explicit 覆寫版本；如果未來需要 RS256（非對稱簽名），需改用 `jjwt-impl` 提供的 RSA key 支援，或評估改用 Nimbus JOSE + JWT

---

## DEC-017 workspace.dsl component view 三層結構規則

- **假設**：每個 SLICE 對應一個「凍結視圖」（filter 只含本 SLICE 新增元件 + 相依 db）；另維護一個永遠 `include *` 的累積全貌視圖 `Backend-Components`
- **依據**：2026-05-10 觀察到 SLICE-001 view 用 `include *` 導致 SLICE-002 元件也被拉進去，違反「凍結快照」語意。正確結構：SLICE-N view = 該 SLICE 的局部快照（`include` 明確列舉）；`Backend-Components` = 累積全貌（`include *`）。兩種視圖功能不同，不可混用
- **操作規則**：每個 SLICE 完成 Phase 3 L1 時，SLICE-N view 必須明確 include；`Backend-Components` 隨 `include *` 自動更新，無需手動修改
- **反證條件**：如果 Structurizr 的 `include *` 有效能問題或因元件過多導致圖難以閱讀，需評估是否棄用全貌視圖改用 tag-based filter

---

## DEC-016 JWT payload 包含 sub + exp + iat

- **假設**：JWT 簽發時只需三個 claim：`sub`（admin username）、`exp`（過期時間）、`iat`（簽發時間）
- **依據**：Q3 決策（2026-05-10）。SLICE-002 範圍內只有一個 role（Admin），不需要 `role` claim；`iat` 是 JWT 標準 claim，方便後續 token 撤銷策略（比較 iat 與 token 黑名單）；保持最小化，未來需要擴充時再加
- **反證條件**：如果 SLICE-003+ 的 API 需要區分不同 admin 角色或權限，需要補 `roles` claim；如果需要 token refresh 機制，需要補 `jti`（JWT ID）

---

## DEC-013 workspace.dsl 採用累積式演化模型，D2 採用局部式協商模型

- **假設**：D2 圖（Phase 2）是單一 SLICE 的局部協商工具，可丟棄；workspace.dsl（Phase 3）是全系統的累積 SOT，每個 SLICE 疊加進去
- **依據**：RETROSPECT-SLICE-001 問題 3 揭露：若 AI 每個 SLICE 都把 workspace.dsl 視為「本 SLICE 的快照」而非「累積系統圖」，SEAM 和 stub 的遺留問題就無法被後續 SLICE 自動識別。`AccountExistencePort` 的定時炸彈正是靠「下一個 SLICE 開頭讀 DSL」這個機制才能被發現——DSL 的 `SEAM` tag 是跨 SLICE 的信號燈。D2 則相反：它是協商草稿，凍結後即可封存，不需要維護成全系統視圖
- **操作含義**：每個新 SLICE 進入 Phase 2 前，先讀 workspace.dsl 環顧現有系統；SEAM tag 的 component 是待辦事項；D2 只畫本次新增或變更的部分
- **反證條件**：如果系統規模變大導致單一 workspace.dsl 難以維護，需評估拆分成多個 DSL workspace（Structurizr 支援 `!include`）；但在三個 SLICE 以內，單一累積式 DSL 是合理選擇

---

## DEC-011 DSL tag 語義定義：「實作所在層」而非「介面所在層」

- **假設**：`workspace.dsl` 中 component 的 `tags` 標記指的是「該 component 的主要實作所在的架構層」，不代表其 port interface 的所在層
- **依據**：RETROSPECT-SLICE-001 卡點 3 揭露：`registrationAppRepository` 在 DSL 中標記為 `Infrastructure`，但 port interface `RegistrationApplicationRepository` 必須放在 `application` 層，否則 `RegistrationApplicationService` 的依賴方向違反 ARCH-RULE-002。此語義差距在 Phase 4 實作後才被發現，屬於 Phase 2/3 協商遺漏的細節
- **反證條件**：如果採用不同的 port/adapter 模式（例如 interface 放在 infrastructure、用 `@Bean` 在 application 層重新綁定），此假設不成立，需重新定義 tag 語義

---

## DEC-012 INV-004 email 格式驗證：簡化 regex 而非完整 RFC 5322

- **假設**：使用 `^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$` 作為 INV-004 的後端 email 格式守門足夠
- **依據**：RFC 5322 的完整實作極為複雜，且在實務上多數系統採用「基本格式可辨識」而非「完整規範合法」的驗證策略；此 regex 可攔截明顯錯誤格式（無 `@`、無 domain），足以作為繞過前端的防護。已知限制：`foo@bar.c`（TLD 一個字元）會通過，`foo@.example.com` 也會通過
- **反證條件**：如果業務要求更嚴格的 email 合法性（例如 MX record 驗證、或 TLD 最短長度），此 regex 不足，需引入專用 email 驗證函式庫

---

## DEC-020 Modulith 模組公開 API 統一規則：root package = 公開 API

- **假設**：每個模組的跨模組可見類型（UseCase interfaces、共享 DTO、enum）一律放在模組 root package（`com.example.backend.{module}/`）。Sub-packages（application/domain/infrastructure/web）全部視為 module-internal，其他模組不得直接存取。
- **依據**：
  - Modulith 原生規則：root package 類型預設公開，sub-package 類型預設 internal，不需要任何 annotation
  - 跨模組一致性：讀任何模組都適用同一心智模型，不需要找 `@NamedInterface` 在哪裡
  - `@NamedInterface` 增加認知負擔，在單一 sub-package 需要跨模組暴露時才有必要，目前規模不符合其使用場景
  - SLICE-003 首次落地：`ReviewApplicationUseCase`、`ListPendingApplicationsUseCase`、`ApplicationSummary`、`ReviewDecision` 全部在 `com.example.backend.registration/` root
- **推論**：
  - `allowedDependencies = {"registration"}` 語法足夠，不需要 `"registration::application"` 命名介面語法
  - 若未來某模組需要分層暴露不同粒度的 API，再評估是否引入 `@NamedInterface`，並補 DEC 條目
- **反證條件**：如果 root package 因為公開類型過多而難以閱讀（超過 ~10 個類型），考慮引入 `@NamedInterface` 分層，需新決策條目覆蓋本條

---

## DEC-019 `admin` 模組定位：薄 Portal 層，業務語意留在 domain 模組

- **假設**：`admin` 模組是「Admin Portal」——只放 HTTP adapter（controller）+ Admin 身份認證（AdminCredential entity / AdminAuthService）。所有業務語意（審核申請、封鎖帳號等）由對應的 domain 模組擁有，並以 UseCase interface 對外暴露。
- **依據**：
  - 若 admin 擁有其他 domain 的業務邏輯，admin 模組將隨功能增長無限膨脹（God Module 反模式）
  - 若每個 domain 模組自行決定「admin 可以做什麼」並暴露 UseCase interface，模組邊界清晰，每個 domain 可以獨立演化
  - `admin` module `displayName` 更新為 `"Admin Portal"` 以宣告定位
  - 此決策首次在 SLICE-003 落地：`ReviewApplicationUseCase` 介面定義在 `registration` 模組，`admin` 模組只呼叫它
- **推論**：
  - `admin/package-info.java` 的 `allowedDependencies` 隨新 SLICE 增長（每次 admin 操作一個新 domain，就加一條）。這是有意為之的顯式依賴聲明，不是壞味道。
  - URL 前綴 `/admin/` 是路由慣例，不代表模組所有權。查詢或操作位於 `/admin/applications` 的業務邏輯屬於 `registration` 模組。
- **反證條件**：如果出現某個 admin 操作的業務語意確實無法歸屬任何既有 domain 模組（孤立業務），則需另立模組而非打破此規則

---

## DEC-007 SLICE-001-REVIEW.md 修正決策集（2026-05-09）

- **假設**：SLICE-001-REVIEW.md 提出的 7 項弱點（含 ITEM-08 額外發現）全部採納後，工件群的一致性與可追溯性足以進入 Phase 3
- **依據**：
  - ITEM-01：Domain group 加入 D2（方案 A），視覺化「故意空」而非靜默缺席
  - ITEM-02：DEC-003（Nexus SSL）補回標題，從 DEC-006 結尾分離為獨立條目，編號序列恢復連續
  - ITEM-03：四條 INV 加入 `對應測試` 欄位骨架，Phase 3 測試方法名以此為準
  - ITEM-04：DEC-001 加明確聲明，臨時優先於 copilot-instructions.md 的 Angular 21 規定
  - ITEM-05：D2 的 RegistrationController 加紅底色與 `[PUBLIC - no auth]` 標記；SLICE-001.md 範圍邊界補 JWT 邊界說明
  - ITEM-06：成功判準的去重描述改為指向 INV-001，避免兩個版本並存
  - ITEM-07：DEC-005 反證條件加 SLICE-003 時序觸發點
  - ITEM-08（額外）：INV-004 補前後端雙層驗證聲明，反證方式改為直打 API 場景
- **反證條件**：如果 Phase 3 建測試時發現任一 `對應測試` 的方法名無法對應到合理的測試邏輯，需退回修正 INV 條款而非改方法名
