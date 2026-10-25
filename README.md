# Pawly AI - Pet Emergency Triage System

AI-powered pet emergency triage using **Mistral AI** + **RAG** (Retrieval-Augmented Generation) with a veterinary knowledge base.

## Project Structure

```
pawly-ai/
├── pawly-backend/      # Spring Boot REST API (Java 17, port 8081)
├── pawly-frontend/     # Angular 17 SPA (port 4200)
└── ai-pawly/           # Python FastAPI AI service (port 8000)
```

## Tech Stack

| Layer | Tech |
|-------|------|
| Backend | Spring Boot 3, Spring Security, JWT, JPA |
| Frontend | Angular 17, standalone components |
| AI Service | FastAPI, Mistral AI API, RAG with MySQL |
| Database | MySQL 8 (`pawly_db`) |

---

## Prerequisites

- Java 17+
- Node.js 18+ and npm
- Python 3.10+
- MySQL 8 running locally
- A Mistral API key from https://console.mistral.ai

---

## Setup

### 1. Database

Create the database:
```sql
CREATE DATABASE IF NOT EXISTS pawly_db;
```

### 2. Backend (Spring Boot)

```bash
cd pawly-backend
./mvnw spring-boot:run
```
Runs on **http://localhost:8081**

> Configure DB credentials in `src/main/resources/application.properties` if needed.

### 3. AI Service (Python + Mistral)

```bash
cd ai-pawly
pip install -r requirements.txt
cp .env.example .env
```

Edit `.env` and add your Mistral API key:
```
MISTRAL_API_KEY=your_key_here
```

Then start:
```bash
python main.py
```
Runs on **http://localhost:8000**

On first start it auto-seeds the veterinary knowledge base into MySQL.
Check it: http://localhost:8000/health

### 4. Frontend (Angular)

```bash
cd pawly-frontend
npm install
npx ng serve
```
Open **http://localhost:4200**

---

## Service Dependency Chain

```
Angular (4200) → Spring Boot (8081) → ai-pawly Python (8000) → Mistral API
                                                              → MySQL (RAG)
```

All 3 services must be running at the same time.

---

## Key Features

- **AI Emergency Triage** — describe symptoms, get urgency level (CRITICAL / URGENT / MODERATE / MILD)
- **RAG Knowledge Base** — answers grounded in veterinary knowledge stored in MySQL
- **Pet Management** — add/manage pets with species-specific triage
- **Vet Assignment** — admin can assign vets to users
- **JWT Authentication** — secure login/register flow

---

## Integrating Your Module

The backend follows standard Spring Boot conventions:

1. Add your entity in `pawly-backend/src/main/java/com/pawly/entity/`
2. Add your repository in `.../repository/`
3. Add your service in `.../service/`
4. Add your controller in `.../controller/`
5. Security config is in `.../config/SecurityConfig.java` — add your public routes there

The frontend uses Angular standalone components under `pawly-frontend/src/app/features/`.
Add your route in `src/app/app.routes.ts` and your nav link in the shell component.

---

## Environment Variables

| File | Description |
|------|-------------|
| `ai-pawly/.env` | Mistral API key + DB config (not committed) |
| `ai-pawly/.env.example` | Template to copy |
| `pawly-backend/src/main/resources/application.properties` | Spring Boot DB + JWT config |

---

## API Endpoints

### Spring Boot (8081)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/register` | Register |
| GET | `/api/pets` | Get user pets |
| POST | `/api/triage` | Submit triage (delegates to ai-pawly) |

### AI Service (8000)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Service health + chunk count |
| POST | `/triage` | Run AI triage |
| POST | `/ingest/pdf` | Upload PDF to knowledge base |
| GET | `/chunks` | List knowledge chunks |