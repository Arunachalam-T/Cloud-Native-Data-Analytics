# ⬡ Cloud-Native Data Analytics Terminal — Architectural Concept & System Design

---

## 1. Executive Summary & Problem Statement

Modern data analysis in organizations faces two common extremes:
1. **Complex Big-Data Tooling:** Platforms like Databricks, Snowflake, or AWS EMR require deep cloud engineering knowledge, high maintenance costs, and extensive setup.
2. **Ad-Hoc Desktop Workflows:** Relying on Excel spreadsheets or Python/Jupyter scripts leads to data isolation, lack of auditability, manual chart generation, and local dependency headaches.

The **Cloud-Native Data Analytics Terminal** bridges this gap:
- A **lightweight desktop terminal (JavaFX)** providing an intuitive, interactive user interface.
- A **microservice backend (Spring Boot 3.2 + Java 21)** executing vectorized, in-memory exploratory data analysis (EDA).
- A **serverless cloud database (Neon PostgreSQL)** decoupled from client machines.
- An **automated data quality auditor** scoring datasets from 0 to 100 with automated anomaly detection.

---

## 2. Cloud-Native Design Principles

This application adheres to standard cloud-native and 12-factor architectural principles:

```
┌─────────────────────────────────────────────────────────────────┐
│                    JavaFX Desktop Client                        │
│   ▸ Scene Builder FXML + Dark CSS Design System                 │
│   ▸ Asynchronous UI Threading (javafx.concurrent.Task)          │
│   ▸ Interactive Native Charts (Bar, Histogram, Scatter, Pie)   │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP REST / Basic Auth
┌──────────────────────────────▼──────────────────────────────────┐
│                   Spring Boot 3.2 Backend                       │
│   ▸ Stateless Microservice Architecture                         │
│   ▸ Non-blocking Async Ingestion (CompletableFuture)            │
│   ▸ JDBC Insert Batching (50 records/batch)                     │
│   ▸ Tablesaw 0.43 Vectorized In-Memory Analytics Engine         │
│   ▸ Data Quality Scoring Engine & Outlier Detection             │
└──────────────┬───────────────────────────────────┬──────────────┘
               │ JPA / Hibernate                   │ S3 SDK / Fallback
┌──────────────▼────────────────────┐  ┌───────────▼──────────────┐
│  Neon Serverless PostgreSQL (Cloud)│  │ AWS S3 / Local Storage   │
│  ▸ Flyway Schema Migrations (V1-V3)│  │ ▸ Pre-signed URLs        │
│  ▸ Column Metadata & JSONB Results │  │ ▸ Raw Dataset Persistence│
└───────────────────────────────────┘  └──────────────────────────┘
```

### Key Principles:
1. **Stateless Backend Service:**
   The backend container holds no session state. Any container instance can handle any incoming request.
2. **Decoupled Serverless Cloud Database:**
   Neon PostgreSQL runs serverless compute in the cloud. Client machines need zero local database installation or Docker daemons to function.
3. **Automated Schema Evolution (Flyway):**
   Database DDL changes are tracked via Flyway migrations (`V1__datasets`, `V2__column_metadata`, `V3__analysis_results`), ensuring reliable versioning across environments.
4. **Resilient Hybrid Storage (Local Fallback + AWS S3):**
   When AWS credentials are present, files stream directly to AWS S3 with pre-signed download URLs. When running offline or in development, an automatic filesystem fallback (`uploads/`) activates seamlessly.

---

## 3. End-to-End Data Pipeline

```
[CSV / Excel / TSV File]
           │
           ▼
[1. Upload Controller] ──> Multipart streaming validation
           │
           ├─► Save to Storage (S3 / uploads/)
           ├─► Save Dataset record (Status: PROCESSING)
           └─► Return HTTP 200 OK immediately (< 1 second)
           │
           ▼ (Background Worker Thread)
[2. Tablesaw Engine] ──> Vectorized Columnar Parsing
           │
           ├─► Compute missing value bitsets
           ├─► Calculate unique value cardinality
           ├─► Batch-insert ColumnMetadata to PostgreSQL
           └─► Update Dataset status to READY
           │
           ▼
[3. Analytics & Visualization Engine]
           │
           ├─► Numerical Summaries (Mean, Std Dev, Quartiles, Skewness)
           ├─► Categorical Breakdown (Frequency distribution, Pie data)
           ├─► Pearson Correlation Matrix (Inter-variable relationships)
           └─► Data Quality Scoring (0-100 composite formula)
```

---

## 4. Key Algorithms & Scientific Methodology

### 1. Interquartile Range (IQR) Outlier Detection
For continuous numeric variables, the Tukey boxplot method detects anomalies:
$$\text{IQR} = Q_3 - Q_1$$
$$\text{Lower Fence} = Q_1 - 1.5 \times \text{IQR}$$
$$\text{Upper Fence} = Q_3 + 1.5 \times \text{IQR}$$
Values falling outside this range are flagged as statistical outliers.

### 2. Automated Data Quality Score (0–100)
A weighted heuristic calculates the dataset quality rating:
- **Completeness (40%):** Penalty proportional to overall missingness ($\% \text{ nulls}$).
- **Uniqueness (20%):** Penalty applied for duplicate row occurrences.
- **Validity & Outliers (20%):** Outlier density evaluation.
- **Consistency (20%):** Data type cardinality and format compliance.

---

## 5. Technology Stack Summary

| Layer | Component | Version | Purpose |
|---|---|---|---|
| **Frontend** | JavaFX | 21.0.3 | High-performance hardware-accelerated desktop UI |
| **Backend** | Spring Boot | 3.2.5 | Enterprise REST microservice framework |
| **Language** | Java LTS | 21 | Modern virtual-thread-ready LTS runtime |
| **Data Engine** | Tablesaw | 0.43.1 | Columnar in-memory DataFrame analysis |
| **Database** | Neon PostgreSQL | 16+ | Cloud serverless relational storage |
| **Database Migration** | Flyway | 9.22.3 | Version-controlled DDL migrations |
| **Object Storage** | AWS S3 / Local | SDK v2 | Binary file persistence |
