# LikeC4 設計慣例 — SM-T1 Sandbox

本文件記錄 `architecture.c4` 的設計決策、DSL 語法限制、與踩過的坑，供後續 AI session 產生相同風格的 LikeC4 DSL。

---

## 1. 目標與驗證清單

本 sandbox 要驗證三件事：

| # | 驗證項目 | 對應 view |
|---|---------|----------|
| 1 | `element kind` 直接對應模組概念，不翻譯成 C4 詞彙 | 全部 |
| 2 | 同一 model，兩種視角：C4-style（consultant）& 模組拓撲（開發）| `container_view` vs `module_topology` |
| 3 | Module Topology view 的 relationship auto-aggregation | `module_topology` |

---

## 2. Specification 設計

### 2.1 Element kinds

C4 主軸（巢狀對應 C4 Level）：

```
actor      → L1 人物（shape person）
system     → L1 系統
container  → L2 容器
module     → L3 模組（取代 C4 的 component，語意保真）
```

模組內部結構（只在細部 view 出現）：

```
usecase    → 跨模組公開 API（module root package）
port       → SEAM / Extension Point interface
adapter    → Port 的實作
controller → HTTP 入口點
service    → Application / Domain service（模組內部）
```

### 2.2 Relationship kinds

```
implements → Port ←實作─ Adapter；amber dashed
calls      → 一般呼叫；secondary
```

---

## 3. Model 結構規則

### 3.1 巢狀層次

```
model {
  actor / actor             ← L1 人物，放 model 頂層
  system {                  ← L1 系統
    container {             ← L2 容器
      module {              ← L3 模組
        usecase / port / adapter / controller / service
        // Internal relationships 在此定義
      }
      // Cross-module relationships 在 container 層定義
    }
  }
  // L1 / L2 relationships 在 model 頂層定義
}
```

### 3.2 Relationship 定義位置（重要限制）

**LikeC4 不允許在 model 頂層用 deep dot-path 定義 relationship。**

| 關係類型 | 定義位置 | 路徑寫法 |
|---------|---------|---------|
| L1（actor → system）| model 頂層 | `visitor -> sm '...'` |
| L2（container 間）| model 頂層 | `sm.frontend -> sm.backend '...'` |
| Actor → Module（跨 level，供 module_topology 顯示外部連線）| model 頂層 | `visitor -> sm.backend.registration '...'` |
| Module 內部 | module block 內 | `reg_controller -> reg_service '...'`（相對名）|
| 跨 module（同 container）| container block 內 | `admin_mod.app_svc -> registration.review_uc '...'`（相對名）|

❌ 錯誤寫法（在 model 頂層用 4 層 dot-path）：
```
// 會產生 parse error
sm.backend.registration.reg_controller -> sm.backend.registration.reg_service '...'
```

✅ 正確：把 relationship 移進對應的 container / module block，用相對路徑。

### 3.3 Implements 關係語法

```
// ✅ 正確
account_mod.jpa_existence_adapter -[implements]-> registration.account_existence_port

// ❌ 錯誤（implements 放行尾會被解析為 ElementKind reference）
account_mod.jpa_existence_adapter -> registration.account_existence_port implements
```

### 3.4 Service implements UseCase（模組內部）

模組的 Application Service 實作同模組內的 UseCase interface，必須用 `-[implements]->`。

```
// ✅ 正確：用 relationship kind
reg_service -[implements]-> list_pending_uc
reg_service -[implements]-> review_uc

// ❌ 錯誤：default relationship + label 'implements'與 specification 定義的 implements kind 不一致
reg_service -> list_pending_uc 'implements'
```

### 3.5 Cross-cutting Port（如 PublicPathContributor）必須建模

若有 interface 被多個模組實作（如 `PublicPathContributor`），每個實作模組都需要：

1. 在模組內部加 `adapter` 元素
2. 在 container 層証 container 與 port 所在模組之間的跨模組 `-[implements]->` 關係

```
// 在 registration module 內加
reg_pub_path_adapter = adapter 'RegistrationPublicPathContributor' {
  description 'Declares POST /registrations as PUBLIC to security module (DEC-015)'
}

// 在 backend container block 加 cross-module relationship
registration.reg_pub_path_adapter -[implements]-> security_mod.public_path_port
```

缺少這些建模會導致：
- `ports_and_adapters` view 看不到完整的 extension point 全貌
- `cross_cutting_security` 等 view 無法顯示「哪些模組宣告了 PUBLIC endpoint」

---

## 4. Views 設計慣例

### 4.1 Layer 對應

| View | 語法 | 對應 Structurizr |
|------|------|----------------|
| `system_context` | `view id { include element.kind == actor; include element.kind == system }` | System Context view |
| `container_view of sm` | `view id of sm { include * }` | Container view |
| `backend_internal of sm.backend` | `view id of sm.backend { include * }` | Component view（scoped to backend）|
| `module_topology` | `view id { include element.kind == actor; include element.kind == module }` | Backend-Module-Topology（black-box）|

### 4.2 Kind filter 語法（重要限制）

`include` 只接受 element reference 或 predicate，**不接受 kind 名稱直接寫**：

```
// ❌ 錯誤
include actor
include system

// ✅ 正確（predicate 語法）
include element.kind == actor
include element.kind == system
```

### 4.3 Module drill-down views

LikeC4 點進 module 時，若無對應 scoped view，auto-generated view **只顯示有對外 relationship 的元件**，內部孤立節點不可見。

**每個 module 都要補明確的 scoped view：**

```
view registration_internal of sm.backend.registration {
  title 'Registration Module · Internal'
  include *
}
```

