# CareQ — Intelligent Patient Flow Platform

**Brand:** SmartOPD AI

An AI-powered OPD (Outpatient Department) operations platform that predicts patient wait times, triages patients by urgency using AI, and gives Patients, Doctors, and Admins a live, role-specific view of hospital queue operations.

---

## Project Status

| Day | Module | Status |
|-----|--------|--------|
| Day 1 | Project Scaffold | ✅ Complete |
| Day 2 | Authentication (JWT, Login, Signup) | ✅ Complete |
| Day 3 | User Module (Profiles, File Upload) | ✅ Complete |
| **Day 4** | **Doctor/Department Module** | **✅ Complete** |
| Day 5 | Queue Service + AI Triage | 📅 Planned |
| Day 6+ | Doctor Queue Mgmt, Testing, Deployment | 📅 Planned |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Spring Boot 3.2 + Java 17 |
| Service Registry | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Database | MySQL 8 |
| Auth | Spring Security + JWT (HMAC-SHA256) |
| Build | Maven |

---

## Project Structure

```
careq/
├── README.md
├── docs/                          # Requirements, architecture, DB schema, API contracts
├── backend/
│   ├── pom.xml                    # Parent Maven POM
│   ├── eureka-server/             # Service registry (port 8761)
│   ├── api-gateway/               # API gateway (port 8080)
│   ├── auth-service/              # Auth & JWT (port 8081)
│   ├── user-service/              # Profile management + file upload (port 8082)
│   ├── doctor-service/            # Doctor/Department catalog (port 8083)
│   └── queue-service/             # Queue & AI operations (port 8084)
├── frontend/                      # React SPA (port 3030)
├── scripts/                       # Utility scripts (e.g., seed-data.sh)
└── uploads/                       # Profile picture uploads (auto-created)
```

---

## Microservices

| Service | Port | Responsibility |
|---------|------|---------------|
| **eureka-server** | 8761 | Service registry — all services register here |
| **api-gateway** | 8080 | Entry point — JWT validation, route to microservices |
| **auth-service** | 8081 | User signup/login, JWT issuance |
| **user-service** | 8082 | Profile CRUD, profile picture upload |
| **doctor-service** | 8083 | Department & Doctor catalog management |
| **queue-service** | 8084 | Queue operations, AI wait prediction, triage (Day 5+) |

---

## Documentation

All project documentation is in the [docs/](docs/) folder:

| Document | Description |
|----------|-------------|
| [01_PROJECT_CONTEXT.md](docs/01_PROJECT_CONTEXT.md) | Tech stack, architecture, folder structure, git strategy |
| [02_REQUIREMENTS.md](docs/02_REQUIREMENTS.md) | SRS — features, user stories, non-functional requirements |
| [03_ARCHITECTURE.md](docs/03_ARCHITECTURE.md) | Architecture diagram, service responsibilities, auth flow |
| [04_DATABASE.md](docs/04_DATABASE.md) | ER diagram, MySQL schema, column design rationale |
| [05_API_CONTRACT.md](docs/05_API_CONTRACT.md) | API contracts for all services (auth, user, doctor) |
| [09_TESTING.md](docs/09_TESTING.md) | Unit test plans for all modules |
| [11_PROMPTS.md](docs/11_PROMPTS.md) | Archive of daily development prompts |

---

## How to Run

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8+
- Maven 3.9+

### Backend (Start in Order)

```bash
# 1. Start MySQL and create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS careq_db;"

# 2. Start Eureka Server (Service Registry)
cd backend/eureka-server
mvn spring-boot:run

# 3. Start API Gateway (JWT validation, routing)
cd backend/api-gateway && mvn spring-boot:run

# 4. Start Auth Service (login, signup, JWT)
cd backend/auth-service && mvn spring-boot:run

# 5. Start User Service (profiles, file upload)
cd backend/user-service && mvn spring-boot:run

# 6. Start Doctor Service (departments, doctor catalog)
cd backend/doctor-service && mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be available at **http://localhost:3030**.

---

## Seed Data

To populate the app with sample data (admin, 10 doctors with catalog entries, and a test patient):

```bash
bash scripts/seed-data.sh
```

| Account | Email | Password |
|---------|-------|----------|
| Admin | `admin@careq.com` | `admin123` |
| Patient | `john@careq.com` | `password123` |
| Dr. Arjun Sharma | `dr.arjun@careq.com` | `password123` |
| Dr. Priya Patel | `dr.priya@careq.com` | `password123` |
| Dr. Vikram Reddy | `dr.vikram@careq.com` | `password123` |
| (and 7 more doctors...) | See `scripts/seed-data.sh` | `password123` |

---

## API Endpoints (Day 4)

### Auth Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/signup` | No | Create account |
| POST | `/api/auth/login` | No | Login, get JWT |

### User Profile Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/users/me` | Yes | View own profile (lazy-created) |
| PUT | `/api/users/me` | Yes | Update own profile |
| POST | `/api/users/me/profile-picture` | Yes | Upload profile picture |
| GET | `/api/users/{id}` | Admin | View any user's profile |
| GET | `/api/users/profile-pictures/{filename}` | No | Serve uploaded images |

### Department Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/departments` | Yes | List all departments |
| GET | `/api/departments/{id}` | Yes | Get department by ID |
| POST | `/api/departments` | Admin | Create department |
| PUT | `/api/departments/{id}` | Admin | Update department |
| DELETE | `/api/departments/{id}` | Admin | Delete department |

### Doctor Catalog Endpoints

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/doctors` | Yes | List doctors (filter by `departmentId`, `specialization`) |
| GET | `/api/doctors/{id}` | Yes | Get doctor by catalog ID |
| POST | `/api/doctors` | Admin | Create doctor catalog entry |
| PUT | `/api/doctors/{id}` | Admin | Update doctor catalog entry |
| DELETE | `/api/doctors/{id}` | Admin | Delete doctor catalog entry |
| PUT | `/api/doctors/me/availability` | Doctor | Toggle own availability |

---

## Frontend Pages

| Page | Route | Role |
|------|-------|------|
| Login | `/login` | Public |
| Signup | `/signup` | Public |
| Patient Dashboard | `/patient/*` | Patient |
| Browse Doctors | `/patient/doctors` | Patient |
| Doctor Dashboard | `/doctor/*` | Doctor |
| Admin Dashboard | `/admin/*` | Admin |
| Manage Departments | `/admin/departments` | Admin |
| Manage Doctors | `/admin/doctors` | Admin |
| Profile | `/profile` | All |

---

## Git Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production — always deployable |
| `develop` | Daily integration — merge PRs here |
| `feature/*` | One branch per day's checklist items (e.g., `feature/doctor-service`) |

---

## License

This project is built for educational purposes as part of the TrainingMug ADF v1.0 framework.
