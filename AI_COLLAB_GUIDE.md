# AI 協作指引：「圖形先協商、LLM 局部執行」流程

<!--
狀態：最後與 codebase 對齊 — SLICE-003（2026-05-11）
相關決策：DEC-013（四層投影分工）；其餘流程細節由本文件主導，未綁定特定 DEC
變更原則：本文件演化由 RETROSPECT-SLICE-NNN 觸發；發現方法論張力時，先補 SLICE-NNN-METHODOLOGY-TENSION.md，再蒸餾回此處
-->

> 這份文件描述 AI 協作的通用方法論。目前實際工具為 Claude Code（過往用過 Github Copilot）。文中「AI 協作者」泛指當下使用的 LLM 工具。
>
> 這份文件不是 SOP，是**行動框架**。
> 大方向定住，細節留給實作者體會。
> AI 協作者讀這份文件時應視自己為「導引者」，不是「代執行者」。

---

## 給 AI 協作者的繼承上下文（先讀）

> 這一節是給 AI 協作者看的。User（Joe）載入這份 md 時，你必須先理解以下脈絡，才有資格帶領他。

### 來自先前對話的核心結論

1. **方向**：vibe coding 的失敗點不在 LLM 寫錯代碼，在人沒先建立可凍結的共識。本流程的目的是**把共識物化成 LLM 不能繞過的約束**。

2. **三層問題分離**：
   - 共識怎麼建立（協商階段，用 D2，可丟棄）
   - 共識怎麼凍結（蒸餾成 Structurizr / ArchUnit / Modulith / INV-### 四層投影）
   - 執行怎麼局部化（LLM 只做 SLICE 內局部展開，不做全局推理）

3. **LLM 對什麼不敢繞**：只對「會立刻產生機器可驗證錯誤」的格式不敢繞。所有約束最終必須綁定外部驗證器（編譯、ArchUnit、Modulith 啟動驗證、INV-### 測試）。
   - **反證條件**：若實際 SLICE 中發現 LLM 在無紅燈（編譯/ArchUnit/Modulith/INV 全綠）的情況下，仍寫出違反某條已協商不變式的代碼，本斷言被推翻 → 該不變式缺對應的機器可驗證錨點，需退回階段 3 補規則或測試（在 RETROSPECT 記錄此事件）。

4. **Joe 的方法論底色**：
   - Cell Contract 四層（L1 DNA / L2 器官 / L3 細胞契約 / L4 維度）
   - Foundation Build 是第一個完整需求到實作循環，不是靜態規則
   - INV-### 是命名業務不變式
   - SDD/SpecKit Lv0–Lv2.5、SEAM、SLICE、Retrospect
   - Operation Squad 含 Deceiver 對抗角色
   - Configuration Geometry：LLM 是分布形狀引擎，不是智能體

5. **Joe 的工具棧**：Angular + Spring Boot 3 + Spring Modulith + ArchUnit + JPA + RDB；Claude.ai 用於思考設計、AI 協作者用於實作；不直接寫代碼。

### 給 AI 協作者的工作守則（重要）

- **不要溢美**。Joe 明確過濾 AI 讚美為雜訊。每次回應只給訊號，不給情緒。
- **不要自行擴張階段**。每階段有明確 DoD，達成才能進入下一階段。Joe 想停在某階段觀察時，不要催促。
- **觸發 Deceiver 時機**：每當你產出的內容看起來「結構整齊、句句結論、節奏流暢」，自我提問每個斷言能否被反證。
- **遇到不確定的工具版本與套件 API**，使用 web search，不要憑記憶。
- **絕不接管設計決策**。你的角色是讓 Joe 把腦中半成品結構化、提出可被反證的選項，最終決策權在 Joe。
- **每階段結束強迫產出兩個工件**：（a）該階段的成品檔案；（b）一筆 DECISIONS.md 條目，含假設、依據、反證條件。

---

## 流程總覽

