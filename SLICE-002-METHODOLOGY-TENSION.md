# SLICE-002 啟動前的方法論張力分析

> 本文件針對 SLICE-002 在 Phase 2 D2 協商前浮現的兩個方法論挑戰提供分析與建議。
> 不是 SOP，是討論材料。讀完後跟我（Joe）討論再決定怎麼走。

---

## 給 Claude Code 的工作守則

讀這份文件時：

1. **先確認你理解兩個挑戰的本質**，再給回應。不要急著進入解法。
2. **挑戰 1 的解法需要我先做決定**——你不能替我做。你的角色是把選項與後果說清楚。
3. **挑戰 2 的解法已經有方法論依據**（CROSS-CUTTING-CONCERNS.md），但仍需確認是否適用本情境。
4. **不要全盤照辦本文件的建議**——這份文件本身可被反證。對不同意處明確說「不同意，理由是 X」。
5. **觸發 Deceiver 自檢時機**：你產出的回應若看起來「方案完整、邏輯閉環」，停下來問每個結論是否有反證條件。
6. **完成評估後產出兩件事**：(a) 對兩個挑戰的方案選擇，(b) DECISIONS.md 候選條目草稿。

---

## 兩個挑戰的本質（先確立框架）

### 挑戰 1：Spring Security filter chain 屬於哪個 Modulith 模組？

**表面問題**：SecurityFilterChain 是全局 bean，不屬於任何業務模組。

**本質問題**：**這不是「該放哪」的問題，是「Modulith 切分依據還沒明確」的具體案例**。
配置放錯位置會在三個月後爆炸，但根本原因不在配置，在 module 切分依據未宣告。

### 挑戰 2：D2 / DSL 如何表達 filter chain 這類橫切關注點？

**表面問題**：D2 是節點-邊圖，filter 不被業務代碼呼叫，畫在哪都不對。

**本質問題**：**這不是 D2 表達能力不足，是「所有東西都該畫在 SLICE D2 上」這個假設不成立**。
filter chain 不該在業務 SLICE 的 D2 上有節點——它該在另一張獨立的圖上。

### 兩個挑戰的真正價值

不在解決它們，在它們**逼方法論把還沒明確的部分挑明**：
- 挑戰 1 → 強迫宣告 module 切分依據
- 挑戰 2 → 強迫區分 SLICE D2 與 cross-cutting 圖

兩者都是 Foundation Build 開始長出來的訊號。

---

## 挑戰 1：分析與建議

### 三個原始選項都有問題

| 選項 | 表面理由 | 真實問題 |
|------|---------|---------|
| `auth` 模組 = Security + JWT + AdminCredential | 業務+技術一起放 | 技術配置與業務資料耦合，未來換 auth 機制要動兩邊 |
| `security` 純技術 + `auth` 純業務 | 職責分清 | 兩個小 module 互相依賴緊密，違反「一起變的放一起」 |
| SecurityConfig 放根包 | 承認全局性質 | Modulith 對未模組化的 package 沒驗證能力，等於放棄治理 |

**這三個選項都「對某個切分依據有道理」**——但你的方法論（modulith-archunit-guide 第 2 節）已經明確：**module 切分依據必須先宣告**，否則任何選擇都建立在隱性假設上。

### 必須先填的元問題

照 modulith-archunit-guide 的格式，對 `auth/security` 這塊填：

```markdown
## Module 切分依據宣告（auth/security）
- 主依據：____________
- 次要依據：____________
- 明確排除的依據：____________（並寫下排除理由）
- 接受的代價：____________
```

填這四題之前，三個選項都是猜。填完之後，正確選項通常會自動浮現。

### 三種可能的填法演練

#### 填法 A：主依據 = 變更頻率

> 「auth 機制（JWT 演算法、token TTL、issuer）和 admin 業務（誰能登入、什麼角色）變更頻率不同」

**結論**：拆兩個 module（`security` + `identity`）。承認次要依據（DDD bounded context）會吃虧——admin 業務和 auth 機制必須跨 module 通訊。

#### 填法 B：主依據 = DDD bounded context

> 「Identity & Access 是一個業務領域，包含認證、授權、credential 管理」

**結論**：合一個 `identity` 模組（不叫 auth，叫 identity 更貼合 bounded context），技術配置和業務資料都進去。SecurityConfig 在這個 module 內。

