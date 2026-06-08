# MODULE-PLACEMENT.md — 模組內部放置指南

<!--
狀態：最後與 codebase 對齊 — SLICE-003（2026-05-11）
相關決策：DEC-006（四層架構）、DEC-013（四層投影分工）、DEC-016（Port tag 語義）、DEC-020（root = 公開 API）
變更原則：本文件對「物件歸屬」的規則演化由 RETROSPECT-SLICE-NNN 觸發；新規則生效時須同步補對應 ArchUnit 測試或 Modulith verifier 規則。
-->

本文件說明在一個模組的四層結構中，各種類型的物件應該放在哪裡。並解釋 Domain、SEAM、Adapter、UseCase interface 等架構概念的定義與辨識方式。

本文件是 ARCHITECTURE-LAYERS.md 的補充，聚焦「物件的具體歸屬判斷」而非「層的依賴方向規則」。

---

## 一、架構名詞定義

### Domain Entity / Aggregate Root
**定義**：持有業務狀態與業務行為的物件。狀態只能透過自身方法改變，不允許外部直接設定欄位。INV 的執行者。

**辨識**：
- 有 `@Entity`（JPA），但業務邏輯在 Java 方法裡，不只是 getter/setter
- 有明確的狀態機（如 `PENDING → APPROVED`）
- 有保護不變式的方法（方法拋出 `IllegalStateException` 代表不變式守門）

**放哪**：`{module}/domain/`

**範例**：`RegistrationApplication.approve(reviewer)` — 只有在 `status == PENDING` 時才允許，否則拋例外（INV-008）。這個守門邏輯屬於 domain，不屬於 service。

---

### Value Object (VO) / Enum（domain 內部的）
**定義**：描述 domain 概念、沒有獨立生命週期的型別。通常是不可變的（`record` 或 enum）。

**辨識**：
- Domain Entity 的欄位型別直接使用它
- 移除它，Entity 的程式碼就無法完整表達其狀態或行為

**放哪**：`{module}/domain/`

**反例**：`ReviewDecision`（APPROVE/REJECT）不屬於 domain。Domain Entity 的方法是 `approve()` 和 `reject()`，它們不接收 `ReviewDecision` 參數——是 Application Service 在 application layer 做判斷後才呼叫對應的 entity 方法。`ReviewDecision` 是外部呼叫者（admin）的詞彙，domain 不需要知道。

---

### Port Interface 與 Adapter（SEAM 及相關模式）

Port 用 interface 作為隔離點，解決兩個不同層次的耦合問題，動機不同，選型方式也不同。

**模組內（package 層次）—— 控制依賴方向**

同一個模組內，`application/` 持有業務邏輯，不應知道 `infrastructure/` 的技術細節；`infrastructure/` 持有技術實作，可以向內依賴 `application/` 的抽象。這是一條不對稱的規則：`infrastructure/ → application/` 可以，反過來不行。要讓 `application/` 的 Service 使用某個技術能力（資料庫、外部 API），唯一合法的方式是在 `application/` 定義 interface，讓 `infrastructure/` 的 Adapter 去實作它。interface 是這條單向規則的執行工具。

**跨模組（模組邊界層次）—— 解耦模組間依賴**

當一個模組需要另一個模組的能力，但又不應該直接 import 對方時，interface 同樣是隔離點——但這裡多了一個問題：**誰來擁有這個 interface**？擁有者決定了模組間依賴的流向，也決定了選哪個模式。

用 interface 作為隔離點後，只需回答兩個問題就能選出對應設計：

**Q1：介面放在哪？（普遍規則，不需要分情況）**

介面永遠放在**定義者的 package**。實作者 `implements` 它，所以依賴方向是「實作者 → 定義者」，不是反過來。這條規則對所有分支都成立。

**Q2：跨模組時，定義者是誰？**

誰擁有 interface，誰就決定了依賴流向。兩個角色對應兩種模式：

- **消費者定義** → **Port Pattern（SEAM）**。需要某種能力的模組自己定義 interface，實作者模組負責兌現。依賴方向是「實作者模組 → 消費者模組」，實作者可在不同 SLICE 替換，消費者程式碼不需要改。真實實作不存在時，先在同模組的 `infrastructure/` 放 Stub 佔位；未來跨模組實作時，再把 interface 促升至模組 root（記錄為遷移債）。
- **提供者定義** → **Extension Point / Collector**。開放擴展點的模組定義 interface，其他模組各自 implement 並貢獻，提供者透過 `List<interface>` 在執行期收集全部實作者。依賴方向是「實作者模組 → 提供者模組」，提供者不需要知道有哪些模組參與。