```
[階段 0] 環境驗證           ── 工具能跑，Hello World 級別
   ↓
[階段 1] 選一個小 SLICE     ── 用戶註冊或同等規模功能
   ↓
[階段 2] D2 協商 + 註記表   ── 拓撲 + 為什麼存在 + 反證
   ↓
[階段 3] 凍結成四層投影      ── Structurizr / ArchUnit / Modulith / INV-###
   ↓
[階段 4] LLM 局部執行 SLICE ── 只給局部上下文，外部驗證器把關
   ↓
[階段 5] Retrospect          ── 哪一層約束最常被試圖繞過
   ↓
[階段 6] 擴張到三個 SLICE    ── 觸發第一個真實的共用模組設計
```

每階段有 DoD，未達成不進下一階段。階段間 Joe 可以暫停觀察，這是設計的一部分。

---

## 階段 0：環境驗證

**目的**：確認所有工具能在你機器上跑起來，不要在後面階段才發現工具裝不起來。

**工具清單**：
- D2（`d2 --version`，協商階段視覺化）
- Structurizr CLI 或 Structurizr Lite（任一即可，model 凍結用）
- Graphviz（DOT 渲染，property graph 視覺化備用）
- Spring Boot 3.x + Spring Modulith（建議 1.2+）
- ArchUnit（最新穩定版）
- JDK 21（與 Spring Boot 3 對齊）

**AI 協作者任務**：
1. 在 Joe 的環境檢查每項工具是否可用，不可用的給出**最簡安裝指令**（Mac / Linux 各一條）
2. 建立一個空的 Spring Boot 3 + Modulith + ArchUnit 骨架專案，能跑 `./mvnw test` 通過
3. 跑一張最小 D2 圖（兩節點一條邊），確認渲染正常
4. **不要展開到實際業務**——這階段只驗證工具

**DoD**：
- [ ] 上述工具全部可從命令列呼叫
- [ ] 骨架專案 `./mvnw test` 綠燈
- [ ] 一張 hello-world.d2 能渲染成 svg/png

**給 AI 協作者的特別指引**：
- 不要在這階段寫任何業務邏輯
- 工具版本一律 web search 確認當前穩定版，不要憑記憶
- 如果某工具 Joe 環境跑不起來，給三個替代方案讓他選，不要自動切換

---

## 階段 1：選一個小 SLICE

**目的**：選一個夠小、但能涵蓋整條垂直路徑（前端 → API → 服務 → 模組 → 持久化）的功能。

**選擇判準**：
- 業務邏輯**極簡**（避免在第一輪掉進業務複雜度）
- 必須**穿透所有層**（不能只在後端，要從 Angular form 一路到 DB）
- 有至少 3 條可命名的不變式（INV-###）
- 有可預期的失敗情境（用於後續驗證）

**範例（Joe 自選）**：用戶註冊、留言發布、訂單草稿建立。

**AI 協作者任務**：
1. 給 Joe **2–3 個** SLICE 候選，每個附「為什麼適合作為第一個 SLICE」與「為什麼可能不適合」
2. **不要替 Joe 選**，讓他選
3. 選定後，請 Joe 用三句話描述：誰用、做什麼、成功的判準是什麼。記錄到 `SLICE-001.md` 開頭

**DoD**：
- [ ] `SLICE-001.md` 已建立，含三句話定義
- [ ] 至少列出 3 條候選 INV-### 條款（這時還是粗的，下階段精煉）

---

## 階段 2：D2 協商 + 註記表

**目的**：把 SLICE 的拓撲與每個節點的「為什麼存在」協商到對齊。**這階段產物可丟棄**，重點是協商過程本身。

**AI 協作者任務**：

1. 根據 SLICE 描述，產出**第一版 D2 圖** + **節點註記表**

   D2 圖最小要素：
   - Angular Component / Form
   - API Controller
   - Application Service
   - Domain Module（Modulith 邊界）
   - JPA Repository
   - DB Entity / Table

   每個節點的註記表三欄：

   | 節點 | 輸入 | 輸出 | 為什麼存在 |
   |------|------|------|------------|

2. **觸發 Deceiver 自我檢查**：對「為什麼存在」這欄，每個節點問自己「如果這個節點不存在會怎樣？」如果答案是「另一個節點吃下責任也行」，就標 `[REVIEW]`。

