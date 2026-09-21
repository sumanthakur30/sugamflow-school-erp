# Local PC → EC2: all build, upload, and run commands

Single place for School ERP + SugamFlow platform deploy on your host  
(`ec2-user@sugamflow` / `ip-172-31-16-63`).

**Live paths on this server (confirmed):**

| Role | Path |
|------|------|
| School compose + env | `/opt/school` |
| School deploy scripts | `/opt/school/scripts/ec2` |
| SugamFlow platform | `/opt/sugamflow` |
| School Angular UI | `/var/www/school-ui` |
| Shop Angular UI | `/var/www/sugamflow-ui` |
| Empty / unused | `/home/ec2-user/opt/school` (ignore) |

Replace placeholders:

```text
EC2_HOST=your-public-ip-or-dns
KEY=C:\path\to\your-key.pem
IMAGE_TAG=1.0.3
IMAGE_PREFIX=sumanthakur30
```

---

## 0) SSH login

```bash
# From PC (PowerShell / Git Bash)
ssh -i "$KEY" ec2-user@$EC2_HOST

# On EC2
pwd
whoami
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}' | head
```

---

## 1) PC — build & push Docker images

### School services

```powershell
cd D:\school
docker login

# Phase A+B (student, notification-config, admission, fee, …)
.\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.3 -Phase b

# Everything (A+B+audit/rules/reports)
# .\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.3 -Phase all -NoCache

# Build only, no push
# .\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.3 -Phase b -SkipPush
```

### Platform (SugamFlow) — when gateway / auth / notification / account changed

```powershell
cd D:\sugamFlow
$env:IMAGE_TAG = '1.0.3'
$env:IMAGE_PREFIX = 'sumanthakur30'
.\scripts\sequences\build-by-sequence.ps1 -Push

# Or one service:
docker compose build notification-service account-service
docker compose push notification-service account-service
```

### School UI (static)

```powershell
cd D:\school\apps\school-ui
npm ci
npm run build
# Output: D:\school\apps\school-ui\dist\school-ui\browser\
```

### Preflight

```powershell
cd D:\school
.\scripts\check-prod-env.ps1
```

Set the **same** `IMAGE_TAG` in:

- `D:\school\.env.school.production` → later `/opt/school/.env.school.production`
- `D:\sugamFlow\.env.production` → later `/opt/sugamflow/.env.production` (if platform images bumped)

---

## 2) PC → EC2 file upload

### 2a) Compose + env + scripts (scp)

```powershell
$ec2 = "ec2-user@$EC2_HOST"
$key = $KEY

# School
ssh -i $key $ec2 "mkdir -p /opt/school/scripts/ec2"
scp -i $key D:\school\docker-compose.school.ec2-rds.yml "${ec2}:/opt/school/"
scp -i $key D:\school\.env.school.production "${ec2}:/opt/school/"
scp -i $key D:\school\scripts\ec2\*.sh "${ec2}:/opt/school/scripts/ec2/"

# Platform env (careful — secrets)
# scp -i $key D:\sugamFlow\.env.production "${ec2}:/opt/sugamflow/"
# scp -i $key D:\sugamFlow\docker-compose.ec2-rds.yml "${ec2}:/opt/sugamflow/"
```

### 2b) WinSCP mapping

| Local | Remote |
|-------|--------|
| `D:\school\docker-compose.school.ec2-rds.yml` | `/opt/school/` |
| `D:\school\.env.school.production` | `/opt/school/` |
| `D:\school\scripts\ec2\*.sh` | `/opt/school/scripts/ec2/` (create folder first) |
| `D:\sugamFlow\.env.production` | `/opt/sugamflow/` |
| `D:\sugamFlow\docker-compose.ec2-rds.yml` | `/opt/sugamflow/` |
| `D:\school\apps\school-ui\dist\school-ui\browser\*` | see §4 UI deploy |

**Do not** open `/opt/school/scripts/ec2` in WinSCP until the folder exists:

```bash
mkdir -p /opt/school/scripts/ec2
```

### 2c) Fix Windows CRLF on uploaded `.sh` (required)

```bash
cd /opt/school/scripts/ec2
sed -i 's/\r$//' *.sh
chmod +x *.sh
ls -la
```

Symptom if skipped: `set: pipefail` / `invalid option name`.

---

## 3) EC2 — Docker: School stack

### Check tag

```bash
cd /opt/school
grep -E '^IMAGE_TAG=|^IMAGE_PREFIX=' .env.school.production
```

### Full pull + recreate (all school services)

```bash
cd /opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production pull
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production up -d --force-recreate
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production ps
```

### Partial recreate (example: Comms / Father-Mother change)

```bash
cd /opt/school
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  pull student-service school-notification-config-service
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  up -d --force-recreate student-service school-notification-config-service
```

### Logs / health

```bash
docker logs --tail 80 school-student-service-1
docker logs --tail 80 school-school-notification-config-service-1
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9090/actuator/health
```

`401` on some `/api/.../health` via gateway with JWT enforce is normal without a token.

---

## 4) EC2 — School UI (`/var/www/school-ui`)

Files are owned by **`nginx`**. `ec2-user` WinSCP gets **Permission denied**.

### Recommended: upload to home, then sudo sync

**PC / WinSCP:** upload `dist\school-ui\browser\*` → `/home/ec2-user/school-ui-dist/`

**EC2:**

