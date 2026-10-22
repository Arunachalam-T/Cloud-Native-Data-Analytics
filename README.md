# ⬡ Cloud-Native Data Analytics Terminal

A full-stack desktop data analytics platform for uploading, analyzing, visualizing, and managing datasets — backed by Spring Boot, PostgreSQL, and AWS S3, with a JavaFX desktop interface.

---

## 🏗 Architecture

```
┌────────────────────────────────────────────────────────────┐
│  JavaFX Desktop UI  (Scene Builder FXML + dark theme CSS)  │
│  ▸ Dataset Manager  ▸ EDA View  ▸ Charts  ▸ Quality Report │
└────────────────────────┬───────────────────────────────────┘
                         │ HTTP REST (Apache HttpClient 5)
                         │ Basic Auth
┌────────────────────────▼───────────────────────────────────┐
│  Spring Boot Backend (Java 21)                             │
│  ▸ DatasetService   ▸ AnalyticsService                     │
│  ▸ DataQualityService  ▸ VisualizationService              │
│  ▸ S3Service        ▸ Spring Security                      │
└────────────┬──────────────────────┬────────────────────────┘
             │ JPA / Hibernate       │ AWS SDK
┌────────────▼─────────┐  ┌─────────▼────────────────────────┐
│ PostgreSQL (via RDS) │  │ AWS S3 (file storage)            │
│ Flyway migrations    │  │ Pre-signed URL downloads          │
└──────────────────────┘  └──────────────────────────────────┘
```

---

## 🛠 Tech Stack

| Layer       | Technology |
|-------------|-----------|
| Language    | Java 21 |
| Desktop UI  | JavaFX 21, Scene Builder (FXML) |
| Backend     | Spring Boot 3.2, REST API, Maven |
| Analytics   | Tablesaw 0.43 |
| Database    | PostgreSQL 16, Spring Data JPA, Hibernate, Flyway |
| Cloud       | AWS S3, AWS RDS, AWS SDK for Java, AWS IAM |
| Testing     | JUnit 5, Mockito, Spring MockMvc, JaCoCo |
| DevOps      | Docker, Docker Compose, GitHub Actions (CI + CD) |
| Security    | Spring Security (HTTP Basic), Environment Variables |

---

## 📂 Project Structure

```
cloud-native-data-analytics/
├── backend/                      # Spring Boot application
│   ├── src/main/java/com/dataanalytics/
│   │   ├── DataAnalyticsApplication.java
│   │   ├── config/               # Security, AWS, CORS
│   │   ├── controller/           # REST controllers
│   │   ├── service/              # Business logic + analytics
│   │   ├── repository/           # Spring Data JPA repos
│   │   ├── model/entity/         # JPA entities
│   │   ├── model/dto/            # Request/Response DTOs
│   │   └── exception/            # Global exception handler
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/         # V1, V2, V3 Flyway scripts
│   └── pom.xml
├── frontend/                     # JavaFX desktop application
│   ├── src/main/java/com/dataanalytics/ui/
│   │   ├── MainApp.java
│   │   ├── controller/           # MainViewController, DatasetDetailController
│   │   └── service/              # ApiClient (HTTP)
│   ├── src/main/resources/
│   │   ├── fxml/                 # MainView.fxml, DatasetDetailView.fxml
│   │   └── css/                  # dark-theme.css
│   └── pom.xml
├── docker/
│   └── Dockerfile.backend
├── docker-compose.yml
├── .github/workflows/
│   ├── ci.yml                    # Build + Test + Coverage
│   └── deploy.yml                # ECR push + EC2 deploy
├── .env.example
├── .gitignore
└── README.md
```

---

## 🚀 Quick Start (Zero Local DB / Docker Setup Needed!)

This project is pre-configured with a **serverless Neon cloud PostgreSQL database** and a **local storage fallback** for uploads. You do **not** need Docker, local PostgreSQL, or active AWS credentials to run and test everything immediately.

### Prerequisites
- **Java 21 JDK** (e.g. Eclipse Adoptium Temurin 21)
- **Apache Maven 3.9+**

---

### Option A: 1-Click Launch (Windows)

1. Double-click `run-backend.bat` (or run `.\run-backend.bat` in terminal). Wait until you see `Started DataAnalyticsApplication`.
2. Double-click `run-frontend.bat` (or run `.\run-frontend.bat` in terminal). The JavaFX desktop window will appear.
3. In the UI, click **"Upload Dataset"**, choose the provided `sample-data/sales_analytics_sample.csv`, and explore the instant EDA statistics, charts, and quality reports!