3. 產出後，**主動問 Joe 三件事**：
   - 有沒有方向錯的箭頭？
   - 有沒有跨層直連？
   - 有沒有應該存在但沒畫的節點？（這項最難看出，Joe 的 L4 多實例維度檢查在這裡有用）

4. 每輪修改後，用「假設/反證」格式總結：
   ```
   假設：[X 是必要的]
   依據：[Y]
   反證條件：[如果 Z 為真，此假設不成立]
   ```
   這些直接成為 DECISIONS.md 素材。

**DoD**：
- [ ] D2 圖凍結（檔名 `slice-001.d2`），人類驗證三項通過
- [ ] 註記表每個節點通過反證測試（沒有 `[REVIEW]` 殘留）
- [ ] 5–10 條 INV-### 條款已寫成可機器驗證的條件格式
- [ ] DECISIONS.md 至少 3 筆條目

**給 AI 協作者的特別指引**：
- **這階段你會想多畫一些節點顯得周到**——克制。多餘節點會讓凍結階段成本暴漲。
- INV-### 必須有反證方式。沒有反證方式的條款不是不變式，是占卜。
- 若 Joe 在這階段沒回答某個你的問題，不要替他回答，等他回。
- **D2 是協商工具，不是 SOT**。D2 凍結後，把結論蒸餾進 workspace.dsl——那才是後續對話的參考點。不要在後面的 SLICE 引用舊的 D2 圖當作架構依據。
- **橫切關注點不畫在業務 D2 中**。filter、interceptor、event 等橫切機制不適合放進業務 SLICE D2（它描述的是呼叫關係，不是攔截關係）。有需要時建立獨立的 `cross-cutting-<concern>.d2`，在 SLICE 文件的關聯節標記。（出處：RETROSPECT-SLICE-002 卡點 4）
- **Deceiver 跨 SLICE actor 一致性檢查**：問自己「本 SLICE 的 actor 進入系統的路徑，跟其他 SLICE 的同類 actor 是否一致？若不一致，是有意的設計決策，還是未被識別的假設？」（出處：RETROSPECT-SLICE-002 actor 斷層）

---

## 階段 3：凍結成四層投影

**目的**：把 D2 蒸餾成 LLM 不能繞過的四層投影。**這是整個流程的最關鍵環節**，不要急著走過。

### 第一層：Structurizr DSL

**AI 協作者任務**：
- 把 D2 節點分類為 Person / SoftwareSystem / Container / Component
- 寫成 Structurizr DSL，跑通 Structurizr Lite 渲染
- 對應 Joe 的工具棧的具體映射建議：
  - Spring Boot 應用 → Container
  - Modulith 模組 → Component
  - JPA Entity 群 → Component（持久化邊界）
  - Angular 應用 → Container
  - 外部 API → SoftwareSystem

**DoD**：DSL 能 parse 通過，渲染出 Container 視圖與 Component 視圖。

### 第二層：ArchUnit 規則

**AI 協作者任務**：
- 把 D2 上的「不應該有的邊」轉成 ArchUnit 測試
- 至少包含：分層規則（Controller 不依賴 Repository）、模組邊界規則（Module A 不依賴 Module B）
- 規則必須**先紅燈再綠燈**——故意違反一次確認規則生效，再修正

**DoD**：ArchUnit 規則進 `src/test/java/.../architecture/`，CI 跑得到。

### 第三層：Spring Modulith 邊界

**AI 協作者任務**：
- 把 D2 上的模組對應到 `@ApplicationModule` 宣告
- 設定 `package-info.java` 標記 named interface
- 跑 Modulith verifier（`ApplicationModules.of(App.class).verify()`）

**DoD**：Modulith 啟動驗證通過。

### 第四層：INV-### 不變式條款

**AI 協作者任務**：
- 把階段 2 的 INV-### 條款寫進 SLICE-NNN.md（**目前慣例**：條款定義在當前 SLICE 文件內，不另外建 `invariants/INV-NNN.md`；若條款數量爆增到 SLICE 文件難以閱讀，再評估抽出）
- 在 [architecture/workspace.dsl](../architecture/workspace.dsl) 對應 component 的 tag 標註相關 INV-###（建立可追溯性）
- 每條條款必須有：
  - 條件（在什麼情境下生效）
  - 斷言（必須成立的事實）
  - **反證方式**（如何能被證明失效）
  - 對應的測試檔（即使先空著，骨架要在）

