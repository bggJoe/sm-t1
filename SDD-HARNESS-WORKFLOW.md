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

第一版不需要 Java AST；它只需要驗證 spec id 與 test id 的可追溯性。

決策後命令形狀：

```bash
python3 tools/sddh/check_trace.py
```

決策：Trace Checker v0 採 Python standard library first。

理由：本專案預期以 GitHub / Codespaces 類雲端開發為主要驗證場景，Python runtime 成本低於本機多人環境；同時 SDD Harness 的目標已不只是一次性 grep，而是逐步形成可重跑、可比較、可輸出給 AI closeout audit 使用的監督與評測工具。

工具選型不要只看「最少 runtime」，而要看 SDD Harness 是否準備把 trace check 固化成長期可重複的工程工具：

- 如果只需要一次性 baseline 或人工輔助，shell + ripgrep 足夠。
- 如果需要 structured report、JSON output、baseline diff、CI annotation，Python 的優勢會快速變大。
- 如果希望完全留在 Java ecosystem，可以改成 JUnit governance test；但這會把 repo governance 檢查放進 backend test lifecycle，語意上未必乾淨。
- AI 適合做語意審查與例外判讀，不適合取代 deterministic id coverage check。

Trace Checker v0 仍需保持極簡：不引入第三方 dependency、不建立完整 Python package、不做 Java AST parser、不做 plugin system、不自動修復 spec 或 test。

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

### 5.6 Tool runtime 選擇：shell、Python、Java/JUnit、AI 的分工

此選擇本身是一個 SDD Harness 設計點，不能只用「環境準備最少」或「未來功能最多」單一標準決定。

#### 5.6.1 哪些檢查適合固化成 tool？

適合 tool 化的是 deterministic、可重跑、判準明確的檢查：

- `AC-###` / `INV-###` 是否存在於 spec。
- `ac###_...` / `inv###_...` 是否存在於 test method。
- missing / orphan / disabled 分類。
- baseline diff。
- JSON / machine-readable report。
- CI annotation。
- module / package naming convention 的機械檢查。

這些檢查若交給 AI，每次輸出格式、漏報風險、判準穩定性都較難控管；而且它們不需要語意理解，使用 AI 反而浪費。

#### 5.6.2 哪些檢查適合 AI？

適合 AI 的是 semantic、需要脈絡、需要設計判斷的稽核：

- AC test 描述是否真的符合 SLICE 的 Given / When / Then 語意。
- 新增 UseCase 是否放在正確 module ownership 下。
- Architecture artifact 是否以正確抽象層級呈現，不過度畫 internal class。
- DEC 是否記錄了足夠的假設、取捨與反證條件。
- `SYSTEM-CAPABILITIES.md` 與實作決策之間是否存在張力。

這類檢查不適合過早寫成 parser，應先用 prompt / closeout audit checklist 累積幾次案例，再決定是否值得工具化。

#### 5.6.3 Python 的真正優勢

若 SDD Harness 未來需要以下功能，Python 有明顯優勢：

- structured report：可以穩定輸出 grouped sections 與 exit code。
- JSON output：方便給 CI、dashboard、AI closeout prompt 當輸入。
- baseline diff：容易保存 snapshot 並比較新增 / 修復 / 既有 debt。
- orphan / missing / disabled 分類：比 shell 更容易維護規則與測試。
- multi-file parsing：可逐步擴充 markdown、Java test source、package-info、LikeC4 model 的輕量 parser。
- CI annotations：可依 GitHub Actions / GitLab 格式輸出行號與 warning。

因此 Python 不是因為第一版 trace check 必須使用才引入，而是因為「Harness 若要成為可重複 workflow 工具」時，它能降低後續演化成本。

#### 5.6.4 Python 的代價與反證

Python 的代價：

- 新增 runtime prerequisite。
- 需要決定 Python 版本與安裝方式。
- 可能需要 `pyproject.toml`、lockfile 或 formatting/linting 規範。
- 若只做簡單 grep，會顯得過度設計。

反證條件：如果 SDD Harness 在 2～3 個 SLICE 後仍只需要「列出 missing / orphan / disabled」且不需要 JSON、baseline、CI annotation，那就不應引入 Python；shell + ripgrep 或 Java/JUnit 足夠。

#### 5.6.5 決策：Python standard library first

目前決策：Trace Checker v0 採 Python standard library first，而不是 shell first。

決策依據：

- 本專案預期在 GitHub / Codespaces 類雲端開發環境中驗證 SDD Harness，Python runtime 的環境成本可被集中管理。
- SDD Harness 的目標是建立方法論監督與評測工具，不只是一次性 grep。
- 後續很可能需要 structured report、JSON output、baseline diff 與 CI annotation。
- Python 可以產生 deterministic report，再交給 AI 做 semantic closeout audit，符合 tool / AI 分工。
- Python 僅作為 repo governance / SDD Harness runtime，不進入 backend production runtime。