> 框架（Spring Data、Spring Security 等）直接提供實作時，結構上與 Port Pattern 相同——interface 仍定義在 `application/`，框架在執行期注入唯一實作，省略 Stub 步驟，屬於 Port Pattern 的特例。

---

**分支一：Port Pattern（SEAM）— 消費者定義 + 可替換的單一實作**

SEAM 永遠是三件組，缺一不成立：

| 角色 | 是什麼 | 放哪 |
|------|--------|------|
| Port Interface | 能力的抽象定義（`interface`） | `application/`（只在同模組實作時）；跨模組實作 → 須先促升至模組 root |
| Stub Adapter | 暫時佔位的假實作（`class Stub...`） | 當前模組的 `infrastructure/` |
| Real Adapter | 真實實作（未來 SLICE 補上） | 未來模組的 `infrastructure/` |

```
當前 SLICE（Account 模組尚未建立）：
  RegistrationApplicationService
      ↓ 依賴
  AccountExistencePort (interface)  ← registration/application/
      ↑ 實作
  StubAccountExistenceAdapter       ← registration/infrastructure/
      （目前固定回傳 false；測試需要時可換成不同固定值，但不查外部系統）

未來 SLICE（Account 模組建立後）：
  AccountExistencePort (interface)  ← 必須先促升至 registration root
      ↑ 實作
  JpaAccountExistenceAdapter        ← account/infrastructure/
      （查真實 DB）
```

Stub 被替換時，`RegistrationApplicationService` 不需要改——它只認識 interface，不認識實作者。

這個例子同時展示兩個層次的疊加：`AccountExistencePort` 讓 `application/` 不需要 import `infrastructure/`（模組內的依賴方向控制），也讓 `registration` 不需要直接 import `account`（跨模組解耦）。同一個 interface 承載兩個不同層次的隔離責任。

**Port Interface 位置的促升規則**：

| 實作者在哪 | Port Interface 放哪 |
|-----------|-------------------|
| 同模組（Stub 或 JPA Adapter 都在同一模組） | `{module}/application/` |
| 另一個模組（需跨模組 import 此 interface） | `{module}` root（Modulith 只允許其他模組看到 root，見 [DEC-020](../DECISIONS.md)）|

**辨識**：
- Application Service 依賴它，但不 import 任何 Infrastructure 類別
- 目前有 Stub 或 Mock 實作（代表真實實作尚未建立）
- 代表跨模組的查詢能力（如「帳號是否已存在」）

---

**分支二：Port + Spring Data — 消費者定義 + 框架唯一實作**

結構與 SEAM 相同（消費者定義 interface），但框架在執行期直接提供實作，省略 Stub：

```
RegistrationApplicationRepository (interface) ← application/（Service 定義需求）
    ↑ extends
JpaRegistrationApplicationRepository          ← infrastructure/
    ↑ Spring Data 執行期自動實作（不需要寫 class body）
```

Port Interface 放在 `application/` 確保 Service 不依賴 JPA 細節。

---

**分支三：Extension Point / Collector — 提供者定義 + 多個實作並存**

提供者（如 `security`）定義 interface 作為擴展點，業務模組各自實作並貢獻，提供者在執行期透過 `List<interface>` 收集全部：

```
PublicPathContributor (interface)              ← security root（security 開放擴展點）
    ↑ 各模組各自實作
RegistrationPublicPathContributor              ← registration/infrastructure/
AdminPublicPathContributor                     ← admin/infrastructure/
    ↓ List<PublicPathContributor> 注入，執行期收集全部
SecurityConfig                                 ← security/infrastructure/
```

`security` 不 import 任何業務模組。實作者 implements 介面 → 依賴方向是「業務模組 → security」，不是反過來。

**與 SEAM 的關鍵差異**：SEAM 是「我現在只需要一個，但未來想換掉它」；Extension Point 是「我想在執行期知道所有參與者，不管有幾個」。

---

### Adapter

**定義**：Port interface 的具體實作。連接 Application layer 定義的抽象與外部系統（DB、API、第三方服務）。

**辨識**：`implements {SomePort}` 或 `extends JpaRepository<...>, {SomeRepository}`；依賴 Spring Data JPA、RestTemplate、外部 SDK 等 infrastructure 技術。

**放哪**：永遠放在「**實作者**所在模組」的 `infrastructure/`，不是 Port 定義者的模組：

```
AccountExistencePort (interface)  ← 定義在 registration
    ↑ 實作
JpaAccountExistenceAdapter        ← 放 account/infrastructure/（不是 registration/）
```

**四種形態**，對應上方三個分支：