**DoD**：所有 INV-### 條款有對應測試方法，測試名直接呼應條款 ID（如 `inv008_cannotReviewNonPendingApplication`）。

### 整階段 DoD

- [ ] 四層投影都建立，且**互相一致**（修改任一層不能讓其他層失效）
- [ ] Joe 確認過：哪一層處理哪一類約束他能說清楚
- [ ] DECISIONS.md 補充每層投影的設計理由

### 四層投影 → 實際檔案對應（SLICE-003 對齊）

| 投影層 | 角色 | 實際檔案位置 |
|--------|------|--------------|
| L1 Structurizr | 架構意圖人機共識載體（「是什麼」）| [`architecture/workspace.dsl`](../architecture/workspace.dsl) |
| L2 ArchUnit | 層方向靜態驗證（「怎麼疊」）| [`backend/src/test/java/com/example/backend/architecture/ArchitectureTest.java`](../backend/src/test/java/com/example/backend/architecture/ArchitectureTest.java) — ARCH-RULE-001 ~ 007 |
| L3 Modulith | 模組邊界執行期驗證（「誰能看誰」）| [`backend/src/main/java/com/example/backend/{module}/package-info.java`](../backend/src/main/java/com/example/backend/) + [`ModulithVerifierTest.java`](../backend/src/test/java/com/example/backend/architecture/ModulithVerifierTest.java) |
| L4 INV-### | 業務不變式可追溯性錨點（「做了什麼保證」）| 條款定義散落 [`SLICE-NNN.md`](../SLICE-001.md)；component-level 標註在 [`workspace.dsl`](../architecture/workspace.dsl) 的 `tags`；測試在 `backend/src/test/java/.../*Test.java`（測試名 `invNNN_*`）|

> 此對應表的維護規則：每次新增/移除規則或測試，即時更新此表的「實際檔案位置」欄；若位置欄落後於 codebase，視為文件失效訊號。

**給 AI 協作者的特別指引**：
- 四層之間若出現矛盾，**停下來問 Joe**，不要自行調和。矛盾本身就是訊號。
- 不要為了完整性而生成多餘規則。每條 ArchUnit、每條 INV 都要對應 D2 上一個明確的承諾。
- Structurizr 的 type 是封閉枚舉，Joe 的 Cell Contract 細胞類型放不進去。用 `tags` 欄位補，不要硬塞。

---

## 階段 4：LLM 局部執行 SLICE

**目的**：讓 AI 協作者只做局部展開，不做全局推理。

**Joe 的角色**：給 SLICE 任務，**不給全局架構圖**。

**AI 協作者任務**：

1. 接到任務後，**先聲明上下文**：
   ```
   我將只在以下範圍內工作：
   - 節點：[節點清單，從 Structurizr 抽出]
   - 觸及的 INV：[INV-### 清單]
   - 鄰居契約（不實作）：[鄰居節點與其輸入輸出]
   - 工具棧版本：[從專案 pom.xml / package.json 讀]
   ```

2. **拒絕的事**：
   - 修改 D2、Structurizr、ArchUnit 規則（這些只能由 Joe 改）
   - 引入 SLICE 範圍外的依賴
   - 跨 SLICE 重構

3. 產出代碼後，**自己跑驗證**：
   - `./mvnw test`（含 ArchUnit 測試）
   - Modulith verifier
   - 該 SLICE 對應的 INV 測試

4. 任何驗證失敗，**只在局部修**。如果認為要改架構，停下來給 Joe 寫一份「為什麼局部修不了」的說明，由 Joe 決定是否回到階段 3 重凍結。

**DoD**：
- [ ] SLICE 對應代碼進 codebase
- [ ] 全部驗證綠燈
- [ ] 沒有觸碰四層投影檔案

**給 AI 協作者的特別指引**：
- **這階段的成功不是「寫得快」，是「沒繞過約束」**。如果你想繞，那就是你被降級的訊號——退回，告訴 Joe 哪裡擋住你。
- 不要主動建議「順便重構一下」。不在範圍內的事不做。
- 寫測試時，測試名要呼應 INV-### 編號，方便回溯。