---

### Option B: Command Line (Cross-Platform)

**1. Start the Backend:**
```bash
cd backend
mvn spring-boot:run
# Backend is ready at http://localhost:8080
# Health check: http://localhost:8080/actuator/health
```

**2. Start the Frontend:**
```bash
cd frontend
mvn javafx:run
```

---

### Option C: Run Directly in VS Code or IntelliJ

- **Backend:** Open `backend/src/main/java/com/dataanalytics/DataAnalyticsApplication.java` and click **Run**.
- **Frontend:** Open `frontend/src/main/java/com/dataanalytics/ui/Launcher.java` and click **Run**.
- A predefined `.vscode/launch.json` is also included with ready-to-use launch configurations: **"Run Backend (Spring Boot)"** and **"Run Frontend (JavaFX UI)"**.

---

### Option D: Docker (Optional for Cloud/Container deployments)
```bash
docker-compose up --build
```

---

## 📡 REST API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/datasets/upload` | Upload CSV/Excel/TSV file |
| `GET`  | `/api/v1/datasets` | List all datasets (paginated) |
| `GET`  | `/api/v1/datasets/{id}` | Get dataset with column metadata |
| `DELETE` | `/api/v1/datasets/{id}` | Soft-delete dataset + S3 cleanup |
| `GET`  | `/api/v1/datasets/{id}/preview` | First N rows preview |
| `GET`  | `/api/v1/datasets/{id}/eda` | Full EDA report (stats, correlations) |
| `GET`  | `/api/v1/datasets/{id}/quality` | Data quality report with scoring |
| `GET`  | `/api/v1/datasets/{id}/visualizations` | Chart data (histogram, scatter, box, bar) |
| `GET`  | `/api/v1/datasets/{id}/status` | Processing status check |
| `GET`  | `/actuator/health` | Health check (public) |

All `/api/**` endpoints require **HTTP Basic Auth** (`admin` / `<API_KEY>`).

---

## 🗄 Database Schema (Flyway)

| Migration | Description |
|-----------|-------------|
| `V1` | `datasets` table — metadata, S3 key, status |
| `V2` | `column_metadata` table — per-column stats |
| `V3` | `analysis_results` table — cached JSONB results |

---

## 🧪 Testing

```bash
cd backend

# Run all tests
mvn test

# Run tests + generate JaCoCo coverage report
mvn verify

# View coverage report
open target/site/jacoco/index.html
```

---

## 🔒 GitHub Actions Secrets Required

For the CD deploy workflow, add these secrets to your GitHub repository:

| Secret | Description |
|--------|-------------|
| `AWS_ACCESS_KEY_ID` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key |
| `DB_URL` | Production PostgreSQL JDBC URL |
| `DB_USERNAME` | DB username |
| `DB_PASSWORD` | DB password |
| `AWS_S3_BUCKET` | S3 bucket name |
| `API_KEY` | Backend API key |
| `EC2_HOST` | EC2 public IP / hostname |
| `EC2_USER` | EC2 SSH username (e.g., `ec2-user`) |
| `EC2_SSH_KEY` | EC2 private key (PEM content) |

---

## 📊 JavaFX UI Features

| Screen | Features |
|--------|----------|
| **Dashboard** | Dataset list table with status, rows, columns, format |
| **Upload** | File picker (CSV, Excel, TSV), async upload with progress |
| **Dataset Detail** | Tabbed view with Preview, EDA, Quality, Charts |
| **EDA Report** | Per-column stats: min/max/mean/median/stddev/quartiles/outliers/value counts |
| **Quality Report** | Per-column quality score (0–100), warnings & recommendations |
| **Visualizations** | Auto-generated histograms, bar charts, scatter plots, box plot data |

---

## ☁ AWS IAM Permissions Required

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject", "s3:GetObject", "s3:DeleteObject",
        "s3:ListBucket", "s3:GeneratePresignedUrl"
      ],
      "Resource": [
        "arn:aws:s3:::your-bucket-name",
        "arn:aws:s3:::your-bucket-name/*"
      ]
    }
  ]
}
```

---

## 📄 License

MIT — see [LICENSE](LICENSE) for details.
