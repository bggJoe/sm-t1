# SDD Harness Workflow Decision Reference

> 狀態：決策參考草稿（不作為執行規則）
> 目的：整理 SM-T1 實驗專案目前對 AI coding / SDD workflow 的收斂結論，作為後續 session 展開任務時的事實基礎。

---

## 1. 文件定位

本文件不是 `AGENTS.md`。

理由：

- `AGENTS.md` 適合放「每個 session 都必須遵守」的操作規則，例如命令、禁忌、目錄慣例、提交規則。
- 本文件目前承載的是方法論設計、決策參考、待確認問題與分階段落地建議。
- 若直接放入 `AGENTS.md`，容易把尚未決策的提案誤升格為硬規則，反而掩蓋實驗訊號。

後續建議：

1. 先以本文件作為 decision reference。
2. 等 SDD Harness workflow 經過至少 1 個新 SLICE 驗證後，再把穩定、不可變的操作規則蒸餾成 repo-root `AGENTS.md`。
3. `AGENTS.md` 應保持短而硬；本文件可以保持分析性與演化性。

---

## 2. 背景與目標

SM-T1 的核心不是一般 Spring Boot 功能開發，而是驗證一條 AI coding / SDD 場景專用的 workflow：

> Spec 經過多層 context 轉換後，不能只靠 LLM 記憶與善意，必須在某些節點形成硬約束，讓 AI coding 不能靜默漂移。

本專案已經有以下基礎：

- SLICE 文件描述業務範圍、INV、AC。
- ArchUnit 驗證 layer / module ownership 相關規則。
- Spring Modulith verifier 驗證模組邊界。
- `architecture/workspace.dsl` 與 LikeC4 sandbox 描述模組、public API、Port / Adapter、cross-module relationship。
- `DECISIONS.md` 記錄架構決策的假設、依據、反證條件。

SDD Harness 的目標不是取代這些工件，而是把它們串成可重複使用的 workflow。

---

## 3. 核心原則

### 3.1 Spec 不直接綁 production object

Spec / SLICE / AC / INV 不應直接規定 production class 或 method 的形狀。

原因：production code 同時受以下力量拉扯：

- module ownership
- domain modeling
- dependency direction
- transaction boundary
- orchestration responsibility
- future evolution

若每條 AC 直接對應一個 production class，容易產生 `Ac###Handler` 類型的測試案例污染。

### 3.2 Spec 必須硬綁 BDD / AC Test

Spec 的可追溯性應由 BDD / AC test 承擔。

規則方向：

- `AC-###` 必須對應 `ac###_...` test。
- `INV-###` 必須對應 `inv###_...` unit test。
- Spec id 或描述改變時，對應 test id / 描述必須同步調整。
- Production code 不需要出現 `AC-###` 或 `INV-###` 命名，但其行為必須讓對應測試通過。

### 3.3 Production code 由 OOD + 固定架構規則推導

Production code 的形狀由以下約束共同決定：

- Spring Modulith module boundary。
- ArchUnit layer / dependency rules。
- module root public API 規則。
- Port / Adapter / SEAM 規則。
- Domain object 與 Application Service 的責任分工。
- Cross-cutting capability 規範。

這讓 AI 不再從空白 spec 自由發揮，而是在固定架構框架內推導 OOD。

---

## 4. SDD Harness 的最小必要檢查層

不要一開始建立完整 requirements traceability matrix。初版只需要四層。

### Layer 1：Spec ↔ BDD / AC Test Trace

目的：確保 spec 的可觀察行為沒有只停在 markdown。

檢查問題：

- 每條 `AC-###` 是否有對應 BDD / integration test？
- test method name 是否保留 `ac###`？
- test 描述是否仍符合 spec 的 Given / When / Then？
- 是否有 test 多出 spec 沒有的行為承諾？
- 是否存在 `@Disabled` 的 AC test？若存在，是否登記為 known debt？

這層不檢查 production code。

### Layer 2：INV ↔ UT Trace

目的：避免 BDD 太粗，只驗結果，沒有把業務不變式局部鎖住。

檢查問題：

- 每條 `INV-###` 是否有對應 unit test？
- test method name 是否保留 `inv###`？
- failure path 是否被測到？
- 測試是否只驗證規則，不混入 HTTP / DB / framework 細節？
- 是否存在 `@Disabled` 的 INV test？

