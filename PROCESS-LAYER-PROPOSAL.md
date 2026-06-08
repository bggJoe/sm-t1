# 流程層方法論補強提案

> 這份文件是討論材料，不是執行清單。
> 主題：如何在不引入新工具的前提下，把「業務活動流程」提升為一級工件，
> 跟 D2/DSL 在結構層級同等強。
> 預期使用時機：SLICE-002 啟動時，不要在 SLICE-001 上回頭加。

---

## 給 Claude Code 的工作守則

讀這份文件時：

1. **這份是討論材料**，不是執行清單。讀完後給評估摘要，不要直接動工件檔案。
2. **核心紀律**：本提案的所有工件「**只在真實需要時建立**」。SLICE-001 不需要、SLICE-002 才需要。預先建 = 為 LLM 演儀式，不是真實需求。
3. **不同意要明說**。對任何提案項目若有保留，明確指出「不同意，理由是 X」。
4. **觸發 Deceiver 自檢時機**：你產出的回應若看起來「方案完整、邏輯閉環」，停下來問每個結論是否有反證條件。
5. **完成評估後產出兩件事**：(a) 對提案的同意/保留意見，(b) SLICE-002 啟動時的具體建議步驟。

---

## 問題定位

目前方法論工件處理的層級：

| 層級 | 工件 | 答的問題 |
|------|------|---------|
| 結構 | D2 / Structurizr DSL | 誰存在、誰依賴誰 |
| 規則 | INV-### | 什麼不能違反（禁止式） |
| 端到端能力 | AC-### | 使用者能完成什麼（肯定式） |
| **流程** | **缺** | **多步驟順序、條件分支、狀態轉換** |

**SLICE-001 沒爆出這個缺口**，因為它是線性流程（送 form → 寫 DB），AC 的 Given/When/Then 就夠用。

**SLICE-002 會爆出來**：管理員審核流程涉及狀態機、條件分支、跨 SLICE 觸發：

```
PENDING → (admin 審核) → APPROVED 或 REJECTED
APPROVED → (觸發) → 建立 Account
REJECTED → (允許) → 同 email 重新申請
```

這層用 AC 寫得出來，但會散成 N 條獨立 AC，**整體流程的拓撲被打散，人看不出全貌、LLM 推不出時序**。

---

## 排除的錯誤路徑（先講不要做什麼）

幾個看起來能解、實際會把方法論搞重的選項：

### 不要走：BPMN / Activity Diagram

- BPMN 有自己的執行引擎（Camunda、Flowable），引入 = 引入新依賴
- BPMN XML 對 LLM 不友善
- Activity Diagram 是 UML 子圖，跟 D2 不協同
- **這些是給 BPM 引擎執行的**，你的執行載體是 Spring 程式碼

### 不要走：Cucumber + .feature 檔

- 引入 Cucumber 是引入第三個測試框架（已有 JUnit + ArchUnit）
- `.feature` 檔變成第三層工件，AC 會被分成 markdown 條款與 Gherkin 兩處，同步成本高
- AC 的 Given/When/Then 已經是 Gherkin 風格，差別只在沒接 Cucumber——**沒必要為了接 Cucumber 引入整套框架**

### 不要走：Sequence Diagram 作為主工件

- D2 / Mermaid 都支援 sequence，但與 component 圖共存會讓人不知道哪張是 SOT
- 時序圖容易畫得比實際代碼更詳細，變成過度設計源頭
- **適合補強複雜流程，不適合主要載體**

### 不要走：Spring Statemachine 框架

- 對你目前規模過重
- 引入框架本身會擴大方法論邊界
- enum + 手寫轉換邏輯 + 測試已足夠

---

## 正確路徑：用 Spring/Java 既有抽象作為一級工件

關鍵洞察：**Spring Boot 3 + Modulith + JPA 已經有處理流程的合理抽象**，問題只是方法論還沒把它們提到一級。

三件事，按重要性排序：

---

### 主工件 1：State Machine（狀態機定義）

**為什麼是它**：SLICE-001 已有 `ApplicationStatus.PENDING`，SLICE-002 會加 `APPROVED` / `REJECTED`。**這個 enum 與其轉換規則就是流程的本體**。把它從「程式碼裡的 enum」提升到「方法論一級工件」，不需要新工具。

**檔案位置**：每個有狀態流的業務實體一個 `STATES-<entity>.md`。

