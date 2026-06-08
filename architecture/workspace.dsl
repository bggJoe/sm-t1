workspace "SM-T1" "System Module - Methodology Validation" {

    model {
        visitor = person "Visitor" "未持有帳號、想申請成為系統使用者" {
            tags "Actor"
        }

        admin = person "Admin" "管理者，負責審核申請" {
            tags "Actor"
        }

        smSystem = softwareSystem "SM-T1 System" "Spring Boot 3 + Spring Modulith + Angular 20" {

            frontend = container "Frontend" "Angular 20 SPA" "Angular 20" {
                tags "Frontend"

                registrationForm = component "RegistrationForm" "申請表單 Component，收集 email 與 name" "Angular Component" {
                    tags "Presentation"
                }
            }

            backend = container "Backend" "Spring Boot 3.5.13 + Spring Modulith 1.3.12" "JVM / Java 21" {
                tags "Backend"

                // ── registration 模組 ──────────────────────────────────────────
                group "registration 模組" {
                    registrationController = component "RegistrationController" "接收 POST /registrations，無 auth 保護" "Spring RestController" {
                        tags "Presentation" "PublicEndpoint"
                    }

                    registrationAppService = component "RegistrationApplicationService" "執行 INV-001~004，協調持久化與 Port" "Spring Service" {
                        tags "Application"
                    }

                    accountExistencePort = component "AccountExistencePort" "[SEAM-001] 查詢 email 是否已有 APPROVED 帳號；registration 模組消費者定義的 Port Interface" "Java Interface" {
                        tags "Application" "SEAM" "Port"
                    }

                    registrationAppRepository = component "RegistrationApplicationRepository" "Application 層 Port：定義申請紀錄的持久化契約（介面）；JPA 實作在 Infrastructure" "Java Interface" {
                        tags "Application" "Port"
                    }

                    listPendingApplicationsUseCase = component "ListPendingApplicationsUseCase" "registration 模組公開介面：查詢所有 PENDING 申請" "Java Interface" {
                        tags "Application" "Port"
                    }

                    reviewApplicationUseCase = component "ReviewApplicationUseCase" "registration 模組公開介面：對指定申請執行 approve 或 reject（INV-008/009/010）" "Java Interface" {
                        tags "Application" "Port"
                    }

                    jpaRegistrationApplicationRepository = component "JpaRegistrationApplicationRepository" "RegistrationApplicationRepository 的 Spring Data JPA 實作" "Spring Data Repository" {
                        tags "Infrastructure" "Adapter" "Real"
                    }

                    registrationPublicPathContributor = component "RegistrationPublicPathContributor" "宣告 /registrations 為 PUBLIC 路徑的 Extension Point 實作" "Spring Component" {
                        tags "Infrastructure" "Adapter" "Real"
                    }
                }

                // ── security 模組 ──────────────────────────────────────────────
                group "security 模組" {
                    publicPathContributor = component "PublicPathContributor" "Extension Point：security 模組定義，業務模組各自 implement 宣告 PUBLIC 路徑；SecurityConfig 啟動時透過 List<PublicPathContributor> 收集全部實作" "Java Interface" {
                        tags "Application" "Port" "ExtensionPoint"
                    }

                    jwtTokenProvider = component "JwtTokenProvider" "JWT 簽發（iat/sub/exp）與驗證；security 模組唯一對外公開 API" "Spring Component" {
                        tags "Infrastructure" "Port"
                    }

                    securityConfig = component "SecurityConfig" "收集所有 PublicPathContributor，建立 SecurityFilterChain；預設全路徑保護" "Spring Configuration" {
                        tags "Infrastructure"
                    }
                }

                // ── admin 模組 ─────────────────────────────────────────────────
                group "admin 模組" {
                    adminAuthController = component "AdminAuthController" "接收 POST /auth/login，PublicPathContributor 宣告為 PUBLIC" "Spring RestController" {
                        tags "Presentation" "PublicEndpoint"
                    }

                    adminAuthService = component "AdminAuthService" "驗證 admin 帳密（bcrypt），協調 JWT 簽發；執行 INV-005/006" "Spring Service" {
                        tags "Application"
                    }

                    adminCredentialRepository = component "AdminCredentialRepository" "Application 層 Port：定義 admin 帳密查詢契約；JPA 實作在 Infrastructure" "Java Interface" {
                        tags "Application" "Port"
                    }

                    adminCredential = component "AdminCredential" "Admin 帳號 entity：username + bcrypt 密碼 hash" "JPA Entity" {
                        tags "Domain"
                    }

                    adminApplicationController = component "AdminApplicationController" "接收 GET /admin/applications 與 PATCH /admin/applications/{id}/review；受 JWT 保護；委派至 AdminApplicationService" "Spring RestController" {
                        tags "Presentation"
                    }

                    adminApplicationService = component "AdminApplicationService" "Admin 模組 Application Service：協調跨模組兩階段流程（Stage1 review + Stage2 create）；處理部分失敗；不含業務規則（DEC-021）" "Spring Service" {
                        tags "Application"
                    }

                    jpaAdminCredentialRepository = component "JpaAdminCredentialRepository" "AdminCredentialRepository 的 Spring Data JPA 實作" "Spring Data Repository" {
                        tags "Infrastructure" "Adapter" "Real"
                    }

                    adminPublicPathContributor = component "AdminPublicPathContributor" "宣告 /auth/login 為 PUBLIC 路徑的 Extension Point 實作" "Spring Component" {
                        tags "Infrastructure" "Adapter" "Real"
                    }
                }

                // ── account 模組 ───────────────────────────────────────────────
                group "account 模組" {
                    createAccountUseCase = component "CreateAccountUseCase" "account 模組公開介面：建立 ACTIVE 帳號，BCrypt 密碼 hash（INV-011/012/013）" "Java Interface" {
                        tags "Application" "Port"
                    }

                    accountApplicationService = component "AccountApplicationService" "執行 INV-011~013，協調帳號持久化；BCrypt encode 預設密碼" "Spring Service" {
                        tags "Application"
                    }

                    accountEntity = component "Account" "帳號 Aggregate Root：id(UUID)・email(UNIQUE)・passwordHash・status=ACTIVE・createdAt・lastModifiedAt" "JPA Entity" {
                        tags "Domain"
                    }

                    accountRepository = component "AccountRepository" "Application 層 Port：定義帳號持久化契約（介面）；JPA 實作在 Infrastructure" "Java Interface" {
                        tags "Application" "Port"
                    }

                    jpaAccountExistenceAdapter = component "JpaAccountExistenceAdapter" "[SEAM-001] Real 實作，取代 StubAccountExistenceAdapter；查詢 accounts table 是否有相同 email（INV-014）" "Spring Component" {
                        tags "Infrastructure" "Adapter" "Real"
                    }

                    jpaAccountRepository = component "JpaAccountRepository" "AccountRepository 的 Spring Data JPA 實作" "Spring Data Repository" {
                        tags "Infrastructure" "Adapter" "Real"
                    }
                }
            }

            db = container "Database" "PostgreSQL 17（生產）/ H2（測試）" "RDBMS" {
                tags "Database"
            }
        }

        visitor -> registrationForm "填寫並送出申請"
        registrationForm -> registrationController "POST /registrations (no auth, JSON)"
        visitor -> registrationController "POST /registrations (no auth, JSON)" "L3 component view 直連（frontend SPA 尚未設計，在 L3 省略，L1/L2 保留 frontend 節點）"
        registrationController -> registrationAppService "submitApplication(cmd)"
        registrationAppService -> accountExistencePort "hasApprovedAccount(email)"
        registrationAppService -> registrationAppRepository "findPendingByEmail / save"

        // SLICE-002 relationships
        admin -> adminAuthController "POST /auth/login {username, password}"
        adminAuthController -> adminAuthService "authenticate(cmd)"
        adminAuthService -> adminCredentialRepository "findByUsername(username)"
        adminAuthService -> jwtTokenProvider "issueToken(username)"
        securityConfig -> publicPathContributor "收集所有實作（List<PublicPathContributor>）"
        registrationPublicPathContributor -> publicPathContributor "implements"
        adminPublicPathContributor -> publicPathContributor "implements"

        // SLICE-003 relationships
        admin -> adminApplicationController "GET /admin/applications / PATCH /admin/applications/{id}/review [Bearer JWT]"
        adminApplicationController -> listPendingApplicationsUseCase "listPending()"
        adminApplicationController -> reviewApplicationUseCase "review(id, decision, reason, reviewerUsername)"
        listPendingApplicationsUseCase -> registrationAppService "（實作）"
        reviewApplicationUseCase -> registrationAppService "（實作）"
        registrationAppService -> registrationAppRepository "findAllByStatus / findById / save"

        // SLICE-004 relationships
        adminApplicationController -> adminApplicationService "approveAndProvisionAccount(id, reviewer)"
        adminApplicationService -> reviewApplicationUseCase "Stage 1：review(id, APPROVE, reviewer)（transaction #1）"
        adminApplicationService -> createAccountUseCase "Stage 2：create(email)（transaction #2，Stage 1 成功後）"
        createAccountUseCase -> accountApplicationService "（實作）"
        accountApplicationService -> accountRepository "save / findByEmail"
        accountApplicationService -> accountEntity "new Account(email, BCrypt, ACTIVE)（INV-012, INV-013）"

        // Port ↔ Adapter implements relationships（SEAM 生命週期）
        jpaAccountExistenceAdapter -> accountExistencePort "implements（Real，SEAM-001 填充）"
        jpaAccountExistenceAdapter -> db "JPA / JDBC（SELECT accounts WHERE email）"
        jpaAccountRepository -> accountRepository "implements（Real）"
        jpaAccountRepository -> db "JPA / JDBC"
        jpaRegistrationApplicationRepository -> registrationAppRepository "implements（Real）"
        jpaRegistrationApplicationRepository -> db "JPA / JDBC"
        jpaAdminCredentialRepository -> adminCredentialRepository "implements（Real）"
        jpaAdminCredentialRepository -> db "JPA / JDBC"
    }

    views {
        systemContext smSystem "SystemContext" {
            include *
            autolayout tb
        }

        container smSystem "Containers" {
            include visitor admin backend db
            autolayout tb
            description "後端專注模式：frontend SPA 尚未設計，暂省略（在 L1 SystemContext 中保留）"
        }

        component backend "Backend-Components-SLICE-001" {
            include visitor registrationController registrationAppService accountExistencePort jpaAccountExistenceAdapter registrationAppRepository jpaRegistrationApplicationRepository db
            autolayout tb
            description "SLICE-001（凍結）：用戶提出帳戶註冊申請的後端元件。L3 以 Visitor actor 直連 backend，frontend SPA 省略（見 L1/L2）。注意：AccountExistencePort 的 Adapter 在 SLICE-001 原為 StubAccountExistenceAdapter，已於 SLICE-004 替換為 JpaAccountExistenceAdapter（SEAM-001 填充）。"
        }

        component backend "Backend-Components-SLICE-002" {
            include admin visitor adminAuthController adminAuthService adminCredentialRepository adminCredential jpaAdminCredentialRepository jwtTokenProvider securityConfig publicPathContributor registrationPublicPathContributor adminPublicPathContributor registrationController db
            autolayout tb
            description "SLICE-002（凍結）：Admin 身份驗證（JWT 基礎建設）；L3 以 Actor 直連 backend，frontend SPA 省略（見 L1/L2）。"
        }

        component backend "Backend-Components-SLICE-004" {
            include admin adminApplicationController adminApplicationService
            include createAccountUseCase accountApplicationService accountEntity accountRepository jpaAccountRepository
            include reviewApplicationUseCase registrationAppService registrationAppRepository jpaRegistrationApplicationRepository
            include accountExistencePort jpaAccountExistenceAdapter
            include jwtTokenProvider securityConfig db
            autolayout tb
            description "SLICE-004（凍結）：Admin 協調帳號建立（DEC-021）；兩階段分離 transaction； SEAM-001 填充（Stub 移除，JpaAccountExistenceAdapter 接管）。"
        }

        component backend "Backend-Components-SLICE-003" {
            include admin adminApplicationController listPendingApplicationsUseCase reviewApplicationUseCase registrationAppService registrationAppRepository jpaRegistrationApplicationRepository jwtTokenProvider securityConfig db
            autolayout tb
            description "SLICE-003（凍結）：Admin 審核申請；薄 Portal 模式（DEC-019）— AdminApplicationController 委派至 registration 模組 UseCase；L3 以 Admin actor 直連 backend。"
        }

        component backend "Backend-Components" {
            include *
            autolayout tb
            description "累積全貌：隨每個 SLICE 完成後更新，永遠反映當前所有已實作元件（DEC-017）"
        }

        styles {
            element "Person" {
                shape Person
                background #08427b
                color #ffffff
            }
            element "Frontend" {
                background #438dd5
                color #ffffff
            }
            element "Backend" {
                background #2d6099
                color #ffffff
            }
            element "Database" {
                shape Cylinder
                background #438dd5
                color #ffffff
            }
            element "Presentation" {
                background #b0c4de
                color #000000
            }
            element "Application" {
                background #fff8e6
                color #000000
            }
            element "Infrastructure" {
                background #d4edda
                color #000000
            }
            element "SEAM" {
                border Dashed
                background #f9f0ff
                color #000000
            }
            element "Stub" {
                border Dashed
                background #fff3cd
                color #856404
            }
            element "ExtensionPoint" {
                border Dashed
                background #cfe2ff
                color #084298
            }
            element "Adapter" {
                background #d1e7dd
                color #0a3622
            }
            element "PublicEndpoint" {
                background #ffe8e8
                color #000000
            }

            // ── Module group boundary styles ──────────────────────────────
            element "Group:registration 模組" {
                color #2d6e2d
                strokeWidth 3
            }
            element "Group:security 模組" {
                color #1a4a7a
                strokeWidth 3
            }
            element "Group:admin 模組" {
                color #8a6200
                strokeWidth 3
            }
            element "Group:account 模組" {
                color #2a4a8a
                strokeWidth 3
            }
        }
    }
}