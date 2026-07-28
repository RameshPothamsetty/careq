# CareQ — Intelligent Patient Flow Platform

**Brand:** SmartOPD AI  
**Project Name:** CareQ  
**Duration:** 15 Days  
**Methodology:** TrainingMug AI Development Framework (ADF) v1.0

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend | React (Vite) | 18.x |
| Backend | Spring Boot | 3.2.0 |
| Security | Spring Security + JWT | 3.2.0 |
| Database | MySQL | 8.x |
| Service Registry | Netflix Eureka | 2023.0.0 |
| API Gateway | Spring Cloud Gateway | 2023.0.0 |
| Build Tool | Maven | 3.9+ |
| Language | Java | 17 |
| UI Toolkit | Tailwind CSS (to be added) | 3.x |

---

## Architecture Overview

CareQ follows a **microservices architecture** with the following services:

```
┌─────────────┐     ┌──────────────┐
│   React App │────▶│ API Gateway  │
│  (Frontend) │     │  (port 8080) │
└─────────────┘     └──────┬───────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   ┌────────────┐   ┌────────────┐   ┌────────────┐
   │ Auth       │   │ User       │   │ Doctor     │
   │ Service    │   │ Service    │   │ Service    │
   └────────────┘   └────────────┘   └────────────┘
          │                │                │
   ┌────────────┐                           │
   │ Queue      │◀──────────────────────────┘
   │ Service    │
   └────────────┘
          │
   ┌──────────────┐
   │  Eureka      │◀──── All services register here
   │  Server      │      (port 8761)
   └──────────────┘
          │
   ┌──────────────┐
   │    MySQL     │◀──── All services (single shared DB)
   │  careq_db    │
   └──────────────┘
```

### Microservices List

| Service | Responsibility | Default Port |
|---------|---------------|-------------|
| **eureka-server** | Service registry — all services register here for discovery | 8761 |
| **api-gateway** | Entry point — routes requests to appropriate microservices | 8080 |
| **auth-service** | User signup/login, JWT issuance, role-based access control | 8081 |
| **user-service** | CRUD for Patient/Doctor/Admin profiles | 8082 |
| **doctor-service** | Doctor & Department catalog — create, browse, filter by specialty | 8083 |
| **queue-service** | Queue management — join queue, AI wait-time prediction, AI triage | 8084 |

---

## Folder Structure (ADF Section 3)

```
careq/
├── README.md
├── docs/
│   ├── 01_PROJECT_CONTEXT.md
│   ├── 02_REQUIREMENTS.md
│   ├── 03_ARCHITECTURE.md
│   ├── 04_DATABASE.md
│   └── ... (future: API docs, deployment guides)
├── backend/
│   ├── pom.xml                          # Parent Maven POM
│   ├── eureka-server/                   # Service Registry
│   ├── api-gateway/                     # API Gateway
│   ├── auth-service/                    # Authentication
│   ├── user-service/                    # User Profiles
│   ├── doctor-service/                  # Doctor/Department catalog
│   └── queue-service/                   # Queue & AI operations
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── components/                  # Shared components
│   │   ├── pages/                       # Route pages
│   │   ├── services/                    # API client stubs
│   │   ├── store/                       # State management (future)
│   │   ├── App.tsx                      # Root with routing
│   │   └── main.tsx                     # Entry point
│   ├── package.json
│   ├── vite.config.ts
│   └── index.html
├── docker/
│   └── docker-compose.yml               # (Future — Day 10+)
├── postman/
│   └── CareQ.postman_collection.json    # (Future — Day 5+)
└── .github/
    └── workflows/
        └── ci.yml                       # (Future — Day 12+)
```

---

## Git Branching Strategy

```
main          ─── Production-ready (protected)
  └── develop ─── Integration branch (daily pushes)
       ├── feature/day-1-requirements
       ├── feature/day-2-auth
       ├── feature/day-3-profiles
       └── ... (one feature branch per day)
```

- **`main`** — Always deployable. Merged only at the end of 15 days.
- **`develop`** — Daily integration branch. Push every evening after completing the day's checklist.
- **`feature/*`** — One branch per day's work. Merged into `develop` at end of day.

---

## Deployment Strategy (Future Phase)

> **Note:** Deployment configuration is explicitly out of scope for Days 1–9. It will be addressed in later checklist days.

Target stack (to be built):
- **Containerization:** Docker (one container per microservice + MySQL + React)
- **Orchestration:** Docker Compose (local) → Azure Container Apps / Kubernetes (production)
- **CI/CD:** GitHub Actions (build → test → deploy)
- **Hosting:** Azure (backend) + Vercel (frontend)
- **Database:** Azure Database for MySQL / AWS RDS

---

## ADF Framework Principles Applied

1. **Design before development; document before code** — All docs generated on Day 1
2. **Build one module at a time** — Each day focuses on one slice of functionality
3. **Every feature must be tested and documented** — Unit tests required per service
4. **Every day ends with a GitHub push to `develop`** — Never to `main`
5. **Layered architecture** — Controller → Service → Repository → Entity
6. **DTO pattern** — Never expose entities over the wire
7. **Constructor injection** — No field injection with `@Autowired`
8. **No business logic in controllers** — Thin controllers, fat services