**範例（給 RegistrationApplication 用，SLICE-002 啟動時建）**：

```markdown
# RegistrationApplication 狀態機

## 狀態定義

| 狀態 | 語意 | 進入條件 |
|------|------|---------|
| PENDING | 等待管理員審核 | 訪客送出有效申請 |
| APPROVED | 已通過審核，可建立 Account | 管理員審核通過 |
| REJECTED | 已被拒絕，使用者可重新申請 | 管理員審核拒絕 |

## 合法轉換

| 從 | 到 | 觸發 | 操作者 | 對應 SLICE | 對應測試 |
|----|----|------|-------|-----------|---------|
| (none) | PENDING | 訪客提交申請 | 訪客 | SLICE-001 | `inv002_newApplicationMustBePending` |
| PENDING | APPROVED | 管理員批准 | admin | SLICE-002 | `ac002_adminApprovesPendingApplication` (TODO) |
| PENDING | REJECTED | 管理員拒絕 | admin | SLICE-002 | `ac003_adminRejectsPendingApplication` (TODO) |

## 禁止轉換

| 從 | 到 | 為什麼禁止 | 守衛 |
|----|----|-----------|------|
| APPROVED | (任何) | 已通過審核不可再變動 | INV-### (待寫) |
| REJECTED | PENDING | REJECTED 後重新申請是建立新紀錄，不是狀態回滾 | INV-### (待寫) |
| (none) | APPROVED 或 REJECTED | 必須先 PENDING | INV-002 |

## 流程圖（D2，給人看的視覺投影）

`states-registration-application.d2`

## 對應 SLICE

- SLICE-001：建立 (none) → PENDING
- SLICE-002：管理員審核 PENDING → APPROVED/REJECTED
- SLICE-003 (預期)：APPROVED → 觸發 Account 建立
```

**搭配的視覺投影 `states-registration-application.d2`**：

```d2
direction: right

start: ●
pending: PENDING
approved: APPROVED
rejected: REJECTED
end_approved: ◉ {style.fill: "#90EE90"}
end_rejected: ◉ {style.fill: "#FFB6C1"}

start -> pending: "訪客提交 (SLICE-001)"
pending -> approved: "admin 批准 (SLICE-002)"
pending -> rejected: "admin 拒絕 (SLICE-002)"
approved -> end_approved: "建立 Account (SLICE-003)"
rejected -> end_rejected: "可重新申請\n(新紀錄, INV-001)"
```

**這張 D2 跟業務 SLICE D2 不同職責**：業務 SLICE D2 是 component 拓撲，狀態機 D2 是時序視角。**兩者並存，不互相取代**。

---

### 主工件 2：Domain Events（領域事件目錄）

**為什麼是它**：流程的另一面是「狀態變更後該通知誰」。Spring Modulith 對此有原生支援（`@DomainEvent` / `ApplicationEventPublisher`）——**不引入新工具，只是把現有機制當一級工件**。

**檔案位置**：專案根 `EVENTS-CATALOG.md`，每個 module 的 `events/` 目錄存實際 event class。

**範例（SLICE-002 啟動時建）**：

```markdown
# Events Catalog

## RegistrationApplicationSubmitted (SLICE-001 發布)

- **發布者**：`registration` module
- **觸發時機**：申請成功 INSERT 後
- **承載資料**：applicationId, email, submittedAt
- **訂閱者**：(SLICE-001 暫無)、(SLICE-002 預期：notification module 寄確認信)

## RegistrationApplicationApproved (SLICE-002 預期發布)

- **發布者**：`registration` module
- **觸發時機**：管理員批准且狀態成功變為 APPROVED
- **承載資料**：applicationId, email, approvedAt, approvedBy
- **訂閱者**：(SLICE-003 預期：identity module 建立 Account)

## RegistrationApplicationRejected (SLICE-002 預期發布)

- **發布者**：`registration` module
- **觸發時機**：管理員拒絕且狀態成功變為 REJECTED
- **承載資料**：applicationId, email, rejectedAt, rejectedBy, reason
- **訂閱者**：(預期：notification module 寄拒絕信)
```

**這份檔案的關鍵價值**：把「跨模組通訊」從隱性變顯性。LLM 在 SLICE-002 開發時讀這份，就知道 `RegistrationApplicationApproved` event 將被 SLICE-003 訂閱——**它不會擅自加同步呼叫繞過 event**，因為 event 已被列入契約。

---

### 補強工件（不主推但留路）：Sequence Diagram