```bash
mkdir -p ~/school-ui-dist
# after WinSCP upload into ~/school-ui-dist/

sudo rsync -a --delete ~/school-ui-dist/ /var/www/school-ui/
sudo chown -R nginx:nginx /var/www/school-ui
sudo find /var/www/school-ui -type d -exec chmod 755 {} \;
sudo find /var/www/school-ui -type f -exec chmod 644 {} \;
sudo nginx -t && sudo systemctl reload nginx
```

### Alternate: temporary chown for direct WinSCP

```bash
sudo chown -R ec2-user:ec2-user /var/www/school-ui
# WinSCP sync to /var/www/school-ui/
sudo chown -R nginx:nginx /var/www/school-ui
sudo nginx -t && sudo systemctl reload nginx
```

---

## 5) EC2 — Platform (SugamFlow) Docker

```bash
# Confirm path
ls /opt/sugamflow/docker-compose.ec2-rds.yml
ls /opt/sugamflow/.env.production

cd /opt/sugamflow
grep -E '^IMAGE_TAG=|^NOTIFICATION_WHATSAPP|^TWILIO_WHATSAPP' .env.production

docker compose -f docker-compose.ec2-rds.yml --env-file .env.production pull
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production up -d

# Recreate notification only (Twilio / WhatsApp)
docker compose -f docker-compose.ec2-rds.yml --env-file .env.production \
  up -d --force-recreate notification-service
docker logs --tail 50 sumanthakur30-notification-service-1
# Expect: WhatsApp provider active=twilio
```

### Quote JAVA opts (avoids Flyway script / bash `source` break)

If you see `-Xms256m: command not found`, fix lines like:

```bash
cd /opt/sugamflow
nano .env.production
# Change to:
# JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata -Xms128m -Xmx256m -XX:+ExitOnOutOfMemoryError"
# GATEWAY_JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Kolkata -Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError"
```

Docker Compose `--env-file` is fine with or without quotes; **bash `source`** is not.

---

## 6) EC2 — batch scripts (after scripts uploaded)

```bash
cd /opt/school
chmod +x scripts/ec2/*.sh
sed -i 's/\r$//' scripts/ec2/*.sh

# Full deploy: common → sugamflow apps → school Phase A+B + Flyway check
SCHOOL_DIR=/opt/school SUGAMFLOW_DIR=/opt/sugamflow SCHOOL_PHASE=b \
  bash scripts/ec2/00-full-deploy-and-flyway-check.sh

# School pull+recreate only
SCHOOL_DIR=/opt/school PHASE=b bash scripts/ec2/04-pull-recreate-all-school.sh

# Start / stop helpers
SCHOOL_DIR=/opt/school PHASE=b bash scripts/ec2/02-school-start.sh
SCHOOL_DIR=/opt/school bash scripts/ec2/02-school-stop.sh
SUGAMFLOW_DIR=/opt/sugamflow bash scripts/ec2/01-common-start.sh
```

Always set `SCHOOL_DIR=/opt/school` and `SUGAMFLOW_DIR=/opt/sugamflow` on this host (script defaults may still say `/home/ec2-user/opt/...`).

---

## 7) Local PC test (before EC2)

```powershell
cd D:\school
.\scripts\start-postgres-local.ps1
.\scripts\start-platform.ps1 -SugamFlowRoot "D:\sugamFlow"
.\scripts\seed-school-demo-auth.ps1
.\scripts\start-services.ps1

cd D:\school\apps\school-ui
npm start
# http://127.0.0.1:4300  demo-school / admin_demo-school / password

cd D:\school
.\scripts\verify-gateway.ps1
.\scripts\verify-comms-fanout.ps1
.\scripts\verify-notification-channels.ps1

# Stop school jars
cd D:\sugamFlow
.\scripts\stop-shared-runtime.ps1 -School
```

---

## 8) Minimal release checklist (Comms Father/Mother + UI)

**PC**

```powershell
cd D:\school
.\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.3 -Phase b
cd apps\school-ui; npm run build
```

**EC2**

```bash
# 1) Ensure IMAGE_TAG=1.0.3 in /opt/school/.env.school.production
cd /opt/school
grep IMAGE_TAG .env.school.production

# 2) Pull services
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  pull student-service school-notification-config-service
docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production \
  up -d --force-recreate student-service school-notification-config-service

# 3) UI
mkdir -p ~/school-ui-dist
# WinSCP: browser\* → ~/school-ui-dist/
sudo rsync -a --delete ~/school-ui-dist/ /var/www/school-ui/
sudo chown -R nginx:nginx /var/www/school-ui
sudo nginx -t && sudo systemctl reload nginx
```

**Browser:** `https://school.sugamflow.com` → Comms Hub → audience **Father & Mother only**.

---

## 9) Useful docker one-liners

```bash
# All school containers
docker ps -a --filter name=school- --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'

# Restart one
docker restart school-student-service-1

# Follow logs
docker logs -f --tail 100 school-school-notification-config-service-1

# Disk / memory pressure
df -h
free -m
docker system df
```

---

## 10) Related docs in repo

| File | Content |
|------|---------|
| `D:\school\scripts\ec2\README.md` | Batch start/stop details |
| `D:\school\EC2-FILES-TO-COPY.txt` | What to copy |
| `D:\school\docs\PRODUCTION_DEPLOYMENT_SCHOOL_AND_SUGAMFLOW.md` | Full production runbook |
| `D:\school\docs\NOTIFICATION_CHANNELS.md` | Email / SMS / WhatsApp |
| `D:\sugamFlow\scripts\sequences\README.md` | Platform image sequences |

---

*Updated for host layout `/opt/school` + `/opt/sugamflow` + `/var/www/school-ui` (Aug 2026).*