| 形態 | 語法 | 對應分支 |
|------|------|---------|
| Stub Adapter | `class StubXxxAdapter implements XxxPort { return hardcoded; }` | 分支一（SEAM 暫時佔位）|
| Real Adapter | `class JpaXxxAdapter implements XxxPort { /* 查真實 DB */ }` | 分支一（未來 SLICE 補上，取代 Stub）|
| Spring Data Adapter | `interface JpaXxxRepository extends JpaRepository<E, ID>, XxxRepository` | 分支二（框架自動實作）|
| Contributor | `class XxxContributor implements PublicPathContributor { ... }` | 分支三（Extension Point 的貢獻者）|

Stub 與 Real 的差別只在行為：**Stub 對給定輸入永遠回傳確定的相同輸出，不查外部系統**（值可依測試情境設計，但不依賴運行期外部狀態）；Real 查真實外部系統。Stub 被 Real 取代時，消費者（Service）不需要改程式碼。

**命名慣例**：

| 角色 | 慣例 |
|------|------|
| Port Interface（消費者定義） | `{名詞}Port`（`AccountExistencePort`）/ `{名詞}Repository` |
| Extension Point（提供者定義） | `{動詞}{名詞}Contributor` / `{動詞}{名詞}Provider` |
| Stub Adapter | `Stub{名詞}Adapter`（`StubAccountExistenceAdapter`）|
| Real / Spring Data Adapter | `Jpa{名詞}Repository` / `Jpa{名詞}Adapter` |

---

### UseCase Interface（跨模組公開 API）
**定義**：暴露給其他模組呼叫的業務行為入口。定義「可以做什麼」，不定義「如何做」。Application Service 實作它。

**辨識**：
- 命名包含業務動詞（如 `ReviewApplicationUseCase`、`ListPendingApplicationsUseCase`）
- 其他模組的 Controller 依賴它
- 方法的 Javadoc 標注對應的 INV-### 說明哪些不變式會被執行

**放哪**：模組 root package

**與 Port interface 的區別**：

| 維度 | Port Interface (SEAM) | UseCase Interface |
|------|----------------------|-------------------|
| 方向 | 此模組對外部能力的需求（向外） | 此模組對外部提供的能力（向內接收呼叫） |
| 位置 | `application/`（或 root 若跨模組） | 模組 root |
| 實作者 | 其他模組或 Stub（在 infrastructure/） | 此模組自己的 Application Service |
| 命名 | `{Noun}Port` 或 `{Noun}Repository` | `{Verb}{Noun}UseCase` |

---

### Application Service
**定義**：Use Case 的協調者。組合 Domain 操作、Port 呼叫、持久化，但不持有業務規則本身（規則在 Entity 裡）。

**放哪**：`{module}/application/`

**辨識邊界**：Service 裡的 `if` 語句應該是「流程判斷」（找不到 entity → 拋 not found），而不是「業務規則判斷」（`if status != PENDING` 這種 → 應該在 Entity 方法裡）。如果 Service 在 `if` 裡直接操作狀態，是業務規則外洩到 Application layer 的信號。

**Service vs Entity 判斷規則**：

| 問題 | → 答「是」→ 放哪 |
|------|------------------|
| 行為執行前需要檢查 Entity 自身狀態？（如 `status == PENDING`） | Entity 方法 |
| 行為守護業務不變式（對應 INV-###）？ | Entity 方法 |
| 行為能直接用 `entity.doSomething()` 一行完整表達？ | Entity 方法 |
| 行為需要跨多個 Port / Repository 協調？ | Application Service |
| 行為的主體是一個流程（載入 → 操作 → 儲存），不是一個 Entity？ | Application Service |
| 行為需要呼叫外部服務（透過 Port interface）才能完成？ | Application Service |

**快速記憶**：Entity 是「這件事**能不能做**」（守門）；Service 是「**怎麼做完**這件事」（協調）。Service 裡不應出現 `if (application.getStatus() != PENDING)` 這樣的狀態判斷——那是 Entity 的職責，應該讓 Entity 方法的前置守衛拋出例外。

---

### Command / Query 物件
**定義**：Use Case 的輸入包裝。把呼叫者（通常是 Controller）的請求轉成 Application layer 的語言。

**放哪**：`{module}/application/`（僅在此模組內使用）

**Command vs Query 的區別**：
- **Command**：意圖改變系統狀態。例如 `SubmitApplicationCommand`——告訴系統「執行這個動作」。執行後系統狀態不同，通常不回傳業務資料（或只回傳 ID）。
- **Query**：意圖讀取系統狀態，不改變任何事。例如 `ListPendingApplicationsQuery`（若存在）——告訴系統「告訴我目前的狀態」。可以反覆執行，結果相同（冪等）。

**為什麼要把 HTTP request 翻譯成 Command？**