**只在以下情境用**：

- 流程涉及 ≥3 個節點協作且**非線性**（有條件分支或補償邏輯）
- 流程跨 ≥2 個 module
- 涉及非同步事件回流

**不該畫的場景**：

- 線性流程（譬如 SLICE-001 那種）— AC Then 子句已足夠
- 純狀態變更動作本身 — 狀態機 D2 已足夠

**載體**：D2 sequence variant 或 Mermaid sequence。**重點：sequence 圖是補強，不是主工件**。預設一個 SLICE 沒有 sequence 圖，需要時才加。

---

## 流程的機器可驗證機制

跟 INV / AC / ArchUnit 一樣，流程定義也要有對應驗證機制。**不引入狀態機框架，用既有 JUnit + ArchUnit**。

### 機制 1：ArchUnit 守狀態變更入口

```java
@Test
void onlyApplicationServiceCanChangeApplicationStatus() {
    ArchRule rule = noClasses()
        .that().resideOutsideOfPackage("..registration.application..")
        .should().callMethodWhere(
            target -> target.getName().equals("setStatus") 
                  && target.getOwner().isAssignableTo(RegistrationApplication.class)
        )
        .because("狀態變更必須透過 Application Service 統一處理，不可直接修改");
    rule.check(classes);
}
```

**這條規則守住**：狀態變更不能繞過業務服務直接改。

### 機制 2：State Transition Test（新測試類別）

放在 `*StateTransitionTest.java`，介於 INV 與 AC 之間：

```java
class RegistrationApplicationStateTransitionTest {
    
    /** 狀態機：(none) → PENDING 是合法初始轉換 */
    @Test
    void initialTransitionToPendingIsLegal() {
        var app = new RegistrationApplication("user@example.com", "Joe");
        assertEquals(ApplicationStatus.PENDING, app.getStatus());
    }
    
    /** 狀態機：APPROVED 為終態，不可再轉換 */
    @Test
    void approvedIsFinalState() {
        var app = approvedApplication();
        assertThrows(IllegalStateTransitionException.class, () -> app.reject("reason"));
        assertThrows(IllegalStateTransitionException.class, () -> app.approve("admin"));
    }
}
```

**新類別跟 INV / AC 的分工**：

| 類別 | 答的問題 | 對應工件 |
|------|---------|---------|
| INV | 業務規則被守住嗎？ | INV-### in SLICE.md |
| **State Transition** | **狀態轉換合法嗎？** | **STATES-<entity>.md** |
| AC | 使用者能完成嗎？ | AC-### in SLICE.md |

### 機制 3：Modulith Event Publication Test

Spring Modulith 1.4+ 內建 event publication registry。**這是現成機制，不需要自己造**：

```java
@Test
void approvedEventIsPublishedOnApproval(@Autowired PublishedEvents events) {
    // when
    service.approve(applicationId, "admin");
    
    // then
    assertThat(events.ofType(RegistrationApplicationApproved.class))
        .hasSize(1)
        .first()
        .satisfies(e -> assertThat(e.applicationId()).isEqualTo(applicationId));
}
```

**這把 EVENTS-CATALOG.md 的承諾變成可驗證**——文件說會發 `RegistrationApplicationApproved`，測試用 PublishedEvents 驗證真的發了。

---

## 整合到既有方法論

加入流程層後的工件層級：

```
業務需求
   ↓
SLICE-XXX.md
   ├─ 三句話定義
   ├─ INV-###
   └─ AC-###
   ↓
slice-xxx.d2 (拓撲)
STATES-<entity>.md (狀態機，跨 SLICE)        ← 新增
EVENTS-CATALOG.md (跨模組通訊，跨 SLICE)     ← 新增
   ↓
代碼 + 測試
   ├─ INV unit test
   ├─ AC integration test  
   ├─ State Transition test                ← 新增
   └─ Event Publication test (Modulith)    ← 新增
```

**關鍵設計**：狀態機與事件目錄**跨 SLICE 共用**，不屬於任何單一 SLICE。它們是 entity 級或 module 級的工件——這呼應 Cell Contract「細胞契約」概念。

---

## 跟既有方法論的勾稽

| 既有概念 | 對應到流程層 |
|---------|------------|
| Cell Contract L4 維度 (時序、生命週期) | **狀態機就是 L4 的具體形式** |
| AccountExistencePort SEAM | Event 是另一種 SEAM——非同步版本 |
| INV-### 反證方式 | 狀態機的「禁止轉換」表 |
| Foundation Build | STATES + EVENTS 共用工件，是 Foundation 的具體成分 |

