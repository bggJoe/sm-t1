# SYSTEM-CAPABILITIES.md — Cross-Cutting 規範骨架

> 本文件是 Cross-Cutting Concerns 的系統級規範。
> 不是業務 SLICE，是「**所有 SLICE 在開發時必須遵守的橫向能力**」。
> 受眾：技術管理者、開發團隊、LLM 開發夥伴、合規審計。

---

## 文件性質與閱讀守則

### 對人類讀者

- 技術管理者：看 Section A（**Capability Inventory**）了解系統有哪些非功能要求被治理
- 架構審計：看 Section B（**Coverage Matrix**）確認每個 module/layer 的橫向能力覆蓋
- 開發者：看 Section C（**Per-Capability Spec**）了解具體怎麼遵守
- 合規：看 Section D（**Compliance Mapping**）確認對外承諾如何被技術機制落實

### 對 LLM 讀者

- 進入任何 SLICE 開發前**必讀本文件 Section A 與該 SLICE 涉及的 Section C 條目**
- 違反本文件的設計提案應主動標 `[CROSS-CUTTING-VIOLATION]` 提醒人類複核
- 不可在 SLICE 業務 D2 中畫 cross-cutting 為 Port 節點（參考 CROSS-CUTTING-CONCERNS.md）

---

## Section A — Capability Inventory（能力清冊）

| ID | Capability | 機制 | 引入 SLICE | 狀態 |
|----|-----------|------|-----------|------|
| CC-001 | Authentication | SecurityFilterChain + JWT | SLICE-002 | TBD |
| CC-002 | Authorization | `@PreAuthorize` + Method Security | SLICE-002 | TBD |
| CC-003 | Transaction | Spring `@Transactional` | SLICE-001 | Active |
| CC-004 | Audit Log | Spring Events + 持久化 | SLICE-002 | TBD |
| CC-005 | Metrics | Micrometer Observation API | TBD | Planned |
| CC-006 | Distributed Tracing | OTel + W3C Trace Context | TBD | Planned |
| CC-007 | Application Logging | SLF4J + Logback | SLICE-001 | Active |
| CC-008 | Rate Limiting | Bucket4j / Redis | TBD | Planned |
| CC-009 | Input Validation | Bean Validation `@Valid` | SLICE-001 | Active |
| CC-010 | Idempotency | Idempotency Key + TTL store | TBD | Deferred |
| CC-011 | Module Boundary | Spring Modulith + ArchUnit | SLICE-001 | Active |
| CC-012 | Layer Boundary | ArchUnit Layered Architecture | SLICE-001 | Active |

**狀態說明**：
- `Active`：已實作且 enforced
- `TBD`：規劃但未實作（含預期 SLICE）
- `Planned`：尚未排期，先列入避免遺漏
- `Deferred`：明確決定不做，附理由

---

## Section B — Coverage Matrix（覆蓋矩陣）

哪個 capability 在哪個 module/layer 生效。**未填的格子代表「明確不適用」**，不是遺漏。

|              | registration | account (future) | admin (future) |
|--------------|:-:|:-:|:-:|
| CC-001 Auth  | 例外 (no auth) | ✓ | ✓ |
| CC-002 Authz | — | ✓ | ✓ |
| CC-003 Tx    | ✓ | ✓ | ✓ |
| CC-004 Audit | TBD | ✓ | ✓ |
| CC-005 Metrics | ✓ | ✓ | ✓ |
| CC-007 Logging | ✓ | ✓ | ✓ |
| CC-009 Validation | ✓ | ✓ | ✓ |
| CC-011 Module Boundary | ✓ | ✓ | ✓ |

**例外標記說明**：
- `✓`：適用且已 enforced
- `TBD`：適用但尚未實作
- `例外 (理由)`：明確不適用，附理由
- `—`：邏輯上不可能適用（譬如註冊 module 沒有 authorization 因為沒登入概念）

---

## Section C — Per-Capability Spec

每個 capability 的詳細規範。範例展示三條（其餘視需要展開）。

---

### CC-001 Authentication

**動機（Why）**：
保護需要識別使用者身份的 API 端點。違反代價：未授權存取造成資料外洩或業務濫用。

**機制（How）**：
- 實作技術：Spring Security 6.x SecurityFilterChain + JWT Resource Server
- Token 來源：Authorization header `Bearer <token>`
- 驗證點：`SecurityFilterChain` 在 DispatcherServlet 之前執行
- Public endpoint 白名單：定義在 `SecurityConfig#publicEndpointMatcher`

**範圍（Where applies）**：
- 預設：所有 `*Controller` 端點
- 例外：白名單端點（目前僅 `POST /registrations`，由 SLICE-001 標 no auth）

**驗證錨點（Verify）**：
- 整合測試：`SecurityIntegrationTest` 驗證白名單外端點無 token 回 401
- ArchUnit 規則：`@RestController` 類別必須在 SecurityConfig 的 matcher 中明確列出（否則可能默默變成公開端點）
- 測試類路徑：`src/test/java/.../security/`

**例外管理（Exceptions）**：
- 新增公開端點必須在 SLICE 文件「範圍邊界」明確聲明 `no auth`，並更新 SecurityConfig 白名單
- 公開端點數量增加觸發 review：當 > 3 個時觸發架構討論

