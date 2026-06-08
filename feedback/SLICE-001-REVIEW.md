# SLICE-001 弱點評估與修正請求

> 這份文件是給 Claude Code 用的修正請求清單。
> 對 SLICE-001 工件群（slice-001.d2、SLICE-001.md、ARCHITECTURE-LAYERS.md、DECISIONS.md）做評估與修正。
> 每項都有「問題描述、為什麼是問題、建議修法」三欄。

---

## 給 Claude Code 的工作守則

讀這份文件時：

1. **不要全盤照辦**。我列出的是觀察到的弱點，每項都是候選修法，不是命令。對每一項，請你獨立評估：(a) 是否同意這是問題，(b) 修法是否可行，(c) 有沒有更好的修法。
2. **對不同意的項目**，明確說「不同意，理由是 X」。我不要看到「我已修正所有項目」這種掃式回應。
3. **修改前先讓我看修法計畫**，不要直接動檔案。我確認後再執行。
4. **跨檔修改要保持一致性**。譬如修了 INV-001 的精度，SLICE-001.md 的「成功判準」也要相應調整。
5. **每筆實際修改完成後追加一筆 DECISIONS.md 條目**，記錄變更前後與理由。
6. **觸發 Deceiver 自檢**：你產出的回應若看起來「結構整齊、句句結論」，停下來問每個斷言能否被反證。

---

## 弱點清單

### ITEM-01 [嚴重] Domain Layer 在 D2 上消失，業務規則無處安放

**問題描述**

ARCHITECTURE-LAYERS.md 定義四層（Presentation / Application / Domain / Infrastructure），但 slice-001.d2 只畫了三層分組，Domain 不在圖上。文件解釋「SLICE-001 暫無純 Domain 物件，RegistrationApplication 作為 JPA Entity 存在 Infrastructure 邊界」。

**為什麼是問題**

INV-002 規定「申請建立時狀態必須為 PENDING」，這是業務規則。按 ARCHITECTURE-LAYERS.md 自己的定義，業務規則屬於 Domain Layer。但 Domain 層在 SLICE-001 不存在，所以這條規則的守衛邏輯只能：

- 放在 JPA Entity 上 → 業務規則跑到 Infrastructure 層，違反 ARCHITECTURE-LAYERS.md 的依賴規則
- 放在 Application Service 上 → Service 變成業務邏輯的家，Domain 永遠長不出來

「Domain 層暫時為空」這個事實在 D2 上不可見，未來新人或顧問看圖會以為這是三層架構，不知道 Domain 是「故意空」的。

**建議修法（候選）**

- (A) 在 slice-001.d2 畫一個空的 Domain group，標籤寫 `Empty for SLICE-001 — invariants delegated to Application Service`，視覺顯式化「故意空」
- (B) 把 RegistrationApplication 抽成純 Domain POJO + JPA Entity 兩層（標準 DDD 做法）
- (C) 接受規則放 Application Service，但在 ARCHITECTURE-LAYERS.md 加一條「Application Service 在 Domain 缺席時可承接業務規則，視為 Domain 的暫居地」

我傾向 (A)：最便宜，把訊息顯式化但不動結構。請評估你的看法。

---

### ITEM-02 [嚴重] DECISIONS.md 編號破洞

**問題描述**

DECISIONS.md 有兩處格式錯誤：

1. **DEC-003 跳號**：從 002 直接跳到 004
2. **DEC-006 末尾接了一段沒有編號的決策**：關於 `nexusoss.wistron.com` SSL 憑證的條目，看起來是 DEC-007 但被併入 DEC-006

**為什麼是問題**

DECISIONS.md 是方法論的 traceability 核心。編號錯亂會讓後續引用（譬如「請參考 DEC-006」）出現歧義。雖然只是格式錯誤不是內容錯誤，但**正因為小才該立刻修**——這類錯誤累積到後期會變成審計噩夢。

**建議修法**

1. 確認 DEC-003 的真實狀態：是被刪除、還是漏編號？
   - 如果被刪除：補一個 tombstone 條目 `DEC-003：[deleted - 原因]`
   - 如果漏編號：把後續編號往前移，或補一個說明
2. 把 DEC-006 末尾的 SSL 條目獨立成 DEC-007（或當前可用的下一個編號）

請先回報這兩個錯誤的真實狀態，再決定具體修法。

---

### ITEM-03 [中] INV-### 缺少對應測試的 traceability

**問題描述**

SLICE-001.md 中四條 INV 都有「反證方式」，但沒有指明「這條 INV 對應哪個測試類/測試方法」。

**為什麼是問題**

進入 Phase 3 時，「這條 INV 是否有測試守住」會變成需要全文搜尋的問題。沒有顯式的 traceability，等於把對齊工作從文件移到人腦——這違反「凍結成可機器驗證的約束」的核心原則。

**建議修法**

每條 INV 加 `對應測試：` 欄位。即使是 TODO 也要有欄位骨架：

```markdown
- **INV-001**：...
  - 條件：...
  - 斷言：...
  - 反證方式：...
  - **對應測試**：`RegistrationApplicationServiceTest#duplicateEmailRejected` (TODO Phase 3)
