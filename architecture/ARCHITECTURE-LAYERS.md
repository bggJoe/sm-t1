# Architecture Layers

<!--
狀態：最後與 codebase 對齊 — SLICE-003（2026-05-11）
相關決策：DEC-006（四層架構）、DEC-008（ArchUnit allowEmptyShould 骨架期設定）、DEC-013（四層投影分工）
變更原則：本文件變更必須同步更新 backend/src/test/java/.../architecture/ 下的 ArchUnit 規則。兩者不一致視為架構違反。
-->

本專案採用四層架構。每層的定義與允許/禁止的依賴方向如下。
此文件是 Phase 3 ArchUnit 規則的人類可讀 SOT。

---

## 層次定義

### Presentation Layer
**Package 位置**：`*.web` / `*.controller`  
**Angular 對應**：Component、Form、Page  
**Spring 對應**：`@RestController`、DTO（Request/Response）  
**職責**：接收外部請求，序列化/反序列化，回傳 HTTP 回應。不持有業務邏輯。

### Application Layer
**Package 位置**：`*.application`  
**包含**：Application Service、Command、Port interface（SEAM）  
**職責**：協調業務流程，持有 Use Case 邏輯，定義對外部依賴的抽象（Port）。不直接依賴 Infrastructure 實作。

> **Port interface 位置例外（跨模組促升）**：當 Port interface 的實作者在另一個模組時，interface 必須**從 `application/` 促升到模組 root**，否則跨模組 import 會違反 Modulith 邊界（root = 公開 API，sub-package = internal，見 DEC-020）。判斷規則與範例見 [MODULE-PLACEMENT.md「Port Interface 位置的促升規則」](MODULE-PLACEMENT.md)。

### Domain Layer
**Package 位置**：`*.domain`  
**包含**：Entity、Value Object、Domain Event、Domain Service（如有）  
**職責**：業務核心規則。不依賴任何其他層，不依賴 Spring framework（純 POJO）。  
> SLICE-001 暫無純 Domain 物件；RegistrationApplication 目前作為 JPA Entity 存在 Infrastructure 邊界。若後續業務規則增加，可抽出純 Domain 物件。

### Infrastructure Layer
**Package 位置**：`*.infrastructure`  
**包含**：JPA Repository 實作、Port 的 Adapter 實作、外部 API Client  
**職責**：實作 Application Layer 定義的 Port，處理持久化與外部系統整合。

---

## 依賴規則（允許 → 表示「可以依賴」）

```
Presentation  →  Application
Application   →  Domain
Application   →  Port interface（定義在 Application 層）
Infrastructure →  Application（實作 Port）
Infrastructure →  Domain
```

## 禁止的依賴（這些在 Phase 3 會成為 ArchUnit 紅燈）

| 禁止 | 原因 |
|------|------|
| Presentation → Infrastructure | 跨層直連，繞過業務邏輯 |
| Presentation → Domain | Controller 不應直接操作 Entity |
| Application → Infrastructure 實作類別 | 應依賴 Port interface，不依賴具體實作 |
| Domain → 任何其他層 | Domain 是最內層，不向外依賴 |
| 任何層 → Presentation | 沒有理由從後端層依賴 Controller |

---

## 與 Modulith 邊界的關係

Modulith 的模組邊界是**水平切割**（功能模組，如 `registration`、`account`）。  
Layer 規則是**垂直切割**（技術層次）。  
兩者獨立，合起來形成矩陣：

```
              registration   account   (future modules)
Presentation      ✓
Application       ✓
Domain            ✓
Infrastructure    ✓
```

跨模組依賴只能透過 Port interface，不能直接 import 另一個模組的內部類別。

---

## 變更規則

修改此文件必須同步更新 Phase 3 的 ArchUnit 規則。兩者不一致視為架構違反。

---

## 測試類別與職責

| 類別 | 答的問題 | 測試級別 | Spring Context | 位置 |
|------|---------|---------|----------------|------|
| Architecture | 結構規則對嗎？ | ArchUnit (no Spring) | 不需要 | `architecture/` |
| Invariant (INV) | 業務規則被遵守嗎？（禁止式） | Unit (mock 一切) | 不需要 | `<module>/application/` |
| Acceptance (AC) | 使用者/系統能完成業務目標嗎？（肯定式） | Integration | 需要 | `<module>/` (root) |
| End-to-End (E2E) | 整條鏈路含前端能跑嗎？ | 跨前後端 | 完整 | 待定 |

**INV ≠ AC**：INV 說「狀態必須是 PENDING」（禁止違反的規則），AC 說「訪客送出後收到 201 且 DB 有紀錄」（必須達成的能力）。INV 全綠不代表業務流程能跑通；兩類測試互補，缺一不可。

**AC 草稿自問清單**（每條 AC 寫完後對照）：
- 這條 AC 的 happy path（成功路徑）有對應測試嗎？
- 這條 AC 的 failure path（失敗路徑）有對應測試嗎，或明確說明為什麼不需要？
- 測試的前置狀態（Given）是自給的（`@BeforeEach` seed），不依賴外部或其他 SLICE 的 endpoint 嗎？

---

## SLICE 完成判準（依類別）

| SLICE 類別 | INV 必要性 | AC 必要性 | E2E 必要性 |
|-----------|-----------|-----------|-----------|
| Business | 視業務規則密度而定 | **必須**（Given/When/Then 業務劇本格式） | 視前端涉入而定 |
| Infrastructure | 通常無（橫向機制無業務規則） | **必須**（橫向機制條款格式，例：「機制 X 在情境 Y 下正確運作」） | 通常無 |
| Integration | 跨模組契約 INV | **必須**（整合後業務劇本） | 視涉入而定 |
| Migration | 前後狀態對比 | **必須**（既有業務劇本不破） | 視涉入而定 |

**核心原則**：所有 SLICE 類別都必須有 AC。AC 缺失 = SLICE 未完成。
