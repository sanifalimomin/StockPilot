# Inventory Management System (IMS) — Backend

Cloud-native Inventory Management System backend for CSCI 5411. A single Spring Boot
jar that runs in three roles (REST API, SQS worker, scheduled reorder task) selected by
configuration, designed for AWS Fargate/Graviton but runnable locally with **zero AWS**.

## Stack
- Java 21, Spring Boot 3.3.x, Gradle (with wrapper), Lombok
- Spring Data JPA + PostgreSQL (Flyway migrations); H2 in-memory for local/tests
- AWS SDK v2: DynamoDB (stock-movement ledger), SQS (movement events), SNS (alerts),
  S3 (reports), Bedrock (forecasting)
- Spring Data Redis + Spring Cache (consolidated-stock cache); no-op cache locally
- Actuator + Prometheus, structured JSON logging (logstash encoder)

## Architecture
The same jar plays three roles, chosen by `ims.role` = `API` | `WORKER` | `SCHEDULER` | `ALL`:
1. **REST API** — `/api/v1/**`
2. **SQS worker** — consumes movement events and applies them (`SqsMovementWorker`)
3. **Scheduled reorder task** — `ReorderScheduler` (cron `ims.reorder.cron`)

AWS-backed components sit behind ports with two implementations each, selected by
`ims.aws.enabled` / `ims.aws.sqs.enabled` / `ims.aws.sns.enabled`:

| Port                  | prod impl                      | local impl                    |
|-----------------------|--------------------------------|-------------------------------|
| `StockMovementLedger` | `DynamoDbStockMovementLedger`  | `InMemoryStockMovementLedger` |
| `MovementPublisher`   | `SqsMovementPublisher` (async) | `InlineMovementPublisher` (sync) |
| `Notifier`            | `SnsNotifier`                  | `LoggingNotifier`             |
| `ReportStore`         | `S3ReportStore`                | `LocalReportStore` (`./reports`) |
| `ForecastService`     | `BedrockForecastService`*      | `EwmaForecastService`         |

\* Bedrock impl falls back to EWMA automatically if Bedrock is unavailable.

When SQS is disabled (local), `POST /movements` is processed **synchronously** so the demo
can immediately read updated inventory.

## Run locally (no AWS)
Requires a JDK 21 to be installed. The Gradle build uses a **Java toolchain** pinned to 21,
so Gradle/`./gradlew` itself can run on any JDK — it locates and uses an installed JDK 21 to
compile and run. If Gradle can't find it, pass its path:
`-Dorg.gradle.java.installations.paths="C:\Program Files\Java\jdk-21"`.

```bash
cd app
./gradlew bootRun --args='--spring.profiles.active=local'
```

The `local` profile uses H2 in-memory DB (seeded by Flyway), in-memory ledger, no-op cache,
inline movement processing, and EWMA forecasting. A `CommandLineRunner` runs an initial
reorder scan on boot.

Try it:
```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/api/v1/products
curl "http://localhost:8080/api/v1/inventory/consolidated?sku=SKU-1001"
curl -X POST http://localhost:8080/api/v1/movements -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","warehouseId":1,"type":"OUTBOUND","qty":5,"idempotencyKey":"demo-1"}'
curl "http://localhost:8080/api/v1/alerts?resolved=false"
curl -X POST http://localhost:8080/api/v1/reports/valuation
curl "http://localhost:8080/api/v1/forecast?sku=SKU-1001&days=14"
```

H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:ims`).

## Build & test
```bash
./gradlew bootJar   # build the executable jar -> build/libs/inventory-management-system-1.0.0.jar
./gradlew test      # H2-based tests (no Docker required)
./gradlew build     # compile + test + assemble
```

## REST API (base `/api/v1`)
- `GET /health` -> `{status, role, profile}`
- `GET/POST /categories`, `GET/PUT/DELETE /categories/{id}`
- `GET/POST /suppliers`, `GET/PUT/DELETE /suppliers/{id}`
- `GET/POST /products` (`?categoryId=&supplierId=&q=`), `GET/PUT/DELETE /products/{id}`
- `GET/POST /warehouses`, `GET/PUT/DELETE /warehouses/{id}`
- `GET /inventory?warehouseId=&sku=`
- `GET /inventory/consolidated?sku=` (Redis-cached, invalidated on movement)
- `POST /movements`, `GET /movements?sku=&warehouseId=&limit=`
- `GET/POST /purchase-orders`, `GET /purchase-orders/{id}`,
  `POST /purchase-orders/{id}/transition` (`{status}`; on `RECEIVED` increments inventory)
- `GET /alerts?resolved=false`
- `POST /reports/valuation` -> `{reportId, location}`; `GET /reports`
- `GET /forecast?sku=&days=30`
- `POST /internal/reorder-scan` (manual trigger for demo)

Errors use a global handler returning `{timestamp,status,error,message,path}`.

## Key behaviors
- Movement processing is **idempotent** (dedupe by `idempotencyKey`).
- `OUTBOUND` cannot drive on-hand below zero; `TRANSFER` atomically moves stock between
  warehouses; `ADJUSTMENT` sets an absolute on-hand value.
- After any movement, if on-hand <= product `reorderPoint`, an unresolved `LowStockAlert`
  is created (deduped) and an SNS notification is published in prod; suggested PO qty =
  `reorderQty`.
- Scheduled reorder task scans all inventory and raises/resolves alerts.

## Environment variables (prod / default profile)
| Var | Purpose | Default |
|-----|---------|---------|
| `SPRING_PROFILES_ACTIVE` | profile | `prod` |
| `IMS_ROLE` | `API`/`WORKER`/`SCHEDULER`/`ALL` | `API` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Postgres | local pg |
| `REDIS_HOST` / `REDIS_PORT` | Redis | `localhost:6379` |
| `AWS_REGION` | AWS region | `us-east-1` |
| `DDB_TABLE` / `DDB_ENDPOINT` | DynamoDB ledger table / optional endpoint | `ims-stock-movements` |
| `SQS_ENABLED` / `SQS_QUEUE_URL` | SQS toggle / queue | `true` |
| `SNS_ENABLED` / `SNS_TOPIC_ARN` | SNS toggle / topic | `true` |
| `S3_BUCKET` | reports bucket | `ims-reports` |
| `FORECAST_PROVIDER` | `ewma` or `bedrock` | `ewma` |
| `BEDROCK_MODEL_ID` | Bedrock model | claude-3-haiku |
| `CORS_ORIGINS` | allowed origins | `http://localhost:5173` |
| `REORDER_CRON` | reorder schedule | `0 0 * * * *` |

The DynamoDB table (PK `movementId`) and SQS/SNS resources are provisioned via Terraform
(see project infra). For prod, IAM is supplied via the Fargate task role
(`DefaultCredentialsProvider`).

## Docker
Multi-stage build, ARM64/Graviton-friendly `eclipse-temurin:21-jre`, runs as non-root,
exposes 8080.
```bash
docker build -t ims-backend ./app
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=local ims-backend
```