`include *` 強制展開所有子元素，不論是否有對外 relationship。

### 4.4 Auto-aggregation 驗證（`module_topology` view）

`module_topology` 用 kind filter 只 include module，不 include 任何 service / usecase / adapter。LikeC4 會把 module 內部元件的 cross-module relationship 自動聚合到 module 層顯示，不需要手動 include。

這是與 Structurizr 的關鍵差異：Structurizr 需要手動 include + 明確寫出關係；LikeC4 自動推導。

### 4.5 http_surface view 的 Actor → Controller 連線

`include element.kind == actor` + `include element.kind == controller` 只顯示兩種元素，但它們之間的邊必須在 model 裡有**直接** actor → controller relationship 才能顯示。

actor → module 的關係（如 `visitor -> sm.backend.registration`）**不會**自動向下聚合到 controller。

解法：在 backend container block 內補明確的 actor → controller 關係：

```
// 放在 backend 內部的 cross-module section
visitor -> registration.reg_controller 'POST /registrations [PUBLIC]'
admin -> admin_mod.auth_ctrl 'POST /auth/login [PUBLIC]'
admin -> admin_mod.app_ctrl 'GET/PATCH /admin/applications [JWT]'
```

不要在 model 頂層用 4 層 dot-path `visitor -> sm.backend.registration.reg_controller`（parse error）。

### 4.6 Controller 點擊導航至 Module Internal View

當 `http_surface` 改用明確 include 時，可用 `with { navigateTo <viewId> }` 讓每個 controller 點擊後跳至其所在 module 的 drill-down view。

**加層效果（Compound Nesting）**：同時 include module 和其內部的 controller，LikeC4 會將 controller 渲染在 module 的邊界框內。module 和 controller 各自都可指定 `navigateTo`：

```
// ✅ 模組作為 compound 邊界框 + controller 嵌層在內
// 點擊 module 邊界或 controller 都跳至對應的 internal view
include sm.backend.registration with {
  navigateTo registration_internal
}
include sm.backend.registration.reg_controller with {
  navigateTo registration_internal
}
include sm.backend.admin_mod with {
  navigateTo admin_internal
}
include sm.backend.admin_mod.auth_ctrl with {
  navigateTo admin_internal
}
include sm.backend.admin_mod.app_ctrl with {
  navigateTo admin_internal
}

// ❌ 無法做的：kind filter 無法對不同元素指定不同 navigateTo 目標
// include element.kind == controller with { navigateTo ??? }
```

**Compound Nesting 訪則**：
- `include parent` 必須在 `include child` **之前**，順序重要
- include 了 module，實際 actor → module 的關係線會连到 module 邊界框（而非直展到 controller），說明所屬模組有正確語氣
- 同時 include module 與 controller 內部實作，筆尖會展到具體的 controller 層級

**注意**：`include` 裡用 4 層 dot-path（如 `sm.backend.registration.reg_controller`）在 view predicate 裡是合法的。4 層 dot-path 的限制只適用於 **model 區塊的 relationship 定義**，不適用於 view 的 `include`。

---

## 5. 已知 DSL 限制與解法速查

| 症狀 | 根因 | 解法 |
|------|------|------|
| `Duplicate element name sm` | deep dot-path parse 失敗的 cascade error | 把 relationship 移進正確的 parent block |
| `Could not resolve reference to ElementKind named 'implements'` | `-> target implements` 語法錯誤 | 改用 `-[implements]->` |
| `Expecting token of type '}' but found '.'` | model 頂層用 4+ 層 dot-path 定義 relationship | 移進對應 container / module block |
| `Could not resolve reference to Referenceable named 'actor'` | `include actor` 用了 kind 名稱 | 改用 `include element.kind == actor` |
| 點進 module 看不到內部元件 | 無對應 scoped view；auto-generated view 只顯示對外元件 | 補 `view id of <module> { include * }` || `http_surface` 裡 actor 和 controller 各自孤立，沒有連線 | actor → module 關係不會向下聚合到 controller | 在 backend block 補 actor → controller 直接關係 |
| `ports_and_adapters` 漏掉 extension point 的實作者 | 只建模了 port，沒有補各實作模組的 adapter 元素 | 每個實作類別都要加對應 adapter 元素 + `-[implements]->` |
| service 定義的 implements 關係在圖上看起來是普通简頭笠 | 用了 `-> usecase 'implements'`（普通 label）而非 `-[implements]->` | 統一改用 `-[implements]->` relationship kind |
---

## 6. 檔案結構

```
likec4-sandbox/
  architecture.c4          ← 主 DSL（specification + model + views）
  LIKEC4_CONVENTIONS.md    ← 本文件
```

---

## 7. Views 清單

| View ID | 類型 | 說明 |
|---------|------|------|
| `system_context` | L1 | Actor + System，consultant 入口 |
| `container_view` | L2 scoped to sm | 4 containers + actors |
| `backend_internal` | L3 scoped to sm.backend | 全部 module（黑箱），等同 Structurizr Component view |
| `module_topology` | L3 kind filter | Module 黑箱 + cross-module arrows 自動聚合 |
| `registration_internal` | module drill-down | Registration 全部內部元件 |
| `admin_internal` | module drill-down | Admin 全部內部元件 |
| `account_internal` | module drill-down | Account 全部內部元件 |
| `security_internal` | module drill-down | Security 全部內部元件 |
| `ports_and_adapters` | cross-module | 所有 port + adapter，SEAM-001 可見 |
| `http_surface` | cross-module | 所有 controller + actors，HTTP audit surface |
| `cross_cutting_security` | 手動 include | Security module 內部 + 跨模組 auth 入口 |