### Layer 3：Code ↔ Modulith / ArchUnit Boundary

目的：防止 AI 為了讓 BDD / UT 過關而亂接 module 或破壞 layer。

檢查問題：

- module allowed dependencies 是否符合設計？
- 跨模組是否只碰 root public API？
- Portal module 是否仍維持 thin portal？
- Application layer 是否偷依賴 Infrastructure？
- Domain layer 是否保持向內純粹？
- Cross-cutting module 是否沒有反向依賴業務模組？
- Modulith verifier 是否通過？

這層是硬約束，不是建議。

### Layer 4：Code ↔ Architecture Artifacts Drift Check

目的：避免 architecture DSL / LikeC4 / package-info.java 和 code 逐漸漂移。

初版只檢查架構級事實：

- 每個 `@ApplicationModule` 是否在 architecture model 中存在？
- 每個 module root public interface 是否在 architecture model 中可見？
- 每個跨模組 Port / Adapter 是否有建模？
- `allowedDependencies` 是否與 architecture model 的 cross-module relationship 大方向一致？
- `package-info.java` 是否有 public API / internal / 不負責事項 / dependencies 的描述？
- 架構選擇是否有 DEC 條目記錄假設、依據、反證條件？

不要檢查每個 internal class 是否都被畫進圖。

---

## 5. 建議打造的工件

### 5.1 Prompt Pack（最先做）

先打造 prompt，不急著打造 CLI 或 agent。

建議 prompt：

1. `slice-intake.prompt.md`
   - 輸入：需求 / 修改目標。
   - 輸出：SLICE 三句話、In / Out scope、候選 AC / INV、涉及 capability。
   - 不動 code。

2. `architecture-freeze.prompt.md`
   - 輸入：SLICE 草稿、現有 workspace / LikeC4 / package-info。
   - 輸出：需要新增 / 修改的 module、UseCase、Port、Adapter、cross-module dependency、DEC 草稿。
   - 不動 code。

3. `spec-to-test.prompt.md`
   - 輸入：SLICE 中的 AC / INV。
   - 輸出：BDD / AC test method plan、INV unit test plan。
   - 明確禁止先設計 production class 形狀。

4. `implementation-plan.prompt.md`
   - 輸入：測試計畫與 architecture freeze。
   - 輸出：production code responsibility plan。
   - 必須回答：哪個 module owns business meaning？哪些是 root public API？哪些是 internal service？哪些是 Port / Adapter？

5. `closeout-audit.prompt.md`
   - 輸入：完成後的 repo。
   - 輸出：四層檢查結果。

### 5.2 Trace Checker（第一個值得自動化的小工具）

最小功能：

- 掃描 `SLICE-*.md` 中的 `AC-###` / `INV-###`。
- 掃描 test code 中的 `ac###_...` / `inv###_...`。
- 回報 missing / orphan / disabled。

第一版不需要 Java AST。ripgrep、shell、Python 任一即可。

可能命令：

```bash
scripts/check-spec-test-trace.sh
```

或：

```bash
tools/sddh/check_trace.py
```

### 5.3 Architecture Check Runner

第一版只串現有工具：

```bash
./mvnw test -Dtest=ArchitectureTest,ModulithVerifierTest
```

再搭配 LikeC4 / workspace audit prompt 做人工或 AI-assisted 檢查。

不要一開始做完整 Java AST ↔ C4 model generator。

### 5.4 Codex Skill（延後）

Prompt pack 經過 2～3 個 SLICE 驗證後，再考慮包成 skill。

Skill 應負責：

- 自動讀必要文件。
- 根據任務模式執行 intake / freeze / test plan / implementation plan / closeout audit。
- 保持輸出格式穩定。

不要在規則尚未穩定時過早 skill 化。

### 5.5 Agent（先不要）

Agent 適合平行獨立稽核，例如：

- Spec ↔ Test trace audit。
- LikeC4 ↔ package-info audit。
- ArchUnit failure explanation。

但目前 workflow 還在設計期。過早導入 agent 會增加不可控變數。

---

## 6. 分階段落地建議

### Phase 0：整理現況，不寫工具

目標：把目前 repo 當成 case study，列出已存在的 workflow anchors。

輸出：

- 現有 AC / INV naming convention。
- 現有 ArchUnit / Modulith checks。
- 現有 architecture artifacts。
- 現有 drift / tension。

### Phase 1：定義 Trace Rules

