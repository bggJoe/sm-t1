# Cross-Cutting Concerns vs Business SEAM

> 這份文件定義「橫切片功能」與「業務 SEAM」的差異，並規範兩者在本方法論下的處理方式。
> 起源：SLICE-001 完成後，對 SLICE-002 即將引入的 auth 機制如何處理產生疑慮。
> 結論：**這兩種依賴形態本質不同，不該用同一套機制處理。**

---

## 為什麼需要這份文件

SLICE-001 順利用 SEAM（虛線 Port）處理了「Account 模組尚未存在」的依賴。`AccountExistencePort` 是個漂亮的設計——它讓 SLICE-001 可以獨立完成而不依賴未存在的模組。

但這個成功有個隱含條件：**`AccountExistencePort` 的依賴對象（Account 模組）是另一個業務模組**。SEAM 機制的設計初衷正是處理「業務模組之間的接縫」。

進入 SLICE-002 時，會碰到 auth 機制（admin 身份驗證）。如果天真地套用 SLICE-001 的成功模式，會產出 `AdminAuthPort` 之類的虛線節點。**這是個陷阱**——auth 不是業務模組，它是橫切片功能（cross-cutting concern）。把橫切片硬塞進 SEAM 框架會產生過度抽象。

這份文件明確切開兩者，避免這個陷阱在 SLICE-002 發生。

---

## 兩種依賴形態的本質差異

### 業務 SEAM

**例子**：`AccountExistencePort`、`PaymentGatewayPort`、`InventoryPort`

**特徵**：

- 在業務流程的明確一步觸發（譬如「檢查帳號存在性」這個動作）
- 未來實作是另一個**業務模組**（Account module、Payment module）
- 觸發點是業務語意上的決策點，不是技術上的橫切
- 通常一個 Port 對應一個方向明確的業務查詢/命令

**設計意圖**：

把業務模組之間的耦合延遲到必要時才處理。SLICE 可以獨立完成，靠 stub 跑通；未來模組到位時把 stub 換成真實實作。

### 橫切片功能（Cross-Cutting Concern）

**例子**：Authentication、Authorization、Logging、Transaction、Cache、Metrics、Tracing、Audit Log、Rate Limiting

**特徵**：

- 影響多個業務流程，不是某一步的事
- 通常在「進入/離開某層」時被觸發（譬如進 Controller 之前驗 token、進 Service 時開 transaction）
- 未來實作不是另一個業務模組，而是**框架機制**：Spring Security Filter、AOP Aspect、Spring 的 `@Transactional`、Micrometer 等
- 在代碼層面通常以**裝飾器/攔截器/Filter 鏈**形式出現，不是 Service 之間的呼叫

**設計意圖**：

讓業務代碼不需要知道這些技術關注。Controller 不該關心「我需不需要驗 token」——這是 SecurityFilterChain 的事，不是 Controller 的責任。

### 對比表

| 維度 | 業務 SEAM | 橫切片功能 |
|------|----------|----------|
| 例子 | AccountExistencePort | Auth, Logging, Transaction, Cache |
| 在業務流程中的位置 | 明確的某一步 | 跨越多個進入點 |
| 未來實作 | 另一個業務模組 | 框架的 Filter / AOP / Aspect |
| 業務代碼是否該知道它 | 是（透過 Port 介面） | 否（透明處理） |
| 是否該畫成 D2 節點 | **是**（用 stroke-dash 虛線標記） | **否**（屬於整層的橫向屬性） |
| 對應的設計模式 | Hexagonal Port、Dependency Inversion | Decorator、Interceptor、Filter |
| 違反時的徵兆 | 業務功能無法獨立完成 | 業務代碼充斥技術關注 |

---

## 處理方式規範

### 業務 SEAM 的處理（已有經驗）

走 SLICE-001 已驗證的流程：