#### 填法 C：主依據 = 生命週期

> 「SecurityConfig 是系統啟動時註冊的全局組件，AdminCredential 是運作時讀寫的業務資料，兩者生命週期完全不同」

**結論**：SecurityConfig 屬於「Application Bootstrap」層，不屬於任何業務 module。AdminCredential 屬於 `account` 或 `identity` module。

### 我的建議：填法 B

**理由有三**：

1. **SecurityFilterChain 不是純技術**——它的設定本身承載業務語意（哪些端點 public、哪些角色能存取什麼）。把它跟業務分開會讓「何時更新 filter 配置」變成跨 module 協調工作。
2. **AdminCredential 終究會跟 User/Account 發生關係**——未來 admin 可能也是某種 user，這個演化路徑下「auth 業務」和「identity 業務」會自然合併。
3. **Spring Modulith 1.4+ 對單一較大模組的支援優於多個高耦合小模組**——named interface 機制適合大模組對外開窗，不適合小模組頻繁互通。

### 建議的 module 結構

```
com.example.backend.identity/                   ← @ApplicationModule
├── api/                                        ← @NamedInterface
│   ├── AccountExistencePort.java               ← SLICE-001 的 SEAM 在這裡實作
│   └── AdminAuthenticationApi.java
├── internal/
│   ├── config/
│   │   └── SecurityConfig.java                 ← SecurityFilterChain bean 在這
│   ├── jwt/
│   │   ├── JwtIssuer.java
│   │   └── JwtVerifier.java
│   ├── credential/
│   │   ├── AdminCredential.java                ← Entity
│   │   └── AdminCredentialRepository.java
│   └── service/
│       └── AdminAuthenticationService.java
└── package-info.java                           ← module 宣告
```

**關鍵設計**：把 `AccountExistencePort` 的實作也放在 `identity` module 內。這樣 SLICE-001 的 SEAM 第一次被填實時，自然有家可歸——不需要再為「這個 Port 屬於哪」開新討論。

### 必須在 D2 協商前寫進 DECISIONS.md 的條目

```markdown
## DEC-XXX：identity 作為單一 module，承載 auth 機制與 admin/account 業務

- **假設**：identity 領域涵蓋認證、授權、credential 管理、account 識別
- **主切分依據**：DDD bounded context
- **次要依據**：變更頻率（auth 機制與 admin 業務變更頻率有差，但都比一般業務模組低）
- **排除**：技術-業務分層切（會造成兩個 module 高耦合）
- **接受的代價**：identity module 會比一般業務 module 大，內部有技術 + 業務兩種子結構，需要嚴格的 internal 包紀律
- **反證條件**：若 admin 業務複雜度成長到需要多人團隊維護，需重審是否拆出
```

**沒寫這個 DECISION 就直接畫 D2 是錯的**——這正是 modulith-archunit-guide 第 2 節警告的「未填寫宣告，後續所有 Modulith 設計都建立在隱性假設上」。

### 對抗性批評（這個建議的弱點）

- **identity module 太大的風險**：填法 B 把 auth 機制 + admin/account 業務全包進 identity。如果未來這個 module 內部超過 30 個類，會出現「module 內部需要再切分」的問題。Modulith 對此支援不算強。**對策**：在 identity module 內部用 package 分區，但不抽出獨立 module，直到真的痛。
- **「全局組件」的灰色地帶**：SecurityConfig 註冊的 SecurityFilterChain bean 是全 application scope。Modulith boundary verifier 不檢查 bean 的全域影響範圍，這是工具盲區。需要靠 ArchUnit 補：「除了 identity module 外，沒有其他 module 能宣告 SecurityFilterChain bean」。這條規則要寫進 SLICE-002 的驗證錨點。

---

## 挑戰 2：分析與建議

### 問題假設裡的錯誤

「D2 描述誰呼叫誰，但 filter 不是被業務代碼呼叫的」——對。

「在圖上畫它的位置不直觀」——這個結論要修正。**問題不是「畫在哪個位置」，是「該不該畫在 SLICE 業務 D2 上」**。

回到 CROSS-CUTTING-CONCERNS.md 的規範：**橫切片功能不該以節點形式進入業務 SLICE 的 D2**。SecurityFilterChain 是橫切片的標準範例。