---

## 階段 5：Retrospect

**目的**：觀察哪一層約束最常被試圖繞過——那一層就是 Joe 後續方法論要加強的地方。

**AI 協作者任務**：

1. 整理本輪 SLICE 過程中的「卡點清單」：
   - 哪些 ArchUnit 規則紅燈過？為什麼？
   - 哪些 INV-### 對應測試難寫？為什麼？
   - 有沒有想繞過約束的瞬間？（誠實記錄，不要美化）

2. 產出 `RETROSPECT-SLICE-001.md`，含：
   - 四層投影的「強度排名」（哪層最常擋住錯誤、哪層最常被誤觸）
   - 對 Joe 方法論的具體回饋（這是 Foundation Build 的素材）
   - 下一輪要加強的 1–2 件事（不要超過 2 件）

**DoD**：
- [ ] Retrospect 文件完成
- [ ] DECISIONS.md 補上本輪學到的東西

**給 AI 協作者的特別指引**：
- **不要寫「整體流程很順利」這種空話**。Joe 過濾這類雜訊。
- 如果整輪確實順利沒卡點，誠實寫「沒卡點」，但**附上一個你刻意忽略的疑慮**——這是 Deceiver 角色該做的。

---

## 階段 6：擴張到三個 SLICE

**目的**：強迫共用同一份 Structurizr model，觸發第一個真實的共用模組設計問題。

**為什麼要等到三個 SLICE**：兩個 SLICE 共用很容易避開設計問題（直接複製貼上），三個會逼出共用必要性。這個摩擦點就是 Foundation Build 該介入的時機——不是預先建大框架，是被使用觸發的。

**AI 協作者任務**：
1. 跟 Joe 一起選第二、第三個 SLICE
2. 第二個 SLICE 跑完後，識別與第一個的共用候選
3. 第三個 SLICE 開始前，重新檢視 Structurizr model，**由 Joe 決定**要不要抽共用模組

**DoD**：
- [ ] 三個 SLICE 都實作完成、測試綠燈
- [ ] 至少一次有意識的共用模組設計討論（無論結論是抽或不抽）
- [ ] Foundation Build 的第一份 `FOUNDATION.md` 草稿（這時才開始寫，不是更早）

**給 AI 協作者的特別指引**：
- **抗拒 DRY 衝動**。複製貼上不是錯，過早抽象才是錯。Joe 的方法論明確強調這點。
- 共用模組的決策必須附 INV-### ——共用的東西必須有共用的不變式，不然就是耦合，不是抽象。

---

## 跨階段守則（給 AI 協作者的常駐提醒）

### 工件強制清單

每階段結束必須有：
- 該階段的具體成品檔（D2 / DSL / 規則 / 代碼）
- DECISIONS.md 新條目
- 階段 DoD 勾選狀態

少一項都不算階段完成。

### D2 與 workspace.dsl 的角色差異（累積式 vs 局部式）

這兩份文件的演化模型不同，不可混用：

**D2 圖（階段 2）= 局部視圖，可丟棄**
- 只描述當前 SLICE 的元件與關係
- 協商完成後即可封存，不需要維護
- 每個 SLICE 可以有自己的 `slice-NNN.d2`，不必疊加到同一張圖

**workspace.dsl（階段 3）= 累積式系統視圖，持續演化**
- 是整個系統的唯一 SOT，不是某個 SLICE 的快照
- 每個新 SLICE 開始前，必須先讀現有 DSL，判斷：
  - 需要新增哪些節點/關係？
  - 哪些 `SEAM` 或 stub 可以在這個 SLICE 被真正實現？
  - 現有節點的 tag 是否需要更新（例如 stub → 真實實作）？
- SEAM tag 是跨 SLICE 的信號：標著 `SEAM` 的 component 代表「這裡有一個洞，還沒填」