1. 識別業務流程中需要的外部能力（譬如「查 Account 存在性」）
2. 在 Application Layer 定義 Port interface
3. D2 上畫成虛線框（`style.stroke-dash: 5`），label 標 `→ future X module`
4. SLICE 內提供 stub 實作（fail-fast 或回傳預設值）
5. DECISIONS.md 記錄 Port 的引入動機與替換條件
6. 未來業務模組到位時，stub 換成真實 Adapter

### 橫切片功能的處理

**不該畫進 SLICE 的業務 D2**。改用以下機制：

#### 機制 1：在 Layer Group 標記橫向屬性

D2 的 layer group label 後加 `[ ]` 標註該層的橫向屬性：

```d2
presentation: {
  label: "Presentation Layer\n[Auth: SecurityFilterChain - configured in SLICE-002]"
  ...
}

application: {
  label: "Application Layer\n[Tx: @Transactional, Audit: AOP @Auditable]"
  ...
}
```

`[ ]` 中的內容代表「這層整體有這些橫切機制」，不是節點。視覺上能看到，但不會被誤讀成業務 Port。

#### 機制 2：獨立的 CROSS-CUTTING.md 列表

維護一份 SOT，列出本系統所有橫切片功能、各自的實作機制、影響範圍、引入時機。每進一個 SLICE 時 review 這份文件，確認 SLICE 對橫切片有沒有新需求或變更。

範例骨架：

```markdown
# Cross-Cutting Concerns Inventory

## CC-001 Authentication
- 機制：Spring Security SecurityFilterChain + JWT
- 適用範圍：所有 `*Controller`，例外白名單由 SecurityConfig 管理
- 引入 SLICE：SLICE-002
- 例外端點：POST /registrations（SLICE-001 標 no auth）
- 對應規範：[連結到 Spring Security config 檔]

## CC-002 Transaction
- 機制：Spring `@Transactional`
- 預設：Application Service 方法層級
- ...

## CC-003 Logging
- ...
```

#### 機制 3：橫切片不進業務 D2，但可以有自己的圖

如果某個橫切片夠複雜（譬如 auth 流程），可以**獨立畫一張 D2 圖**標題寫 `cross-cutting-auth.d2`，跟 SLICE 的業務 D2 平行存在。

關鍵：**不要把橫切片塞進 SLICE 業務 D2**。視覺與職責要分。

---

## SLICE-002 的具體應用

SLICE-002（管理員審核申請）會涉及三類依賴，分別處理：

### 1. 業務 SEAM（用 Port 處理）

可預期會出現的 Port：

- `RegistrationApplicationLifecyclePort` 或類似——SLICE-001 的寫入路徑與 SLICE-002 的狀態變更會共用同一張表，可能需要抽出生命週期管理 Port
- `NotificationPort`（如果審核結果要通知）——通知是另一個業務責任
- `AccountCreationPort`（如果 APPROVED 後要建 Account）——SLICE-001 的 `AccountExistencePort` 此時可能升級或補充

這三個都用 SEAM 處理，畫進 SLICE-002 的 D2，標虛線。

### 2. 橫切片功能（不用 Port 處理）

- **Auth（admin 身份驗證）**：用 SecurityFilterChain + `@PreAuthorize("hasRole('ADMIN')")` 標註，不畫 Port
- **Audit Log**（審核行為要留痕）：用 AOP @Auditable 標註或 Spring Events
- **Transaction**：`@Transactional` 標註

這些都不進 SLICE-002 的業務 D2，改進 layer group 標籤或 CROSS-CUTTING.md。

### 3. SLICE-001 留下的 SEAM 第一次需要被填實

- `AccountExistencePort`：如果 SLICE-002 完成 APPROVED 後要觸發 Account 建立，這個 Port 第一次有真實情境需要它運作。是「stub → 真實實作」轉換的時機。

這個轉換本身應該是個獨立的決策，記錄在 DECISIONS.md。

---

## 失敗模式辨識

