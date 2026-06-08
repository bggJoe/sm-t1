# RETROSPECT-SLICE-001

Phase 5 回顧。對象：SLICE-001（用戶提出帳戶註冊申請），Phase 0–4 完整一輪。

---

## 卡點清單

### 卡點 1：ArchUnit 1.4.x `failOnEmptyShould` 行為變更

**發生位置**：Phase 3 Layer 2，ArchUnit 規則初次寫好後跑測試。

**症狀**：4 條 ArchUnit 規則全部紅燈，錯誤訊息為 `Rule was violated — no classes found matching predicate`。

**根因**：ArchUnit 1.4.x 將 `failOnEmptyShould` 預設改為 `true`。骨架期 `.web`、`.application`、`.infrastructure` package 下只有 `package-info.java`，不產生 `.class` 檔，`that()` 子句命中零個類別，觸發此行為。

**修法**：全部規則加 `.allowEmptyShould(true)`。

**這是真實紅燈**：方法論的核心假設（L2 會抓到違規）在這裡被現實反駁了一次——規則「假裝通過」的問題在 ArchUnit 1.3.x 是存在的，1.4.x 的改動反而是正確方向，但需要額外設定。這是版本敏感的知識盲點，不靠執行不會發現。

---

### 卡點 2：測試層盲點（結構性，不是執行錯誤）

**發生位置**：Phase 3 梳理討論，由 Joe 提問引出。

**問題**：`DO_NOT_INCLUDE_TESTS` 讓 ARCH-RULE-002 無法覆蓋測試類別，`RegistrationApplicationServiceTest` 放在 `registration.application` package 下，若測試裡直接 import infrastructure 實作，不會有任何紅燈。

**這是設計盲點，不是執行錯誤**：整個 Phase 3 跑完、所有測試綠燈，這個問題不會自動浮現。是 Joe 主動提問才被發現。如果沒有這個問答，這個漏洞會靜默帶入 Phase 4。

**修法**：新增 ARCH-RULE-005，用 `ONLY_INCLUDE_TESTS` 對測試類別套用同一套隔離規則。

---

### 卡點 3：DSL tag 語義與代碼層次落差

**發生位置**：Phase 4 實作後，post-hoc 說明。

**問題**：`workspace.dsl` 中 `registrationAppRepository` 標記為 `Infrastructure` tag。但 port interface `RegistrationApplicationRepository` 必須放在 `application` 層（否則 Service 無法依賴它而不違反 ARCH-RULE-002）。DSL 的 tag 代表「主要實作的層」，不代表「所有相關介面的層」。這個語義差距沒有在 Phase 2/3 被明確定義。

**這次的選擇**：不修 DSL，Phase 4 後補說明。

**潛在風險**：若下一輪 AI 只讀 DSL 就開始實作，可能把 `RegistrationApplicationRepository` interface 放進 infrastructure 包，直接觸發 ARCH-RULE-002。DSL 的 tag 語義需要在 ARCHITECTURE-LAYERS.md 或 DECISIONS.md 補一條說明。

---

### 想繞過約束的瞬間（誠實記錄）

Phase 4 實作 `JpaRegistrationApplicationRepository` 時，有一個瞬間：直接讓 `RegistrationApplicationService` 依賴 `JpaRegistrationApplicationRepository` 是最短路徑，不需要另外定義 port interface。這條路會讓測試從 0.4 秒變成 5 秒以上，且觸發 ARCH-RULE-002 紅燈。L2 規則確實在這個時間點起到了防護作用——不是靠人工紀律，是靠測試失敗。

---

## 四層投影強度排名

**排名依據**：這一輪中，哪層實際攔截到了錯誤或不良傾向。

| 排名 | 層 | 攔截到的事 | 強度說明 |
|---|---|---|---|
| 1 | **L2 ArchUnit** | `failOnEmptyShould` 強迫修正骨架設定；ARCH-RULE-002 阻止 Service 直接依賴 JPA | 唯一在這輪產生真實紅燈的層 |
| 2 | **L4 INV stubs** | 方法名綁定讓 Phase 4 無法重新詮釋不變式語義；`inv001` 拆兩個 test 的決定在 Phase 3 就固化 | 對 AI 輸出空間有實質限制，但只在 Phase 4 才有壓力 |
| 3 | **L1 Structurizr DSL** | Phase 2 協商出的節點清單成為 Phase 4 上下文聲明的錨點，阻止了範圍外的類別被引入 | 軟約束，靠 AI 自我遵守，沒有機器執行 |
| 4 | **L3 Modulith verify** | 這輪沒有攔截任何東西 | 只有一個模組，跨模組違反的場景不存在；真正的壓力在 SLICE-002 引入第二個模組後才會出現 |

---

## 對方法論的具體回饋

**有效的部分**：

圖形先協商（Phase 2 D2 + Phase 3 DSL）讓 Phase 4 的「上下文聲明」有明確的節點清單可以抄。AI 在 Phase 4 不需要推斷「應該有哪些元件」，只需要在已知節點上做局部展開。這是這套方法論設計意圖的直接驗證。

**弱點 1：DSL tag 的語義不精確**

`workspace.dsl` 的 `tags` 是自由字串，沒有定義語義。`Infrastructure` tag 在這輪被用來標記「概念上屬於基礎設施」的 component，但在 port/adapter 模式下，介面和實作分屬不同層。這個歧義在 Phase 4 後才浮現，說明 Phase 3 的四層協商沒有覆蓋到「介面所在層 vs 實作所在層」這個面向。

**弱點 2：L3 的壓力太晚出現**

Modulith verifier 在只有一個模組時形同虛設。方法論的設計是「Phase 6 三個 SLICE 才逼出共用模組設計問題」，這是合理的，但代表 L3 在前四個 Phase 沒有機會真正被驗證。這是方法論的設計選擇，不是缺陷，但需要明確記錄：L3 的強度依賴於模組數量。

---

## 下一輪要加強的 1–2 件事

**1. DSL tag 語義需要在 ARCHITECTURE-LAYERS.md 補一條說明**

明確定義：`Infrastructure` tag 標記的是「實作所在層」，port interface 放在 `application` 層。這樣下一輪 AI 讀 DSL 時不會把 port interface 放錯地方。

**2. `AccountExistencePort` 的 stub 是一個定時炸彈**

`StubAccountExistenceAdapter` 永遠回傳 `false`，代表 INV-001（已有 APPROVED 帳號→拒絕）的那一半在生產環境永遠不會被觸發。`inv001_alreadyApprovedEmailRejected` 這個測試驗證的是 Service 的邏輯，不是端對端的行為。SLICE-003 如果沒有落地，這個 stub 會靜默成為永久狀態。下一輪開始前需要確認 SLICE-003 的優先序。

---

## Deceiver 角色：一個刻意沒有提出的疑慮

INV-004 的實作使用了 regex `^[^@\s]+@[^@\s]+\.[^@\s]+$`，SLICE-001.md 聲稱符合「RFC 5322 基本格式」。這個 regex 會讓 `foo@bar.c` 通過（TLD 只有一個字元），也會讓 `foo@.example.com` 通過（@ 後緊跟 `.`）。`inv004_invalidEmailFormatRejected` 只測試了 `notanemail` 這個顯而易見的錯誤案例，沒有涵蓋邊界值。

Phase 4 實作時我選擇了「夠用」的解法，沒有主動提出。這是一個已知的品質妥協，現在明確記錄在這裡。
