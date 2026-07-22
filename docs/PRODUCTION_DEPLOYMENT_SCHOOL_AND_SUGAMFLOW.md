# SugamFlow + School ERP Production Deployment Runbook

This runbook deploys:

1. **SugamFlow platform and shop backend** from `D:\sugamFlow`
2. **Shop Management Angular UI** from `D:\sugamFlow\shop-management-ui`
3. **School ERP backend services** from `D:\school\services`
4. **School ERP Angular UI** from `D:\school\apps\school-ui`

The recommended production target is **AWS EC2/ECS + RDS PostgreSQL + HTTPS**, with all APIs entering through one Spring Cloud Gateway.

> Local PowerShell start scripts are development tools. Do not use `start-platform.ps1`, `start-services.ps1`, `ng serve`, MailHog, default passwords, or localhost integration URLs as the production runtime.

---

## 1. Recommended production topology

```text
Users
  |
  +-- https://app.sugamflow.com -------- Shop Management Angular UI
  |
  +-- https://school.sugamflow.com ----- School ERP Angular UI
  |
  +-- https://api.sugamflow.com -------- Public API (optional direct API host)
                                                  |
                                            ALB / Nginx :443
                                                  |
                                      gateway-service :9090
                                                  |
                         private VPC / container network / service discovery
                          |                       |                       |
                 platform services        school services             Redis
                 :8080–8098              :8181–8199
                          \                       /
                           \                     /
                            AWS RDS PostgreSQL :5432
```

### Public exposure

Only expose:

- HTTPS for `app.sugamflow.com`
- HTTPS for `school.sugamflow.com`
- HTTPS for `api.sugamflow.com` if clients need a separate API hostname
- SSH only from an approved administrator IP or through AWS Systems Manager

Do **not** expose:

- Eureka (`8761`)
- Config Server (`8888`)
- PostgreSQL (`5432`)
- Redis (`6379`)
- Individual microservice ports (`8080–8098`, `8181–8199`)

---

## 2. Domain and TLS plan

| Domain | Purpose |
|---|---|
| `app.sugamflow.com` | Shop Management UI |
| `school.sugamflow.com` | School ERP Admin, Parent and Teacher UI |
| `api.sugamflow.com` | Spring Cloud Gateway |

Use:

- Route 53 for DNS
- AWS Certificate Manager for ALB/CloudFront, or Certbot for Nginx on EC2
- HTTPS redirect for all HTTP requests
- A wildcard certificate for `*.sugamflow.com` where appropriate

### Preferred frontend API routing

Keep both Angular applications on same-origin `/api` requests:

```text
https://app.sugamflow.com/api/*    -> gateway-service:9090
https://school.sugamflow.com/api/* -> gateway-service:9090
```

This can be implemented with:

- Nginx `location /api/`, or
- CloudFront behavior `/api/*` with the ALB as a second origin

Same-origin routing avoids embedding backend hosts in browser bundles and simplifies CORS.

---

## 3. Production prerequisites

### Infrastructure

- Linux EC2 instance or ECS cluster
- Recommended RAM:
  - **16 GiB minimum** for the full platform plus all 19 School services on one host
  - Prefer splitting platform and School workloads or using ECS for scaling
- RDS PostgreSQL 16/17 in private subnets
- S3/CloudFront or Nginx for Angular static files
- ECR/Docker Hub for immutable backend images
- Secrets Manager or SSM Parameter Store
- CloudWatch and/or Prometheus/Grafana for monitoring

### Build tools

- Java 17
- Maven 3.9+
- Node.js `^18.19` or `^20` for Shop UI; Node 20 recommended for School UI CI parity
- npm 9+
- Docker with Compose v2
- AWS CLI v2 when using AWS

### Secrets

Generate new production-only values:

```bash
openssl rand -base64 48   # SECURITY_JWT_SECRET
openssl rand -base64 48   # SECURITY_INVITE_INTERNAL_KEY
openssl rand -base64 48   # SHOP_ADMIN_API_KEY
```

Never commit `.env.production`, database passwords, SMTP credentials, Razorpay secrets, or JWT keys.