**最關鍵的對應**：方法論之前承認 L4 維度是 ArchUnit 抓不到的盲區——**狀態機 + State Transition Test 正是補這個盲區**。

---

## 適用性檢查（克制條款）

**SLICE-001 需要狀態機嗎？**
不需要。SLICE-001 只有 (none) → PENDING 一條轉換，獨立檔案是過度設計。

**什麼時候建 STATES-registration-application.md？**
SLICE-002 開始引入 PENDING → APPROVED/REJECTED 時。**有第二條轉換才有狀態機的價值**。

**什麼時候建 EVENTS-CATALOG.md？**
SLICE-002 引入第一個跨模組事件時。**有第一個事件才有目錄的意義**。

**SLICE-002 之前要先建這些嗎？**
不要。**先讓 SLICE-002 設計時自然引出需求，再建工件**。預先建是儀式不是需求。

---

## SLICE-002 啟動時的具體執行順序

1. **Phase 2 D2 協商前**：寫 `STATES-registration-application.md` 草稿（先列已知狀態與轉換）
2. **D2 協商過程**：讓狀態機定義引導業務 D2 圖（哪些 component 負責哪些轉換）
3. **D2 協商完成後**：識別需要的 events，寫進 `EVENTS-CATALOG.md`
4. **Phase 3 凍結**：補上三層驗證機制
   - ArchUnit 守狀態變更入口
   - State Transition Test 守轉換合法性
   - Modulith Event Test 守事件發布
5. **SLICE-002 完成 Retrospect**：把流程層工件寫進 ARCHITECTURE-LAYERS.md，正式納入方法論詞彙

**方法論詞彙更新（在 Retrospect 後）**：

```markdown
## 流程層工件（跨 SLICE 共用）

- **STATES-<entity>.md**：狀態機定義，含合法/禁止轉換、對應 SLICE、對應測試
- **EVENTS-CATALOG.md**：領域事件目錄，含發布者、訂閱者、承載資料
- **State Transition Test**：守轉換合法性
- **Event Publication Test**：守事件發布契約
```

---

## 對抗性檢查（自我警覺）

**狀態機跟 INV 會不會衝突？**
不會，是互補的。INV 守單一業務規則（譬如「新建必須 PENDING」是 INV-002），狀態機守整個生命週期的轉換合法性。**INV 是點，狀態機是線**。

**Event Catalog 跟 D2 會不會職責重複？**
有部分重複，但角度不同。D2 表達「呼叫關係」（同步），Event Catalog 表達「訂閱關係」（非同步）。**這個區分對 Modulith 很關鍵**——同步呼叫違反模組邊界，非同步事件是模組間通訊的合法管道。

**會不會把方法論搞重？**
**只在真實需要時建立**這個原則是關鍵。判準是被使用觸發，跟 Foundation Build 的核心立場一致。

**Spring Statemachine 框架要不要引入？**
**不要**。對目前規模過重。enum + 手寫轉換邏輯 + State Transition Test 已經夠用。**未來若狀態 >10 或轉換 >30 才考慮**。

---

## 給 Claude Code 的回報格式

完成評估後，給我一份摘要：

```markdown
## 對提案的評估

### 主工件 1：State Machine
- 同意度：[完全同意/部分同意/不同意]
- 保留意見：（如有）

### 主工件 2：Events Catalog  
- 同意度：[...]
- 保留意見：（如有）

### 補強工件：Sequence Diagram
- 同意度：[...]
- 保留意見：（如有）

### 三層驗證機制
- ArchUnit 規則：[...]
- State Transition Test：[...]
- Modulith Event Test：[...]

## SLICE-002 啟動時的具體建議步驟
（按本文件「執行順序」評估，給出你建議的調整或補充）

## 我發現的額外問題
（如有，明確列出。如無，明確寫「無」。不要為了顯得周到而虛構問題。）
```

---

## 變更規則

修改本文件時：
1. 本文件本身在 SLICE-002 完成 Retrospect 後可丟棄——它的價值是觸發流程層成形，不是長期維護
2. 結論回寫進 ARCHITECTURE-LAYERS.md 等 SOT 文件
3. 若 SLICE-003 又遇到本文件未涵蓋的流程張力，建立新的對應分析文件