進入 SLICE-002 時，如果你發現自己在做以下事情，停下來重看這份文件：

| 徵兆 | 可能的問題 |
|------|----------|
| 在 D2 上畫 `AdminAuthPort` 之類的虛線節點 | 把橫切片誤當業務 SEAM |
| Application Service 注入一個 `AuthService` 來問「使用者是不是 admin」 | Service 不該知道 auth |
| Controller 充斥 `if (user.isAdmin())` 之類的判斷 | 該交給 SecurityFilterChain |
| Port interface 命名包含 `Auth`、`Permission`、`Tx`、`Log` 字眼 | 這些通常是橫切片，不該用 Port |
| 為了測試業務邏輯需要 mock `AuthPort` | 訊號：auth 滲透到業務層了 |

---

## 與 Cell Contract 框架的對應

對齊到底層方法論詞彙：

- **業務 SEAM** ≈ Cell Contract L2 (器官) / L3 (細胞契約) 的邊界
  - 是不同細胞之間的接縫
  - 處理結構性依賴
- **橫切片功能** ≈ Cell Contract L4 (維度) 的範疇
  - 不是某個細胞的責任，是貫穿所有細胞的維度
  - 處理時序、生命週期、橫向能力

兩者正交，分別由不同機制處理：

| Cell Contract 層 | 處理機制 |
|------------------|----------|
| L1 DNA | Foundation Build / 系統意圖 |
| L2 器官 | Modulith 模組邊界 |
| L3 細胞契約 | 業務 SEAM (Port) + ArchUnit 規則 |
| L4 維度 | 橫切片機制（Filter / AOP）+ INV-### 行為不變式 |

---

## 規範條款（給未來 SLICE 用）

進入任何新 SLICE 的 Phase 2（D2 協商）前，先檢查：

1. **這個 SLICE 涉及的依賴清單**：列出每個 SLICE 內外的依賴
2. **每個依賴分類**：是業務 SEAM 還是橫切片？用上面的對比表判斷
3. **業務 SEAM 走 SLICE-001 流程**：畫進 D2、用虛線、標 future module
4. **橫切片走本文件機制**：layer group 標籤 + CROSS-CUTTING.md 條目
5. **混合情境**：少數情況一個依賴可能兼具兩面（譬如 Account 模組的查詢是業務，但其底下用了 Cache 是橫切片）。這時兩條路都走：業務面用 Port，橫切片面進 CROSS-CUTTING.md。

---

## 反證條件

這份規範的有效性建立在幾個假設上。如果以下任一為真，需要重審：

- 出現一個依賴**既不是業務 SEAM 也不是橫切片**，無法歸類
- Spring Security 升級或被替換為其他 auth 機制，且新機制不適合 Filter 模式
- 系統規模成長到「橫切片功能本身需要被當業務模組管理」（譬如多租戶 auth 需要查 DB 才能判斷）——此時 auth 可能升級成業務 SEAM
- 某個橫切片功能實際上影響了業務語意（譬如 audit log 不只記錄、還影響業務流程）——需要重審它是否真的是橫切片

---

## 開放問題（給後續討論）

以下是這份文件**沒有回答**的問題，暫存於此：

- **多租戶情境下的 auth 算什麼？**
  如果系統有多租戶，「使用者屬於哪個租戶」是業務問題還是橫切片？這個區分變模糊。
- **Observability 算橫切片還是別的？**
  Metrics、Tracing、Logging 通常算橫切片，但 Joe 的方法論裡有「closed loop」概念，runtime trace 反向回到 graph 驗證——這時 observability 不只是裝飾器，有業務語意成分。需要單獨討論。
- **Saga / 分散事務怎麼歸類？**
  跨模組的 Saga 既有業務語意（補償邏輯）又有技術機制（協調器）。可能需要第三類處理方式，不是 SEAM 也不是橫切片。

這些問題不在 SLICE-002 的範圍內，記錄在這裡留待未來。