目標：把 Spec ↔ BDD / AC Test 硬綁定寫成規則。

輸出可為：

```text
SDD-HARNESS-TRACE-RULES.md
```

或合併進本文件。

### Phase 2：實作 Trace Checker

目標：最小自動化。

輸出：

```text
scripts/check-spec-test-trace.sh
```

或：

```text
tools/sddh/check_trace.py
```

### Phase 3：定義 Architecture Harness

目標：把現有 ArchUnit / Modulith / artifact audit 串成固定 closeout check。

輸出：

```text
SDD-HARNESS-ARCH-RULES.md
```

或合併進本文件。

### Phase 4：建立 Closeout Audit Prompt

目標：每個 SLICE 完成前，用固定 checklist 收斂。

輸出：

```text
closeout-audit.prompt.md
```

### Phase 5：萃取 AGENTS.md / Skill

等 workflow 經過至少 1 個新 SLICE 驗證後：

- 把穩定不可變規則寫入 `AGENTS.md`。
- 把 prompt pack 包成 skill。
- 保留本文件作為 decision history，而不是操作規則。

---

## 7. 現有專案可立即驗證的點

### 7.1 AC / INV trace coverage

用 `SLICE-001.md`～`SLICE-004.md` 對照 test method：

- 找 missing AC / INV。
- 找 orphan test id。
- 找 disabled id。
- 找 AC 編號和 SLICE 文件不一致之處。

已知樣本：`AC-002` 對應 test 目前是 `@Disabled`，適合作為 Trace Checker 的歷史 debt 案例。

### 7.2 Admin thin portal 是否被架構測試守住

現有 ArchUnit 規則已經能驗證：

- admin 不可依賴 registration domain / infrastructure。
- admin 不可依賴 account domain / infrastructure。

這是「BDD 通過但 code ownership 錯」的防線。

### 7.3 Architecture artifact drift

可用 `likec4-sandbox/audit-c4-model.prompt.md` 的 M1～M6 檢查清單，對照：

- `package-info.java`
- module root package interface
- Java `implements` 關係
- ArchUnit rules
- Modulith verifier

第一版建議人工 / AI-assisted audit，不做自動修復。

---

## 8. 明確不要做的事

初版不要做：

- Cucumber / `.feature` framework。
- 每條 AC 對應 production class。
- production method 上加 spec id annotation。
- 完整 requirements traceability matrix。
- 自動修改 architecture DSL。
- full Java AST ↔ LikeC4 model generator。
- 多 agent pipeline。
- 非 Spring Boot 技術棧支援。
- 狀態機 / 事件工具，除非下一個 SLICE 真的引入新狀態流或 domain event。

---

## 9. 待決問題

### Q1. Architecture SOT 選擇

短期建議：

- `package-info.java` = code-side module contract。
- `architecture/workspace.dsl` = 目前正式凍結 SOT。
- `likec4-sandbox/architecture.c4` = 研究 / audit candidate。

待確認：是否要把 LikeC4 升格為 active architecture SOT？

### Q2. `@Disabled` 的 AC / INV test 如何處理

建議選項：

1. 嚴格模式：任何 AC / INV `@Disabled` 都 fail。
2. Debt 模式：允許 disabled，但必須在 debt registry 登記。
3. 歷史豁免模式：舊的 disabled 先 warning，新 SLICE 禁止。

初步建議：選 3，避免第一版 Harness 被歷史狀態卡死。

### Q3. 技術棧範圍

初版建議只支援：

- Spring Boot
- Maven
- JUnit
- ArchUnit
- Spring Modulith

不要現在支援 Angular / Playwright / multi-repo。

### Q4. 第一個落地成果

建議先做文件，再做 script：

1. 完成本文件與 trace rules。
2. 再做 `check-spec-test-trace`。
3. 再包 closeout audit prompt。

---

## 10. 一句話收斂

SDD Harness 初版應該是：

> prompt pack + trace checker + architecture check runner + closeout audit checklist。

它的核心不是讓 spec 直接控制 production object，而是讓：

- Spec 硬綁 BDD / AC test。
- INV 硬綁 unit test。
- Production code 受 Modulith / ArchUnit / OOD 約束。
- Architecture artifacts 只同步 module、public API、Port / Adapter、cross-module dependency 等架構級事實。

其餘稽核先保持可選，等真的出現漂移再升級。