限制：

- 初版只使用 Python standard library。
- 不新增 `requirements.txt`、`pyproject.toml`、lockfile 或第三方套件。
- 不建立完整 CLI framework。
- 不解析 Java AST。
- 不做自動修復。

反證條件：如果 2～3 個 SLICE 後，Trace Checker 仍只需要列出 missing / orphan / disabled，且沒有 JSON、baseline 或 CI 需求，則應重新評估是否退回 shell + ripgrep 或保留 Python 但不再擴張。

#### 5.6.6 可攜性附註：方法論擴散後 Python 不應成為唯一入口

本階段採 Python standard library first，是為了在 GitHub / Codespaces 類雲端開發環境中快速驗證 SDD Harness 方法論，並建立一個強力、可重跑的監督與評測工具。

但當方法論確定並擴散到非 GitHub 雲端環境時，Python 工具不應被理解為唯一可接受的落地形式。屆時應區分兩層：

- **Methodology contract**：Spec ↔ AC test、INV ↔ UT、architecture boundary、artifact drift 這些檢查語意與 fail / warning policy 必須保留。
- **Execution adapter**：Python、shell、Java/JUnit governance test、CI job、IDE task、或 AI prompt 都只是把 methodology contract 落地的執行載具。

因此，未來非 Codespaces 環境可以採取以下策略：

- Python 可維持為 reference implementation，提供最完整的 deterministic report / JSON / baseline 能力。
- 對 Python runtime 不友善的環境，可提供 shell wrapper、Java/JUnit governance test，或只使用 AI-assisted closeout prompt 作為降級輔助。
- 若某些團隊已有成熟 CI / build ecosystem，應優先把 SDD Harness 檢查接進既有 pipeline，而不是強迫所有環境安裝 Python。
- AI prompt 可輔助 semantic audit 與例外判讀，但不應取代可重跑的 deterministic checks；若無法執行工具，AI prompt 應明確標示為 degraded mode。

補充建議：後續工具文件應把 `tools/sddh/check_trace.py` 定義為「目前 repo 的 reference checker」，而不是把 Python 寫成 SDD Harness 方法論的一部分。這樣可以同時保留本階段的工具化效率，也避免方法論成熟後被單一 runtime 綁死。

### 5.7 Architecture notation 選擇：Structurizr DSL 與 LikeC4 的定位

目前 `architecture/workspace.dsl` 是既有累積式 SOT；LikeC4 則是後期研究方向。這裡的選擇不只是語法偏好，而是 SDD Harness 的 architecture artifact layer 要支援哪種工作流。

#### 5.7.1 選 LikeC4 的吸引力

LikeC4 的主要優勢：

- 環境準備較輕：不需要啟動 Structurizr image 才能看圖或互動。
- 語意較彈性：本專案不需要嚴格、正統 C4，只是借用分層視角呈現 system / container / module / usecase / port / adapter。
- 更貼近 Modulith：可以直接使用 `module` 這類 element kind，避免把 Spring Modulith module 勉強翻譯成 C4 component。
- 適合 AI audit：模型文字更接近 code-side module topology，較容易讓 AI 對照 `package-info.java`、root public API、Port / Adapter。
- local feedback loop 較短：若開發者能更容易開啟、修改、檢視架構圖，就更可能在每個 SLICE 真的維護它。

這些優勢符合 SDD Harness 的目標：架構圖不是文件裝飾，而是 AI coding 前後都會被使用的 workflow artifact。

#### 5.7.2 Structurizr DSL 的優勢

Structurizr DSL 的主要價值：

- 決策歷史已經累積在 `workspace.dsl`。
- C4 vocabulary 較成熟，適合對外溝通 system / container / component view。
- 既有 DEC 已把 workspace 定位為累積式 SOT，貿然切換會造成歷史斷裂。
- 若需要比較正式的 architecture governance，Structurizr 的生態與概念較穩定。

因此不應只因 LikeC4 開發體驗較好，就立刻宣告 Structurizr 失效。

#### 5.7.3 弱點與斷層

LikeC4 的弱點：

- 若 team 不熟 LikeC4，模型語意可能比 C4 更自由，導致命名與抽象層級失控。
- 自訂 element kind 雖彈性高，但也可能降低與標準 C4 工具 / 文件的互通性。
- 若 LikeC4 長期停留在 sandbox，而 `workspace.dsl` 同時繼續存在，會形成雙 SOT 漂移。
- AI 可能因 LikeC4 model 太貼近 code topology，而忽略 C4 原本服務 stakeholder 溝通的高層視角。

Structurizr DSL 的弱點：

- 本地環境較重，若需要 image / service 才能順利檢視，會降低每次 SLICE 更新架構圖的意願。
- C4 component vocabulary 與 Spring Modulith module 存在語意摩擦。
- 若專案實際目的不是正統 C4，而是 module topology + port / adapter 視覺化，Structurizr 可能顯得過度正式。