---

## 4. Current repository production-readiness gaps

Resolve these before the first production release.

### 4.1 School UI production environment is missing

`D:\school\apps\school-ui` currently has only:

```text
src/app/environments/environment.ts
```

It points to `http://localhost:9090`. Add:

`src/app/environments/environment.prod.ts`

```typescript
export const environment = {
  production: true,
  apiBaseUrl: '',
};
```

Then add a production file replacement in `angular.json`:

```json
"production": {
  "fileReplacements": [
    {
      "replace": "src/app/environments/environment.ts",
      "with": "src/app/environments/environment.prod.ts"
    }
  ],
  "budgets": [
    { "type": "initial", "maximumWarning": "1mb", "maximumError": "2mb" }
  ],
  "outputHashing": "all"
}
```

An empty `apiBaseUrl` makes School UI call same-origin `/api`.

### 4.2 School services are not containerized yet

`D:\school\services` has a Maven multi-module build, but currently no service Dockerfiles and no production Compose/ECS definitions.

Use a generic School service Dockerfile:

`D:\school\Dockerfile.school-service`

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /source
COPY services ./services
RUN mvn -f services/pom.xml -pl "${MODULE}" -am -DskipTests package \
 && JAR="$(find services/${MODULE}/target -maxdepth 1 -name "${MODULE}-*.jar" ! -name "*original*" | sort | tail -n 1)" \
 && test -n "$JAR" \
 && cp "$JAR" /tmp/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 school
COPY --from=build /tmp/app.jar /app/app.jar
USER school
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Build each module with its own image tag:

```powershell
cd D:\school
$Tag = "1.0.0"
$Modules = @(
  "school-settings-service","subscription-service","form-builder-service",
  "workflow-service","rule-engine-service","report-builder-service",
  "school-notification-config-service","audit-service","admission-service",
  "fee-service","student-service","attendance-service","exam-service",
  "library-service","hostel-service","transport-service","payroll-service",
  "staff-service","academic-structure-service"
)

foreach ($Module in $Modules) {
  docker build `
    -f Dockerfile.school-service `
    --build-arg "MODULE=$Module" `
    -t "YOUR_REGISTRY/sugamflow/$Module:$Tag" .
}
```

For long-term use, add these images as ECS task definitions or to a production Compose override. Pin a release tag; never deploy `latest`.

### 4.3 School DB username env key mismatch

`D:\school\infra\postgres\connection.env.example` uses:

```text
SCHOOL_*_DB_USER
```

Spring Boot school services expect:

```text
SCHOOL_*_DB_USERNAME
```

If you copy that example file into production without renaming, credentials will not bind. Always set `*_DB_USERNAME` and `*_DB_PASSWORD` for every school database.

### 4.4 Ledger is missing from the current EC2 Compose stack

`ledger-service` exists in source, local Compose, and gateway routing (`/api/v1/ledger/**`), but it is **not** defined in:

- `D:\sugamFlow\docker-compose.ec2-rds.yml`
- `D:\sugamFlow\.env.production.example`

If the client needs ledger, add the service image, `ledgerdb`, and `GATEWAY_LEDGER_URI` before go-live. Otherwise treat ledger as out of scope for that release.

### 4.5 Config Server is currently empty in Compose

Both local and EC2 Compose set:

```text
SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/tmp
```

No config files are mounted into `/tmp`, and no `config-repo` is packaged. This still works today because school and platform imports use `optional:configserver:` and real settings come from `application*.properties` plus environment variables.

For production, either:

- keep Config Server optional and continue env-based configuration, or
- mount a real config volume / bake a config repo before depending on it.

### 4.6 Do not use `start-prod.sh` as the production entrypoint

`D:\sugamFlow\start-prod.sh` is stale relative to the current EC2/RDS stack. Use:

```bash
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production ...
```

as documented in `D:\sugamFlow\docs\production-deploy-checklist.md`.

### 4.7 Shared tenancy model (School depends on Shop + Auth)

School ERP is not a separate identity platform:

- A school organization is a Shop record with `businessType=SCHOOL`
- Login and invitations come from `auth-service`
- Gateway derives tenant/shop context from the JWT and injects `X-Gateway-Verified`
- School services reject direct calls when `school.security.require-gateway-verified=true`
- Email delivery uses `notification-service` on port `8087`

Production therefore always deploys the shared platform first, then School services, then both Angular UIs.

---

## 5. Database preparation

### 5.1 Platform databases

At minimum the current SugamFlow platform uses:

- `authdb`
- `userdb`
- `shopdb`
- `productdb`
- `stockdb`
- `orderdb`
- `paymentdb`
- `notificationdb`
- `reportingdb`
- `accountdb`

Optional modules add:

- `fieldforcedb`
- `gstdb`
- `doctordb`
- `appointmentdb`
- `queuedb`
- `ledgerdb` (required only if ledger-service is deployed; currently absent from EC2 Compose)

Use the SQL under:

```text
D:\sugamFlow\infra\postgres
```

### 5.2 School ERP databases

Create the following databases and separate least-privilege users:

| Service | Database | URL environment variable |
|---|---|---|
| School Settings | `school_settings_db` | `SCHOOL_SETTINGS_DB_URL` |
| Subscription | `school_subscription_db` | `SCHOOL_SUBSCRIPTION_DB_URL` |
| Form Builder | `school_forms_db` | `SCHOOL_FORMS_DB_URL` |
| Workflow | `school_workflow_db` | `SCHOOL_WORKFLOW_DB_URL` |
| Rule Engine | `school_rules_db` | `SCHOOL_RULES_DB_URL` |
| Report Builder | `school_reports_db` | `SCHOOL_REPORTS_DB_URL` |
| Notification Config | `school_notif_cfg_db` | `SCHOOL_NOTIF_CFG_DB_URL` |
| Audit | `school_audit_db` | `SCHOOL_AUDIT_DB_URL` |
| Admission | `school_admission_db` | `SCHOOL_ADMISSION_DB_URL` |
| Fee | `school_fee_db` | `SCHOOL_FEE_DB_URL` |
| Student | `school_student_db` | `SCHOOL_STUDENT_DB_URL` |
| Attendance | `school_attendance_db` | `SCHOOL_ATTENDANCE_DB_URL` |
| Exam / LMS | `school_exam_db` | `SCHOOL_EXAM_DB_URL` |
| Library | `school_library_db` | `SCHOOL_LIBRARY_DB_URL` |
| Hostel | `school_hostel_db` | `SCHOOL_HOSTEL_DB_URL` |
| Transport | `school_transport_db` | `SCHOOL_TRANSPORT_DB_URL` |
| Payroll | `school_payroll_db` | `SCHOOL_PAYROLL_DB_URL` |
| Staff | `school_staff_db` | `SCHOOL_STAFF_DB_URL` |
| Academic | `school_academic_db` | `SCHOOL_ACADEMIC_DB_URL` |

Reference SQL:

```text
D:\school\infra\postgres\01-create-school-databases.sql
```

Do not use the default passwords from the local SQL in production.

Example production URL:

```text
jdbc:postgresql://RDS_PRIVATE_HOST:5432/school_student_db?sslmode=require
```

### Flyway

All School services run Flyway automatically from `classpath:db/migration`.

Before release:

1. Take an RDS snapshot.
2. Deploy one instance of each changed service.
3. Watch logs for Flyway validation/migration failures.
4. Do not manually edit `flyway_schema_history_*`.
5. Never run two incompatible application versions against one database.

---

## 6. Service inventory and private ports

### 6.1 Shared SugamFlow platform

| Service | Port |
|---|---:|
| Config Server | 8888 |
| Eureka Discovery | 8761 |
| API Gateway | 9090 |
| Shop | 8080 |
| Product | 8081 |
| Stock | 8082 |
| Order | 8083 |
| User | 8084 |
| Auth | 8085 |
| Payment | 8086 |
| Notification Delivery | 8087 |
| Reporting | 8088 |
| Account | 8089 |
| Fieldforce (optional) | 8090 |
| GST (optional) | 8091 |
| Doctor (optional) | 8092 |
| Appointment (optional) | 8093 |
| Ledger (optional; not in EC2 Compose yet) | 8094 |
| Queue (optional) | 8098 |

Authoritative production Compose for platform services:

```text
D:\sugamFlow\docker-compose.ec2-rds.yml
```

Only `gateway-service` publishes a host port there. Redis, Eureka, Config Server, and business services stay on the private Compose network.

### 6.2 School ERP

| Service | Port |
|---|---:|
| school-settings-service | 8181 |
| subscription-service | 8182 |
| form-builder-service | 8183 |
| workflow-service | 8184 |
| rule-engine-service | 8185 |
| report-builder-service | 8186 |
| school-notification-config-service | 8187 |
| audit-service | 8188 |
| admission-service | 8189 |
| fee-service | 8190 |
| student-service | 8191 |
| attendance-service | 8192 |
| exam-service | 8193 |
| library-service | 8194 |
| hostel-service | 8195 |
| transport-service | 8196 |
| payroll-service | 8197 |
| staff-service | 8198 |
| academic-structure-service | 8199 |

---

## 7. Production environment configuration

### 7.1 Common backend values

```dotenv
SPRING_PROFILES_ACTIVE=prod
SPRING_CLOUD_CONFIG_URI=http://config-service:8888
EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-service:8761/eureka
EUREKA_INSTANCE_PREFER_IP_ADDRESS=true

SECURITY_JWT_ENFORCE=true
SECURITY_JWT_SECRET=<from-secrets-manager>
SECURITY_INVITE_INTERNAL_KEY=<from-secrets-manager>
SECURITY_INTERNAL_API_KEY=<from-secrets-manager>
SHOP_ADMIN_API_KEY=<from-secrets-manager>

DB_POOL_MAX_SIZE=3
DB_POOL_MIN_IDLE=1
JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Kolkata -Xms128m -Xmx256m -XX:+ExitOnOutOfMemoryError
```

The same JWT secret must be used by Auth, Gateway, and services that validate the token.

### 7.2 Gateway routes for School services

The gateway already defines `/api/config`, `/api/subscription`, `/api/forms`, `/api/workflows`, `/api/rules`, `/api/reports`, and domain routes through `/api/academic`.

For container production, set direct internal URIs:

```dotenv
GATEWAY_SCHOOL_SETTINGS_URI=http://school-settings-service:8181
GATEWAY_SCHOOL_SUBSCRIPTION_URI=http://subscription-service:8182
GATEWAY_SCHOOL_FORMS_URI=http://form-builder-service:8183
GATEWAY_SCHOOL_WORKFLOW_URI=http://workflow-service:8184
GATEWAY_SCHOOL_RULES_URI=http://rule-engine-service:8185
GATEWAY_SCHOOL_REPORTS_URI=http://report-builder-service:8186
GATEWAY_SCHOOL_NOTIF_CONFIG_URI=http://school-notification-config-service:8187
GATEWAY_SCHOOL_AUDIT_URI=http://audit-service:8188
GATEWAY_SCHOOL_ADMISSION_URI=http://admission-service:8189
GATEWAY_SCHOOL_FEE_URI=http://fee-service:8190
GATEWAY_SCHOOL_STUDENT_URI=http://student-service:8191
GATEWAY_SCHOOL_ATTENDANCE_URI=http://attendance-service:8192
GATEWAY_SCHOOL_EXAM_URI=http://exam-service:8193
GATEWAY_SCHOOL_LIBRARY_URI=http://library-service:8194
GATEWAY_SCHOOL_HOSTEL_URI=http://hostel-service:8195
GATEWAY_SCHOOL_TRANSPORT_URI=http://transport-service:8196
GATEWAY_SCHOOL_PAYROLL_URI=http://payroll-service:8197
GATEWAY_SCHOOL_STAFF_URI=http://staff-service:8198
GATEWAY_SCHOOL_ACADEMIC_URI=http://academic-structure-service:8199
```

Do not use environment keys beginning with:

```text
SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_
```

They can override and break the indexed gateway routes.

### 7.3 Browser CORS

For direct `api.sugamflow.com` calls:

```dotenv
GATEWAY_CORS_ALLOWED_ORIGIN_PATTERN=https://*.sugamflow.com
```

Use an explicit allow-list if your gateway profile supports multiple entries. Never use `*` together with credentialed browser requests.

### 7.4 School service-to-service URLs

The local defaults use `localhost`; override them inside containers.

Core internal endpoints:

```dotenv
SETTINGS_AUDIT_URL=http://audit-service:8188
SETTINGS_SUBSCRIPTION_URL=http://subscription-service:8182
SETTINGS_PUBLIC_API_URL=https://school.sugamflow.com

SCHOOL_STUDENT_BASE_URL=http://student-service:8191
NOTIFICATION_DELIVERY_BASE_URL=http://notification-service:8087

STUDENT_FORMS_URL=http://form-builder-service:8183
STUDENT_SUBSCRIPTION_URL=http://subscription-service:8182
STUDENT_SETTINGS_URL=http://school-settings-service:8181
STUDENT_REPORTS_URL=http://report-builder-service:8186
STUDENT_RULES_URL=http://rule-engine-service:8185
STUDENT_FEE_URL=http://fee-service:8190
STUDENT_LIBRARY_URL=http://library-service:8194
STUDENT_HOSTEL_URL=http://hostel-service:8195
STUDENT_TRANSPORT_URL=http://transport-service:8196
STUDENT_ACADEMIC_URL=http://academic-structure-service:8199
STUDENT_ATTENDANCE_URL=http://attendance-service:8192
STUDENT_EXAM_URL=http://exam-service:8193

EXAM_STUDENT_URL=http://student-service:8191
EXAM_ACADEMIC_URL=http://academic-structure-service:8199
ATTENDANCE_STUDENT_URL=http://student-service:8191
ATTENDANCE_ACADEMIC_URL=http://academic-structure-service:8199
FEE_STUDENT_URL=http://student-service:8191
ADMISSION_STUDENT_URL=http://student-service:8191
```

For Admission, Fee, Attendance, Exam, Library, Hostel, Transport and Payroll, also map:

```text
*_FORMS_URL       -> http://form-builder-service:8183
*_WORKFLOWS_URL   -> http://workflow-service:8184
*_RULES_URL       -> http://rule-engine-service:8185
*_SUBSCRIPTION_URL-> http://subscription-service:8182
*_SETTINGS_URL    -> http://school-settings-service:8181
*_NOTIF_CFG_URL   -> http://school-notification-config-service:8187
*_REPORTS_URL     -> http://report-builder-service:8186
*_NOTIF_DELIVERY_URL -> http://notification-service:8087
```

### 7.5 Fee payment

Development defaults to simulated payments. Production must explicitly choose:

```dotenv
FEE_PAYMENT_MODE=razorpay
RAZORPAY_KEY_ID=<secret>
RAZORPAY_KEY_SECRET=<secret>
RAZORPAY_WEBHOOK_SECRET=<secret>
```

Register the public webhook URL at Razorpay and verify webhook signatures. Never expose the key secret in either Angular application.

### 7.6 Email

Configure a production provider such as AWS SES:

```dotenv
SPRING_MAIL_HOST=<smtp-host>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<secret>
SPRING_MAIL_PASSWORD=<secret>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
```

MailHog is local-only.

---

## 8. Build and publish the backend

Use one immutable release number across all artifacts:

```text
2026.07.19-1
```

### 8.1 SugamFlow images

```powershell
cd D:\sugamFlow

$env:IMAGE_PREFIX = "YOUR_REGISTRY/sugamflow"
$env:IMAGE_TAG = "2026.07.19-1"
$env:COMPOSE_ENV_FILES = ".env.local"

.\build-docker.ps1 -Parallel 1
docker compose --env-file .env.local push
```

Existing reference:

```text
D:\sugamFlow\docs\production-deploy-checklist.md
```

### 8.2 School images

Build using the generic Dockerfile from section 4.2, then push:

```powershell
$Tag = "2026.07.19-1"
foreach ($Module in $Modules) {
  docker push "YOUR_REGISTRY/sugamflow/$Module:$Tag"
}
```

### 8.3 Release evidence

Record:

- Git commit SHA for `D:\sugamFlow`
- Git commit SHA for `D:\school`
- Image digest for every backend image
- Angular artifact checksums
- Flyway migration versions
- Deployment operator and timestamp

---

## 9. Build both Angular applications

### 9.1 Shop Management UI

Production environment already uses same-origin APIs:

```powershell
cd D:\sugamFlow\shop-management-ui
npm ci
npm run build:prod
# equivalent:
# npm run build -- --configuration production
```

Output:

```text
D:\sugamFlow\shop-management-ui\dist\shop-management
```

Ensure `environment.prod.ts` contains:

```typescript
apiUrl: '',
apiGatewayUrl: '',
shopServiceUrl: '',
schoolUiBaseUrl: 'https://school.sugamflow.com'
```

Keep `shopAdminApiKey` empty in the browser build. Production depends on Nginx/CloudFront proxying same-origin `/api` to gateway `:9090`.

### 9.2 School ERP UI

After adding the production environment replacement from section 4.1:

```powershell
cd D:\school\apps\school-ui
npm ci
npm run build -- --configuration production
```

Output:

```text
D:\school\apps\school-ui\dist\school-ui\browser
```

Angular's application builder may place browser assets inside `browser`; confirm the folder containing `index.html` before upload.

### Frontend acceptance checks

- No `localhost:9090` string in production JavaScript bundles
- No admin API key in JavaScript bundles
- Hashed JS/CSS filenames are present
- `index.html` is not cached for a long duration
- Hashed assets can be cached for one year with `immutable`

---

## 10. Nginx deployment example

Use this when serving both UIs from one EC2 host.

```nginx
server {
    listen 80;
    server_name app.sugamflow.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name app.sugamflow.com;

    root /var/www/shop-management;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Request-Id $request_id;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    location ~* \.(?:js|css|woff2|png|jpg|jpeg|svg|webp)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }
}

server {
    listen 80;
    server_name school.sugamflow.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name school.sugamflow.com;

    root /var/www/school-ui;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Request-Id $request_id;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location = /index.html {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
    }

    location ~* \.(?:js|css|woff2|png|jpg|jpeg|svg|webp)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }
}
```

Add your certificate paths or use an ALB/CloudFront certificate instead.

Deploy static files:

```bash
sudo rsync -a --delete /home/ec2-user/releases/shop-management/ /var/www/shop-management/
sudo rsync -a --delete /home/ec2-user/releases/school-ui/ /var/www/school-ui/
sudo chown -R nginx:nginx /var/www/shop-management /var/www/school-ui
sudo nginx -t
sudo systemctl reload nginx
```

---

## 11. Backend deployment order

Use health checks between each phase.

### Phase 1 — infrastructure

1. RDS
2. Redis
3. Config Server
4. Eureka Discovery

### Phase 2 — shared platform

1. Shop
2. User
3. Auth
4. Notification
5. Product / Stock / Order / Payment / Reporting / Account
6. Optional platform modules

### Phase 3 — School foundation

1. School Settings
2. School Subscription
3. Form Builder
4. Workflow
5. Rule Engine
6. Report Builder
7. School Notification Config
8. Audit

### Phase 4 — School domains

1. Academic Structure
2. Staff
3. Student
4. Admission
5. Fee
6. Attendance
7. Exam / LMS
8. Library
9. Hostel
10. Transport
11. Payroll

### Phase 5 — Gateway

Deploy/recreate Gateway after all route targets are healthy.

### Phase 6 — Angular UIs

Deploy Shop Management UI and School ERP UI only after gateway smoke tests pass.

---

## 12. Health and smoke testing

### Internal checks

```bash
curl -fsS http://127.0.0.1:9090/actuator/health
curl -fsS http://discovery-service:8761/actuator/health
curl -fsS http://school-settings-service:8181/actuator/health
curl -fsS http://student-service:8191/actuator/health
```

Expected result:

```json
{"status":"UP"}
```

All School services expose:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/prometheus`

Do not publish detailed Actuator endpoints to the internet.

### Public checks

```bash
curl -I https://app.sugamflow.com
curl -I https://school.sugamflow.com
curl -fsS https://api.sugamflow.com/actuator/health
```

### Functional smoke

#### Shop Management

- Login
- Shop selection
- Product list
- Stock list
- Create/read one order
- Payment/report flow as enabled

#### School ERP

- Login through platform Auth
- Open dashboard
- Open Student Directory
- Admission create/read
- Fee collection read
- Attendance list
- Exam / homework list
- Library list
- Hostel list
- Transport list
- Parent portal bootstrap
- Teacher portal bootstrap

Use the scripts in:

```text
D:\school\scripts
```

Against production only with a dedicated test tenant and test credentials. Do not run destructive seed scripts against a live tenant.

---

## 13. Monitoring and alerting

Collect:

- Gateway request rate, latency, 4xx and 5xx
- JVM heap, CPU, GC and thread count
- Hikari active/pending connections
- RDS CPU, storage, connections and replication lag
- Redis memory and evictions
- Eureka registered instance count
- Flyway startup failures
- Notification delivery failures
- Fee webhook signature failures
- Disk usage and container restart count

Recommended alerts:

- Gateway health down for 2 minutes
- 5xx rate above 2%
- p95 API latency above 2 seconds
- Any service restart loop
- RDS storage below 20%
- Database connections above 80%
- Failed backups or snapshots

Use structured logs with request IDs. The gateway already propagates `X-Request-Id`.

---

## 14. Backup and restore

### RDS

- Enable automated daily backups
- Keep at least 7–30 days according to policy
- Enable point-in-time recovery
- Create a manual snapshot before every migration release
- Test restore into a non-production RDS instance quarterly

### Static files

- Keep each Angular release under a versioned S3 prefix or release directory
- Do not overwrite the only copy of the previous UI

### Configuration and secrets

- Version non-secret infrastructure/configuration in Git
- Back up Secrets Manager/SSM definitions through infrastructure-as-code
- Rotate JWT/admin/internal keys through a planned maintenance procedure

---

## 15. Rollback procedure

### UI rollback

Point Nginx/CloudFront back to the previous versioned static artifact, then invalidate:

```bash
sudo rsync -a --delete /home/ec2-user/releases/previous/shop-management/ /var/www/shop-management/
sudo rsync -a --delete /home/ec2-user/releases/previous/school-ui/ /var/www/school-ui/
sudo nginx -t && sudo systemctl reload nginx
```

### Backend rollback

1. Stop rollout.
2. Restore previous immutable image tags.
3. Recreate only changed services.
4. Confirm health and smoke tests.

Example:

```bash
IMAGE_TAG=2026.07.18-2 docker compose \
  -f docker-compose.ec2-rds.yml \
  --env-file .env.production \
  up -d --force-recreate gateway-service auth-service shop-service
```

### Database warning

Application rollback is safe only when the previous version is compatible with the migrated schema.

For destructive/incompatible migrations:

- schedule maintenance,
- take a snapshot,
- use expand-and-contract migrations,
- restore RDS only as an incident decision because restore loses newer writes.

---

## 16. Security go-live checklist

- [ ] No default/demo passwords
- [ ] No local SQL passwords used in production
- [ ] No secret committed to either repository
- [ ] Strong JWT secret shared only where required
- [ ] `SECURITY_JWT_ENFORCE=true`
- [ ] Only Gateway is publicly reachable
- [ ] School services retain `school.security.require-gateway-verified=true`
- [ ] HTTPS-only cookies/tokens and secure headers reviewed
- [ ] CORS allows only deployed UI origins
- [ ] RDS is private and requires TLS
- [ ] S3 buckets are private behind CloudFront OAC
- [ ] WAF/rate limits enabled
- [ ] Razorpay webhook signature verification tested
- [ ] SMTP sender domain has SPF, DKIM and DMARC
- [ ] Admin/API keys are not embedded in Angular bundles
- [ ] Actuator details are private
- [ ] IAM roles follow least privilege

---

## 17. Release checklist

### Before deployment

- [ ] Both repositories are committed and tagged
- [ ] Unit/integration tests pass
- [ ] `mvn clean verify` passes for backend changes
- [ ] Both Angular production builds pass
- [ ] Production bundles contain no localhost URL
- [ ] All images exist with the release tag
- [ ] RDS snapshot completed
- [ ] Environment variables validated
- [ ] Migration impact reviewed
- [ ] Rollback version recorded

### During deployment

- [ ] Infrastructure services healthy
- [ ] Platform services healthy
- [ ] School foundation services healthy
- [ ] School domain services healthy
- [ ] Eureka shows expected registrations
- [ ] Gateway deployed last
- [ ] No Flyway error in logs
- [ ] Angular artifacts deployed atomically

### After deployment

- [ ] Both login pages load
- [ ] Shop login and one business workflow pass
- [ ] School Admin login passes
- [ ] Parent and Teacher portals load
- [ ] Admission, Fee, Attendance and LMS smoke pass
- [ ] Notification delivery test passes
- [ ] Monitoring dashboards show normal values
- [ ] Release evidence saved

---

## 18. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Angular calls `localhost:9090` | School production environment replacement missing | Complete section 4.1 and rebuild |
| Angular route returns 404 on refresh | SPA fallback missing | Nginx `try_files ... /index.html` or CloudFront 403/404 mapping |
| API returns 503 | Gateway target URI wrong or service unhealthy | Check `GATEWAY_*_URI`, DNS and readiness |
| School API returns 403 Gateway verification required | Calling service directly | Call through Gateway; do not expose service port |
| Service cannot reach another service | Localhost integration URL used in container | Set internal `*_URL=http://service-name:port` |
| Service cannot register in Eureka | Wrong default zone / network DNS | Set `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` |
| CORS error | UI origin not allowed or mixed host strategy | Use same-origin `/api` or correct allowed origins |
| Flyway validation fails | Changed old migration or wrong database | Restore original migration; add a new version |
| RDS connections exhausted | Pool too large across many services | Reduce `DB_POOL_MAX_SIZE`, inspect leaks |
| Container OOM/restarts | Heap/container limits too small | Increase host memory or split workloads |
| Email marked failed | SMTP/SES not configured | Check credentials, verified sender and network |
| Razorpay payments remain pending | Webhook URL/secret mismatch | Check public webhook, signature and logs |
| School DB auth fails after loading `connection.env.example` | Example uses `_DB_USER`, services expect `_DB_USERNAME` | Rename keys; see section 4.3 |
| `/api/v1/ledger/**` returns 503 | `ledger-service` not in EC2 Compose | Deploy ledger + `ledgerdb`, or remove ledger from release scope |
| Config values missing after Config Server start | Compose points at empty `/tmp` | Mount a real config repo or rely on env vars (section 4.5) |
| School login works but tenant APIs fail | Shop not created as `businessType=SCHOOL`, or gateway JWT headers missing | Verify school shop onboarding and gateway-verified path |

---

## 19. Recommended CI/CD stages

1. Checkout both repositories at pinned commits
2. Backend tests
3. Angular lint/build
4. Secret scan and dependency scan
5. Build immutable images
6. Push images to ECR
7. Deploy to staging
8. Run API and browser smoke tests
9. Manual production approval
10. RDS snapshot
11. Rolling production deployment
12. Post-deploy smoke and monitoring gate
13. Mark release successful or automatically rollback

Use separate AWS accounts or at least separate VPCs/databases/secrets for staging and production.

---

## 20. Final production decision

For an initial small pilot, EC2 + RDS + Nginx + immutable Docker images is acceptable.

For sustained client production:

- use ECS Fargate or EKS,
- run at least two Gateway instances across availability zones,
- use an ALB,
- use RDS Multi-AZ,
- serve Angular through S3/CloudFront,
- keep all microservices private,
- manage secrets outside Git,
- automate release, smoke testing and rollback.