### 三層分工

#### 第一層：SLICE-002 業務 D2 上**標 layer 屬性**

```d2
presentation: {
  label: "Presentation Layer\n[Auth: SecurityFilterChain - configured in identity module]"
  ...
}
```

業務節點不變，只在 layer group 標籤後加 `[ ]` 註記。**讀者一眼看到「這層有 auth 機制」但不會被誤導成 auth 是業務流程的一部分**。

#### 第二層：獨立的 `cross-cutting-auth.d2` 圖

專門畫 SecurityFilterChain 的內部結構：filter 鏈順序、JWT 驗證流程、SecurityContext 傳遞。**這張圖跟 SLICE 業務 D2 平行存在，互不依賴**。

```d2
direction: right

http_request: HTTP Request
filter_chain: SecurityFilterChain {
  jwt_filter: JwtAuthenticationFilter
  context_filter: SecurityContextHolderFilter
  authz_filter: AuthorizationFilter
}
controller: "@RestController"

http_request -> filter_chain.jwt_filter: validate token
filter_chain.jwt_filter -> filter_chain.context_filter: set authentication
filter_chain.context_filter -> filter_chain.authz_filter: check role
filter_chain.authz_filter -> controller: pass (or 401/403)
```

這張圖屬於 `identity` module 的內部設計圖，**不進任何 SLICE**。

#### 第三層：SYSTEM-CAPABILITIES.md 的 CC-001 條目

文字敘述完整規範：機制、範圍、驗證錨點、合規對應、演化路徑。**這是 SOT**，前兩層都是它的視覺投影。

### 為什麼這個分工是對的

- **Filter chain 的特殊性質**：它不是業務節點之間的呼叫關係，是 HTTP 請求的「**前置處理鏈**」。畫在業務 D2 上會給人錯誤印象——以為它是業務流程的一環。
- **業務 D2 的純度**：保持 SLICE D2 只表達業務拓撲，不被基礎建設細節污染，這是它對人和 LLM 都保持高訊號密度的關鍵。
- **Cross-cutting 圖的獨立性**：橫切機制有自己的演化節奏（SLICE 變、cross-cutting 不變；cross-cutting 變、所有 SLICE 都受影響）。獨立成圖才能反映這個生命週期差異。

### 預期會被質疑的反問

> 「但 SLICE-002 業務上就是『管理員登入後審核申請』，登入動作本身是業務功能，怎麼不畫？」

**回答**：登入動作是業務功能，但 JWT filter 鏈的執行不是。判準：

- **業務功能**：`POST /admin/login` 的處理邏輯（驗證 credential、發 token）→ 這是 SLICE-002 業務 D2 的內容
- **橫切機制**：`Authorization: Bearer <token>` 在每個後續請求被 filter 自動驗證 → 這是 cross-cutting 圖的內容

SLICE-002 的業務 D2 應該畫的是 `LoginController → AdminAuthenticationService → AdminCredentialRepository`，**不是 filter chain**。Filter chain 只在 layer group 註記。

### 對抗性批評（這個分工的弱點）

- **Cross-cutting 獨立圖容易被遺忘**：`cross-cutting-auth.d2` 跟業務 SLICE 不直接相關，更新頻率低，容易在 auth 機制改變後沒同步更新。**對策**：每次修改 SecurityConfig 必須觸發 cross-cutting 圖 review，這個機制需要加進 CROSS-CUTTING-CONCERNS.md 的「變更規則」節。

---

## SLICE-002 啟動前的具體準備清單

按順序處理。**前一步沒完成不進下一步**。

### 步驟 1（最先做）：宣告 module 切分依據

填寫 modulith-archunit-guide 第 2 節的「Module 切分依據宣告」表，記錄到 DECISIONS.md。

**預計時間**：30–60 分鐘的內省時間，不是純文書工作。**這個決定會影響後續所有 SLICE**，值得花時間。

**完成判準**：DECISIONS.md 有對應條目，四個欄位都填寫，並指出反證條件。

### 步驟 2：建立 identity module 的 Module Canvas

按 modulith-archunit-guide 第 3 節的七題清單，對 `identity` module 填 Canvas。**特別注意**：