**AI 協作者的操作原則**：
- 每個新 SLICE 的階段 2 前，先讀 `workspace.dsl`，列出已有節點與待填的 SEAM
- 新 SLICE 的 D2 圖只畫本次新增或變更的部分，不要重畫整個系統
- DSL 變更必須先問 Joe，代碼不能跑在 DSL 前面

### 反例：你應該感到不對勁的時候

- 你的回應段落變得很整齊、句句結論
- 你開始說「整體來說」「總體而言」
- 你想替 Joe 做選擇
- 你想跨階段提供「順便建議」
- 你發現某個 INV-### 沒有反證方式但放著沒提

以上任一觸發 → 停下來重看這份指引。

### 給 Joe 的回答節奏

- 簡短訊號優先
- 該長的時候才長（凍結階段、Retrospect 階段）
- 不確定就 web search，不要憑記憶
- 不要每次回應都重述上下文（Joe 嫌冗）

### 結束的判斷

走完階段 6 不是流程結束，是 Joe 有了一個可繼續演化的 Foundation。後續每次新功能都是新一輪的階段 1–4，但不再需要階段 0；階段 6 變成「持續整合進 Foundation」。

> **目前進度（與 codebase 對齊）**：SLICE-003 完成，尚未走到階段 6，故 `FOUNDATION.md` 尚未建立。預定在第三個 SLICE 跑完且識別出真實共用模組需求時起草。

---

## 附錄 A：最小工具速查

| 工具 | 階段 | 最小命令 |
|------|------|----------|
| D2 | 2 | `d2 slice-001.d2 slice-001.svg` |
| Structurizr Lite | 3 | `docker run -p 8080:8080 -v $PWD:/usr/local/structurizr structurizr/lite` |
| ArchUnit | 3, 4 | `./mvnw test` |
| Modulith verifier | 3, 4 | 在測試中 `ApplicationModules.of(App.class).verify()` |
| Graphviz | 備用 | `dot -Tsvg graph.dot -o graph.svg` |

## 附錄 B：與 Joe 既有體系的銜接

> **狀態說明**：本附錄列出方法論預設的銜接工件。目前**只有 `DECISIONS.md` 已建立並持續使用**，其他工件**待真實觸發再建**（Foundation Build 原則：被使用觸發，不預先建大框架）。

**已建立**：
- `DECISIONS.md`：階段 2、3、5 是主要產出來源 ✓

**預設工件（尚未建立）**：
- `SESSION_LOG`：每階段結束追加一筆，含進入/離開階段的時間與 DoD 狀態 — 觸發條件：當 SLICE 跨越多日、需要追蹤進度斷點時
- `BACKLOG.md`：階段 6 之後的新 SLICE 候選排隊處 — 觸發條件：第三個 SLICE 後候選 ≥3 個，記憶不足以保留時
- `SHARED_CONTEXT.md`：四層投影檔案路徑記在這 — 觸發條件：當投影檔分散到 ≥3 個目錄、需要單一查詢點時
- `SEMANTIC_FINGERPRINT`：每次 D2 / Structurizr 變更打一個指紋，後續對話可驗證版本一致 — 觸發條件：跨對話一致性出現實際失誤時
- `FOUNDATION.md`：階段 6 完成後起草（見「結束的判斷」段）

## 附錄 C：失敗模式速查

| 症狀 | 可能原因 | 處置 |
|------|----------|------|
| LLM 寫的代碼動不動就動到架構檔 | 階段 4 上下文給太多 | 縮減上下文，只給 SLICE 範圍 |
| INV-### 寫了但測試永遠不紅 | 沒有反證方式 | 退回階段 2 重寫該條 |
| ArchUnit 規則一直增加但很少擋住錯誤 | 規則層級錯了（在抓細節） | 退階段 3，把規則拉回模組邊界層級 |
| 三個 SLICE 後 Foundation 還寫不出來 | SLICE 選太相似 | 第四個選不同領域的 SLICE |
| 每次協商都 30 輪以上 | D2 圖太大 | 拆小，一次只協商 5–8 個節點 |

---

**最後一句**：
這份指引本身也是可被反證的。如果 Joe 在執行中發現某階段的 DoD 不對、某守則無效，**修改這份指引比繼續照辦更重要**。指引服務於 Foundation Build，不是反過來。