```

四條 INV 都加上。先填 TODO 但欄位先建。

---

### ITEM-04 [中] DEC-001 與 copilot-instructions.md 偏離未明示

**問題描述**

`copilot-instructions.md` 指定 Angular 21，但 DEC-001 鎖定 Angular CLI 20（理由是機器已裝 20）。

**為什麼是問題**

這不是技術問題（CLI 20 對 SLICE-001 沒影響），是**文件權威來源衝突**。如果 copilot-instructions.md 是常駐約束，現在已經被 DEC-001 靜默覆寫——你的文件體系不該有這種無聲覆寫。

**建議修法（二擇一）**

- (A) 改 `copilot-instructions.md` 為 Angular CLI 20，與 DEC-001 對齊
- (B) DEC-001 加一句明確聲明：「**此決策臨時優先於 copilot-instructions.md 的 Angular 21 規定，待環境升級後重審**」

我傾向 (B)：保留升級的指針，不靜默覆寫。請評估。

---

### ITEM-05 [中] 「no auth」是戰略聲明，但視覺上太弱

**問題描述**

slice-001.d2 上 `no auth` 標在 Controller 的 label 末段和邊的描述上。

**為什麼是問題**

`no auth` 不是隨便的端點屬性，是**這個 SLICE 對 JWT 基礎建設的戰略性聲明**——「我不引入 auth」。新人看 D2 時會以為「就是公開端點」，不知道背後是「**SLICE-001 故意不引入 auth 基礎建設**」。

**建議修法**

兩件事一起做：

1. 在 Controller 節點加更明顯的視覺標記（例如改 fill color、加 emoji 標記如 `🔓 PUBLIC`、或加 stroke 顏色區分）
2. 在 SLICE-001.md 的「範圍邊界」明確列「不引入 auth 基礎建設（→ SLICE-002）」

注意：**不要把 auth 畫成 Port 節點**，這是橫切片功能不是業務 SEAM。詳見另一份文件 `CROSS-CUTTING-CONCERNS.md`。

---

### ITEM-06 [中] 成功判準與 INV-001 之間有精度落差

**問題描述**

SLICE-001.md「成功的判準」寫「相同 email 不可重複送出申請」。

INV-001 寫得更精確：「不能 PENDING + 不能 APPROVED + REJECTED 例外不擋」。

**為什麼是問題**

「相同 email 不可重複送出」會誤導：實際上 REJECTED 過的 email 是可以重新申請的，但成功判準看起來是「永遠不可重複」。這個精度落差會讓未來新人混淆。

**建議修法（二擇一）**

- (A) 把成功判準寫精確：「相同 email 在 PENDING 或 APPROVED 狀態下不可重複送出，REJECTED 後可重新申請」
- (B) 成功判準改寫為指向 INV：「申請的去重規則詳見 INV-001」

我傾向 (B)：避免成功判準與 INV 並存兩個版本。

---

### ITEM-07 [輕微] DEC-005 的時序假設懸空

**問題描述**

DEC-005 說 AccountExistencePort 等 Account 模組來時實作。但 SLICE-002 是「審核」不是「建立 Account」，所以 Account 模組的觸發時機不明。

**為什麼是問題**

如果 Account 模組三個月後才出現，AccountExistencePort 的 stub 實作會在 codebase 待三個月——這是該被察覺的風險。但目前 DEC-005 沒有觸發審視的條件。

**建議修法**

DEC-005 的反證條件加一條：「**若 Account 模組在 SLICE-003 完成後仍未出現，需重審 Port 是否變成永久結構，並評估是否需要把 stub 實作升級為過渡期的真實邏輯**」。

---

## 整體建議的優先順序

按建議的處理順序：

1. **ITEM-02**（DECISIONS.md 編號）：10 分鐘，文件衛生，立刻做
2. **ITEM-03**（INV traceability）：10 分鐘，加欄位骨架
3. **ITEM-01**（Domain Layer 視覺化）：15 分鐘，動 D2
4. **ITEM-05**（no auth 視覺化）：10 分鐘，動 D2 + SLICE-001.md
5. **ITEM-06**（成功判準精度）：5 分鐘，動 SLICE-001.md
6. **ITEM-04**（Angular 偏離）：5 分鐘，動 DEC-001 或 copilot-instructions.md
7. **ITEM-07**（DEC-005 時序）：5 分鐘，動 DEC-005

預期總時間 60 分鐘以內。如果你發現某項實際上比這個長，停下來問我。

---

## 完成後的回報格式

請在所有修正完成後，給我一份摘要：

```
## ITEM-01: [採納/不採納/部分採納] 採用方案 (X)
   實際修改：[列出動到的檔案與位置]
   DECISIONS.md 新增條目：DEC-XXX

## ITEM-02: ...
（每項一段）

## 不同意或修改的項目
（如有）

## 我發現的額外問題
（如修正過程中發現新問題）
```

最後一節「我發現的額外問題」是必填——如果沒發現，明確寫「無」。**不要為了顯得周到而虛構問題**，但發現了就要報。
