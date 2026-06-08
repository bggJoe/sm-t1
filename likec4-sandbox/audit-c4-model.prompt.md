# LikeC4 架構模型一致性審查

## 目的

對照 Java 代碼實際結構，審查 `likec4-sandbox/architecture.c4` 是否完整且正確。
輸出：缺口清單 + 可直接套用的 DSL 修正片段。

---

## 執行前必讀

先讀取以下兩份文件，建立審查基準：

1. `likec4-sandbox/LIKEC4_CONVENTIONS.md` — DSL 語法規則、relationship 定義位置、已知坑
2. `likec4-sandbox/architecture.c4` — 當前模型全文

---

## 掃描範圍

從以下位置取得代碼事實：

| 資料來源 | 目的 |
|---------|------|
| 所有 `package-info.java` | 確認模組邊界、`allowedDependencies` |
| 所有 module root package 的 interface | 確認 UseCase / Port 建模完整性 |
| 所有 `implements` 關係（Java class implements interface） | 確認 adapter 元素與 `-[implements]->` 是否齊全 |
| `ArchitectureTest.java` | 確認 ArchUnit 規則與 c4 關係方向是否一致 |
| `ModulithVerifierTest.java` | 確認 `@ApplicationModule` 邊界宣告 |

---

## 審查清單

### M1 Module 完整性

- 每個 `@ApplicationModule` 對應 c4 中一個 `module` 元素
- module 的 `allowedDependencies` 與 c4 中的 cross-module relationship 方向一致
  - 沒有 `allowedDependencies` = 不限制（仍需確認代碼實際依賴是否在 c4 中可見）

### M2 Public API 建模

- module root package 的每個 interface：
  - UseCase interface → `usecase` 元素
  - Port interface（SEAM）→ `port` 元素
- **DTO / record 不需建模**（ApplicationSummary、SubmitApplicationCommand 等）

### M3 Adapter 完整性（最容易遺漏）

對每個 Java class implements 跨模組 Port interface：
- [ ] 在對應 module 內有 `adapter` 元素
- [ ] 在 container block 有 `-[implements]->` 指向該 port
- [ ] 包含 **cross-cutting port**（如 `PublicPathContributor`）的所有實作模組

常見遺漏模式：只建了 port，沒建實作側的 adapter + relationship。

### M4 Relationship 語意正確性

| 場景 | 正確寫法 | 錯誤寫法 |
|------|---------|---------|
| Service implements UseCase（同模組內） | `reg_service -[implements]-> list_pending_uc` | `reg_service -> list_pending_uc 'implements'` |
| Adapter implements Port（跨模組） | `adapter -[implements]-> port`（在 container block） | `adapter -> port implements`（行尾） |
| Service calls Port（查詢） | `reg_service -> account_existence_port 'hasApprovedAccount(email)'` | — |

### M5 Views 功能可達性

- `module_topology`：model 中有 actor → module 直接 relationship（不能靠 actor → container 推導）
- `http_surface`：backend container block 中有 actor → controller 直接 relationship（不能靠 actor → module 推導）
- `ports_and_adapters`：所有 port + 所有 adapter 都有元素，且都有 `-[implements]->` 連結
- `cross_cutting_security`：view 的 `include` 列表包含所有 PublicPathContributor adapter 元素
- 每個 module 都有對應的 drill-down scoped view（`view id of <module> { include * }`）

### M6 ArchUnit 規則對應

| ArchUnit 規則 | C4 對應檢查 |
|-------------|-----------|
| ARCH-RULE-006：只有 security module 可用 Spring Security web | c4 中只有 security_mod 包含 SecurityConfig service |
| ARCH-RULE-007：admin 不得依賴 registration domain/infra | c4 中 admin → registration 的箭頭只連向 usecase 元素，不連向 service/adapter |
| ARCH-RULE-010：admin 不得依賴 account domain/infra | c4 中 admin → account 的箭頭只連向 usecase 元素 |

---

## 回報格式

對每個發現的不一致，輸出：

```
[缺口類型]  <M1/M2/M3/M4/M5/M6>
元素：      <Java class 或 package>
C4 現況：   <目前 architecture.c4 的狀態（缺少 / 語意錯誤 / view 不可達）>
修正 DSL：
  <可直接插入 architecture.c4 的片段，含正確放置位置說明>
```

若全部通過，輸出：`✅ 無缺口，architecture.c4 與代碼一致。`

---

## 修正執行規則

1. 所有修正必須符合 `LIKEC4_CONVENTIONS.md` 的 DSL 語法規則
2. Relationship 定義位置規則必須遵守（不可在 model 頂層用 4+ 層 dot-path）
3. 修正後執行 `get_errors` 確認無編譯錯誤
4. 修正後同步更新 `LIKEC4_CONVENTIONS.md`（若發現新的坑）

---

## 轉換備忘（給未來的 command/agent 製作）

- **輸入參數**：workspace root path（掃描範圍）
- **可並行**：M1~M6 各項掃描互相獨立，可平行執行
- **幂等性**：重複執行結果應一致（不修改代碼，只回報）
- **scope 限制**：只審查 architecture.c4，不審查 Structurizr workspace.dsl 或 D2 檔案