Controller 知道 HTTP 的語言（JSON 欄位名、HTTP method、路徑參數）。Application Service 知道業務的語言（email、username、決策）。Command 物件是兩者之間的翻譯層：

```
HTTP POST body { "email": "foo@bar.com", "name": "Foo" }
    ↓ Controller 翻譯
SubmitApplicationCommand(email="foo@bar.com", name="Foo")
    ↓ Application Service 執行
RegistrationApplication（domain entity）
```

好處：Application Service 的單元測試可以直接構造 Command，完全不需要 MockMvc 或 HTTP 環境。Controller 的測試也可以只測「翻譯是否正確」，不需要真正執行業務邏輯。

**原則**：Command 是 Application layer 的詞彙，不是 HTTP 詞彙，也不是 Domain 詞彙。Controller 負責把 HTTP request body 翻譯成 Command；Domain Entity 不知道 Command 的存在。

---

## 二、閉合判斷流程

```
這個型別需要跨模組可見嗎？
  ├─ 是 → 放 module root（例如 registration/ReviewDecision.java）
  └─ 否
       └─ Domain entity 的程式碼直接 import 並使用它嗎？
              ├─ 是 → 放 domain/
              └─ 否
                   └─ 它是「此模組對外部能力的需求」抽象（Port/SEAM）嗎？
                          ├─ 是 → 放 application/（Port interface）
                          │        infrastructure/（Adapter 實作）
                          └─ 否
                               └─ 它是 Use Case 的輸入/輸出嗎？
                                      ├─ 是 → 放 application/（Command/DTO）
                                      └─ 否
                                           ├─ HTTP 相關 → web/
                                           └─ 框架技術類 → infrastructure/
```

---

## 三、放置一覽表

| 物件類型 | 放置位置 | 典型命名 | 範例 |
|---------|---------|---------|------|
| Aggregate Root / Entity | `domain/` | `{Noun}` | `RegistrationApplication` |
| 狀態 Enum（entity 欄位型別） | `domain/` | `{Noun}Status` | `ApplicationStatus` |
| Domain Value Object | `domain/` | `{Noun}` | — |
| Application Service | `application/` | `{Noun}Service` | `RegistrationApplicationService` |
| Port Interface (SEAM) | `application/` | `{Noun}Port` / `{Noun}Repository` | `AccountExistencePort` |
| Stub Adapter（暫時） | `infrastructure/` | `Stub{Noun}Adapter` | `StubAccountExistenceAdapter` |
| JPA Repository（Adapter） | `infrastructure/` | `Jpa{Noun}Repository` | `JpaRegistrationApplicationRepository` |
| Command / Query 物件 | `application/` | `{Verb}{Noun}Command` | `SubmitApplicationCommand` |
| UseCase Interface（跨模組） | module root | `{Verb}{Noun}UseCase` | `ReviewApplicationUseCase` |
| DTO（跨模組傳遞） | module root | `{Noun}Summary` / `{Noun}Result` | `ApplicationSummary`（跨模組暴露原則詳見 [MODULE-TYPES.md](MODULE-TYPES.md) 業務模組「跨模組暴露原則」）|
| Enum（UseCase 參數，跨模組） | module root | `{Noun}Decision` / `{Noun}Type` | `ReviewDecision` |
| REST Controller | `web/` | `{Noun}Controller` | `AdminApplicationController` |
| HTTP Request/Response record | `web/` | `{Noun}Request` / `{Noun}Response` | `ReviewRequest` |

---

## 四、常見誤放警示

**業務規則在 Service 而非 Entity**
- 症狀：`if (app.getStatus() != PENDING) throw ...` 寫在 Service 裡
- 問題：Entity 的不變式守門應該在 Entity 方法中，Service 呼叫 `app.approve()` 就讓 Entity 自己守門
- 後果：Service 測試複雜；Entity 可被繞過直接 `setStatus()`

**Domain 知道 ReviewDecision**
- 症狀：`Entity.approve(ReviewDecision decision)` 接收外部決策 enum
- 問題：Domain 不應知道呼叫者的詞彙；Domain 的 API 是 `approve()` 和 `reject()`，決策轉換是 Application Service 的職責

**UseCase Interface 放在 application/ 而非 root**
- 症狀：其他模組 import `com.example.backend.registration.application.ReviewApplicationUseCase`
- 問題：`application/` 是 module-internal，Modulith verifier 會報違反
- 修法：移至 module root

**Port Interface 放在 root（不必要暴露）**
- 症狀：`AccountExistencePort` 放在 root，但只有 registration 模組內部使用
- 問題：不必要地擴大了模組公開 API，增加未來耦合風險
- 修法：確認是否真的需要跨模組；若只有一個模組使用，留在 `application/`