主要斷層：如果 `workspace.dsl` 是名義 SOT，但實務上大家只維護 LikeC4，名義 SOT 會快速失真；反過來，如果 LikeC4 只是 sandbox，卻被 AI 當成最新架構依據，也會造成錯誤推導。

#### 5.7.4 反證條件

選 LikeC4 作為 active architecture model 的反證條件：

- 新增 module / Port / Adapter 後，LikeC4 比 `workspace.dsl` 更容易遺漏或誤畫。
- LikeC4 的彈性導致每個 SLICE 抽象層級不同，無法形成穩定規範。
- closeout audit 無法從 LikeC4 穩定判斷 module relationship、public API、SEAM 狀態。
- 對外溝通仍大量依賴 C4/Structurizr，而 LikeC4 只服務開發者局部視角。

保留 Structurizr 作為唯一 SOT 的反證條件：

- 每次更新架構都因 image / tooling 太重而延遲或被跳過。
- AI / developer 實作時主要依賴 LikeC4，`workspace.dsl` 只在 retrospect 才補，造成實質漂移。
- Modulith module、port、adapter 的語意在 Structurizr 中需要大量註解才能避免誤解。

#### 5.7.5 暫定策略

短期不做一次性遷移。建議採「明確雙軌，單一 active」策略：

- `package-info.java` 是 code-side module contract。
- `architecture/workspace.dsl` 保持 legacy / formal architecture SOT，直到正式決策變更。
- `likec4-sandbox/architecture.c4` 可作為 candidate active model，用來驗證更輕的 local workflow 與 module topology 表達。
- 下一個涉及架構變更的 SLICE 可要求同時更新兩者一次，並在 retrospect 評估哪個更能支撐 SDD Harness。

若 LikeC4 在下一個 SLICE 中展現更低維護成本與更低漂移風險，應新增 DEC，明確把 active architecture SOT 從 Structurizr workspace 遷移到 LikeC4；否則維持現狀。

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

### Phase 2：實作 Trace Checker v0

目標：用極簡 Python 工具把 Spec ↔ Test trace 變成可重跑的 deterministic check。

輸出：

```text
tools/sddh/check_trace.py
```

第一步展開內容：

1. 掃描 repo root 的 `SLICE-*.md`，收集 `AC-###` 與 `INV-###`，並記錄來源檔案與行號。
2. 掃描 `backend/src/test/java/**/*.java`，收集 test method name 中的 `ac###` 與 `inv###`。
3. 偵測 AC / INV test method 附近或方法宣告前的 `@Disabled`。
4. 產生 human-readable text report，分類列出 missing / orphan / disabled。
5. 提供 `--format json`，讓後續 AI closeout audit、baseline diff 或 CI 可以消費 deterministic report。
6. 採歷史豁免模式：v0 先把既有 disabled 視為 warning；baseline 機制完成後，新增 disabled 才升級為 fail。

v0 不做：Java AST parser、semantic Given / When / Then 判斷、architecture model parser、自動修復、第三方套件。

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
- `likec4-sandbox/architecture.c4` = 研究 / audit candidate，可在下一個架構變更 SLICE 驗證是否升格。

待確認：是否要把 LikeC4 升格為 active architecture SOT？若升格，需要補 DEC 說明遷移理由、雙軌過渡期與反證條件。

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

已更新：第一個落地成果是 Trace Checker v0，而不是再停留在文件整理。

展開順序：

1. 實作 `tools/sddh/check_trace.py`。
2. 對現有 `SLICE-*.md` 與 `backend/src/test/java/**/*.java` 跑 baseline。
3. 先只回報 missing / orphan / disabled，不修正歷史 debt。
4. 將 deterministic report 作為下一輪 AI closeout audit 的輸入。
5. Trace Checker v0 驗證後，再整理 closeout audit prompt。

### Q5. Trace Checker 是否引入 Python

已決策：引入 Python 作為 Trace Checker v0 的 repo-level tool runtime，但限用 standard library。

決策原因：本專案以 GitHub / Codespaces 類雲端開發驗證 SDD Harness，且工具目標已從一次性 grep baseline 推進到方法論監督與評測工具；近期需要保留 structured report、JSON output、baseline diff、CI annotation 的演化空間。

保留反證：若 2～3 個 SLICE 後仍沒有 JSON / baseline / CI 需求，或 Python runtime 在實際 Codespaces / CI 中造成摩擦，則重新評估工具 runtime。

可攜性附註：Python 是本 repo / cloud-dev 驗證階段的 reference checker runtime，不是 SDD Harness 方法論本身。方法論擴散到非 GitHub 雲端環境後，可用 shell、Java/JUnit、CI adapter 或 AI prompt 作為降級或替代執行載具，但必須保留相同的 traceability contract 與 fail / warning policy。

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
