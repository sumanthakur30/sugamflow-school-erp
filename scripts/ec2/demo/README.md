# Demo on-demand (prefer `docker compose`)

Preferred: copy-paste `docker compose` below.  
Optional wrappers: `up-*.sh` / `down-demos.sh` / `status.sh` (same groups).

## Aliases (optional, once per SSH session)

```bash
SF='docker compose -f /opt/sugamflow/docker-compose.ec2-rds.yml --env-file /opt/sugamflow/.env.production'
SFIPD='docker compose -f /opt/sugamflow/docker-compose.ec2-rds.yml -f /opt/sugamflow/docker-compose.ec2-ipd.yml --env-file /opt/sugamflow/.env.production'
SCH='docker compose -f /opt/school/docker-compose.school.ec2-rds.yml --env-file /opt/school/.env.school.production'
```

---

## Always-on (keep up — retail / login)

```bash
cd /opt/sugamflow

docker compose -f docker-compose.ec2-rds.yml --env-file .env.production up -d \
  redis config-service discovery-service \
  auth-service shop-service user-service notification-service gateway-service \
  product-service stock-service order-service payment-service \
  reporting-service account-service gst-service ledger-service
```

---

## Clinic / OPD

```bash
cd /opt/sugamflow

# UP
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production up -d \
  doctor-service appointment-service queue-management-service

# STOP
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production stop \
  doctor-service appointment-service queue-management-service
```

---

## IPD (hospital)

```bash
cd /opt/sugamflow

# UP
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production up -d \
  doctor-service appointment-service queue-management-service
docker compose -f docker-compose.ec2-rds.yml -f docker-compose.ec2-ipd.yml --env-file .env.production up -d \
  ipd-service accommodation-service

# STOP
docker compose -f docker-compose.ec2-rds.yml -f docker-compose.ec2-ipd.yml --env-file .env.production stop \
  ipd-service accommodation-service
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production stop \
  doctor-service appointment-service queue-management-service
```

---

## Fieldforce

```bash
cd /opt/sugamflow

# UP
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production up -d fieldforce-service

# STOP
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production stop fieldforce-service
```

---

## School

```bash
cd /opt/school

# UP — Phase A
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production up -d \
  school-settings-service subscription-service form-builder-service workflow-service \
  academic-structure-service staff-service student-service admission-service \
  fee-service compliance-service support-service

# UP — Phase A + B
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  --profile phase-b up -d \
  school-settings-service subscription-service form-builder-service workflow-service \
  academic-structure-service staff-service student-service admission-service \
  fee-service compliance-service support-service \
  attendance-service exam-service payroll-service library-service \
  hostel-service transport-service school-notification-config-service

# UP — full (A + B + website/cms)
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  --profile phase-b --profile website up -d

# STOP all school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  --profile phase-b --profile website stop
```

---

## After demos (stop extras, keep retail)

```bash
# School
cd /opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  --profile phase-b --profile website stop

# Clinic + fieldforce + IPD
cd /opt/sugamflow
docker compose -f docker-compose.ec2-rds.yml -f docker-compose.ec2-ipd.yml --env-file .env.production stop \
  ipd-service accommodation-service
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production stop \
  doctor-service appointment-service queue-management-service fieldforce-service
```

---

## Status

```bash
docker ps -a --format '{{.Names}} {{.Status}}' | sort
```

Or: `bash /opt/school/scripts/ec2/demo/status.sh`