- **第 1 題（存在理由）**：刪掉 identity module 系統會發生什麼？答「無法登入」太弱，要更具體
- **第 4-5 題（輸入/輸出契約）**：列出 `AdminAuthenticationApi`、`AccountExistencePort`、`SecurityFilterChain` 的對外承諾
- **第 6 題（INV）**：identity 守的 INV-### 包含哪些？（SLICE-001 已寫的 INV 中，INV-001 的 `hasApprovedAccount` 部分由 identity 守）
- **第 7 題（Transaction 邊界）**：admin login 跟 SLICE-001 的 registration application 寫入怎麼隔離？

**完成判準**：`docs/modules/identity-canvas.md` 七題都有具體答案，沒有「不知道」「Spring 會處理」這類紅旗訊號。

### 步驟 3：跑 Phase 1 三大壓力測試

特別是「測試三：刪除模擬」——`identity` module 暴斃 5 分鐘會發生什麼？**這個答案會驗證你 module 切分的合理性**。

**完成判準**：三題壓力測試都在 30 秒內能給出具體技術路徑。

### 步驟 4：開始 SLICE-002 D2 協商

**關鍵紀律**：
- 不在業務 D2 上畫 filter chain
- Filter chain 進獨立的 cross-cutting 圖
- SLICE-002 業務 D2 看起來會跟 SLICE-001 類似簡潔——這是好事

---

## 一個方法論演化的隱藏訊號

「SLICE-002 是基礎建設 SLICE，不是業務功能 SLICE」這個觀察本身比兩個挑戰的解答更重要。

直覺感受到 SLICE-002 跟 SLICE-001 性質不同——這個直覺是訊號：**「SLICE」這個概念可能不是同質的，需要再細分**。

### 候選分類

| 類別 | 範例 | D2 重點 |
|------|------|--------|
| **Business SLICE** | SLICE-001 註冊申請 | 業務拓撲 |
| **Infrastructure SLICE** | SLICE-002 引入 auth | 橫切機制 |
| **Integration SLICE** | 跨業務模組整合（譬如 registration ↔ account） | 跨 module 通訊 |
| **Migration SLICE** | 把 stub Port 升級為真實實作 | 前後狀態對比 |

### 對方法論的影響

不同類別的 SLICE 有不同的方法論需求。**目前方法論假設所有 SLICE 同質——這個假設遇到 SLICE-002 開始破裂**。

這正是 Foundation Build 該長出來的訊號。SLICE-002 完成後做 Retrospect 時，這是值得獨立記錄的方法論演化點。

---

## 預期影響

- **SLICE-002 比 SLICE-001 慢 1.5–2 倍**：這個慢不是失敗，是方法論長大的成本
- **Foundation 的具體形式開始浮現**：identity module 邊界 + cross-cutting 圖體系 + SLICE 分類擴展
- **三件事會在 SLICE-002 完成後成為方法論的永久部分**

---

## 給 Claude Code 的回報格式

完成評估後，給我一份摘要：

```markdown
## 對挑戰 1 的回應
- 是否同意「先宣告 module 切分依據」是必要前置？[是/否，理由]
- 對三種填法（A/B/C）的評估
- 對「填法 B（identity 單一 module）」的同意度與保留意見
- DECISIONS.md 候選條目草稿

## 對挑戰 2 的回應
- 是否同意「filter chain 不進業務 D2」原則？[是/否，理由]
- 對三層分工（layer 標籤 / cross-cutting 圖 / CC-001 條目）的評估
- 是否需要先更新 CROSS-CUTTING-CONCERNS.md 的「變更規則」節？

## 我發現的額外問題
（如有，明確列出。如無，明確寫「無」。不要為了顯得周到而虛構問題。）

## 建議的執行順序
（按四步驟清單給出時程估算與優先順序）
```

---

## 變更規則

修改本文件時：
1. 每次討論的結論都應該回寫進相關的 SOT 文件（DECISIONS.md、CROSS-CUTTING-CONCERNS.md、SYSTEM-CAPABILITIES.md）
2. 本文件本身在 SLICE-002 完成後可丟棄——它的價值是觸發 Foundation Build 演化，不是長期維護
3. 若 SLICE-003 又遇到不同類別的張力，建立新的對應分析文件，不要在本文件上追加
