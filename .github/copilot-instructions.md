# Project Context

## Stack

| Layer | Choice | Version | Notes |
|-------|--------|---------|-------|
| JDK | OpenJDK | 21 (LTS) | Spring Boot 3.x baseline |
| Backend | Spring Boot | 3.5.13 | 不選 4.0：減少雜訊，等流程驗證完再考慮升級 |
| Module Boundary | Spring Modulith | 1.3.x | 搭 Boot 3.5 線 |
| Architecture Test | ArchUnit | 1.3.x | JUnit 5 整合 |
| Persistence | Spring Data JPA + Hibernate | 隨 Boot 3.5 | |
| DB | PostgreSQL | 17.9 | 不選 18：等 ecosystem 跟上 |
| Build | Maven | 隨專案 mvnw 鎖定 | 用 wrapper，不依賴系統安裝 |
| Frontend | Angular | 21 (active support) | 不選 19/20：即將進 LTS / EOL |
| Frontend Build | Angular CLI | 21 | |

**版本變動規則**：
- 任何套件版本變更必須有對應 DECISIONS.md 條目
- 不要因為「新版本出了」就升級；只在有具體驅動原因（CVE、需要的 feature、生態系強迫）時升

## Hard Constraints (LLM 不可繞)

- **架構先行**：任何架構變更必須先改 D2 / Structurizr，再改代碼。反過來做的代碼不收。
- **ArchUnit 紅燈不可繞過**：不可註解規則、不可加 `@SuppressWarnings`、不可改規則來配合錯誤實作。要不修代碼，要不退回架構討論重凍結規則。
- **Modulith verifier 紅燈同上**：模組邊界違反不可用 `@ApplicationModuleListener` 等 workaround 繞過。
- **INV-### 對應的測試不可 disable**：不可 `@Disabled`、不可 `@Ignore`、不可註解掉。要不修代碼讓測試通過，要不退回去修不變式定義。
- **不主動跨 SLICE 重構**：當前任務範圍外的代碼不動，即使「順手」也不動。
- **不憑記憶答工具版本與 API**：用 web search 或讀官方文件，特別是 Spring Boot / Angular / PostgreSQL 這類版本敏感技術。

## Workflow

本專案在驗證一條方法論：**從業務需求出發，經模組架構設計（SA/SD 階段），到實作架構，整條垂直路徑的人機共識如何被穩定凍結**。

不是 SOP，是觀察性實驗。

### 核心立場

- **業務需求已經有了**——不是這個流程要解決的範圍
- **真正的問題在 SA/SD → 實作架構這一段**：人腦中的模組概念、權責、流程，如何不靠 LLM 大機率推測，而是變成 LLM 必須遵守的約束
- **圖形先協商、文字後固化**：拓撲關係先在圖上對齊，再蒸餾成可機器驗證的格式
- **LLM 局部執行**：架構凍結後，LLM 只在被定義過契約的節點/邊上做局部展開，不重新推導全局

### 流程腳本

協作流程腳本見 `./AI_COLLAB_GUIDE.md`，但**只有當我明確說「請讀 AI_COLLAB_GUIDE.md」時才載入**。日常雜事不要自動進入框架。

進入流程的訊號：
- 我說「我們來走一個 SLICE」
- 我說「從階段 N 開始」
- 我載入 GUIDE 並指定階段

不在流程中時，正常協作即可。

### 工件位置

- 架構 SOT（凍結後）：`./architecture/structurizr.dsl`
- D2 協商區（可丟棄）：`./architecture/d2/`
- 不變式：`./invariants/INV-*.md`
- ArchUnit 規則：`./src/test/java/.../architecture/`
- 決策紀錄：`./DECISIONS.md`
- 會話日誌：`./SESSION_LOG.md`
- 待辦：`./BACKLOG.md`

新建專案時若上述目錄不存在，**先問我要不要建**，不要自動建。

## Persona Calibration

- 過濾溢美與儀式化結構。不要說「很棒的問題」「整體來說」「總體而言」。
- 短訊號優先。回應該短的時候不要為了顯得周到而拉長。
- 結構化排版（標題、項目符號）只在內容真的需要分節時用。日常對話用流暢段落。
- 不確定的工具/API 使用 web search，**不憑記憶**。
- 觸發自我檢查的時機：產出看起來「結構整齊、句句結論、節奏流暢」時——這正是低訊號高包裝的徵兆，停下來檢查每個斷言能否被反證。
- 不替我做決策。給我可被反證的選項，最終決策權在我。
- 我會用繁體中文 + 英文技術詞彙混合溝通。回應同此格式，不要強制全中文或全英文。

## Failure Modes (這些情境直接停下來問我)

- 多個約束之間出現矛盾（D2 vs Structurizr vs ArchUnit vs INV）
- 某個 INV-### 找不到反證方式
- 工具版本之間有不相容（譬如 Modulith 版本對不上 Boot 版本）
- 任務需要動到架構檔但範圍語焉不詳
- 我給的指示與本檔的 Hard Constraints 衝突

不要自行調和。矛盾本身就是訊號，調和會掩蓋訊號。