**合規對應（Compliance）**：
- ISO 27001 A.9.4.1（資訊存取限制）
- 公司內部政策：`SEC-POLICY-001 身份驗證標準`

**演化路徑（Evolution）**：
- v1：JWT + 單一 Issuer
- v2 (TBD)：考慮 OIDC + Multi-tenant
- v3 (TBD)：考慮 mTLS for service-to-service

**反證條件（Falsification）**：
此規範的有效性建立在以下假設。若任一為真，需要重審：
- 出現需要驗證但不適合 JWT 的場景（譬如 WebSocket 不能套用 Bearer header）
- 公司 SSO 整合要求改用 SAML 而非 JWT

---

### CC-003 Transaction

**動機**：
保證業務操作的原子性。違反代價：部分寫入失敗導致資料不一致。

**機制**：
- 實作技術：Spring `@Transactional`
- 預設層級：Application Service 方法
- Propagation 預設：`REQUIRED`
- Rollback 預設：所有 RuntimeException

**範圍**：
- 必須：所有改變 DB 狀態的 Application Service 方法
- 禁止：在 Repository 層宣告 `@Transactional`（破壞 Service 邊界）
- 禁止：在 Controller 層宣告 `@Transactional`（業務邊界滲透到 Presentation）

**驗證錨點**：
- ArchUnit 規則：`*ApplicationService` 的 public 方法必須有 `@Transactional`
- ArchUnit 規則：`*Repository` 與 `*Controller` 不可有 `@Transactional`
- 跨 module 寫入禁止用單一 transaction：必須走 outbox / saga（CC-### TBD）

**例外管理**：
- 純查詢方法可用 `@Transactional(readOnly = true)`，但不可省略註解（顯式 > 預設）
- 跨 module 寫入需要 DECISION 條目記錄補償策略

**演化路徑**：
- 當前：單一 RDB 事務
- 未來：跨 module 引入 outbox pattern (Spring Modulith 支援)

---

### CC-011 Module Boundary

**動機**：
維護模組間的弱耦合，避免結構腐敗。違反代價：架構漂移、無法獨立演化、抽出微服務時痛苦。

**機制**：
- 實作技術：Spring Modulith `@ApplicationModule` + `@NamedInterface`
- 驗證工具：`ApplicationModules.of(App.class).verify()` + ArchUnit 自定規則

**範圍**：
- 所有 top-level package 對應一個 module
- 跨 module 通訊只能透過 `@NamedInterface` 標記的類別
- 跨 module 寫入只能透過 Domain Event（非同步）

**驗證錨點**：
- 整合測試：`ModuleStructureTest` 在 CI 跑
- 詳細規範：見 `modulith-archunit-guide.md`
- 切分依據宣告：每個 module 的 `package-info.java` 連結到 Module Canvas

**例外管理**：
- 新增 module 必須先完成 Module Canvas（Phase 0）
- 跨 module 同步呼叫需要明確 DECISION 條目（預設禁止）

**合規對應**：
- 內部架構治理政策

---

## Section D — Compliance Mapping（合規對應）

對外合規承諾如何被技術機制落實。**這節是合規審計的查表入口**。

| 合規要求 | 技術落實 | 證據 |
|---------|---------|------|
| ISO 27001 A.9.4.1 存取限制 | CC-001, CC-002 | SecurityIntegrationTest |
| ISO 27001 A.12.4.1 事件記錄 | CC-004, CC-007 | AuditEventTest, log retention policy |
| GDPR Art. 32 資料安全 | CC-001, CC-003 (atomic write) | (TBD) |
| 內部 SOC2 變更追蹤 | CC-004 | AuditEventStore |

**未填項目代表尚未對應**——合規審計時這些是首先被質疑的地方。

---

## Section E — Verification Index（驗證索引）

LLM / CI / 審計都會查的快速索引：每個 capability 的測試類在哪。

```
src/test/java/com/acme/
├── security/
│   ├── SecurityIntegrationTest.java         ← CC-001, CC-002
│   └── PublicEndpointTest.java              ← CC-001 例外驗證
├── transaction/
│   └── TransactionRollbackTest.java         ← CC-003
├── audit/
│   └── AuditEventTest.java                  ← CC-004 (TBD)
├── architecture/
│   ├── ModuleStructureTest.java             ← CC-011
│   ├── LayerDependencyTest.java             ← CC-012
│   └── TransactionAnnotationTest.java       ← CC-003 ArchUnit
└── ...
```

---

## Section F — Open Questions（開放問題）

預留位置記錄尚未決定的橫切問題。**留白比假裝完備好**。

- **多租戶 auth**：tenant 識別是業務還是橫切片？
- **Saga / 分散事務**：跨 module 補償邏輯既有業務語意又有技術機制，可能需要第三類處理
- **Observability 與 closed loop**：runtime trace 是否反向影響業務語意？若是，部分 observability 升級為業務 SEAM
- **Audit Log 的查詢**：審計查詢本身需不需要被審計？

---

## 變更規則

修改本文件必須：
1. 同步更新對應的 ArchUnit 規則或測試
2. 新增 DECISIONS.md 條目記錄變更動機
3. 觸及合規對應時通知合規團隊
4. 更新版本號與日期

**任何「先改文件、之後再對齊測試」的承諾都視為違反本文件**——人腦的 follow-up 不可信。
