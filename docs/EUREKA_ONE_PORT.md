# Eureka: one host port (`:8761`)

## Why you used to see `:8761` and `:18761`

There was never two Eureka servers by design.

| Layer | Port |
|-------|------|
| Eureka process (jar or container) | always **8761** |
| Docker → host publish (old default) | **18761→8761** |
| Docker containers talking to Eureka | `http://discovery-service:8761/eureka` |
| School / platform jar defaults | `http://localhost:8761/eureka` |

**18761** existed only so Docker Eureka would not collide with a host-jar Eureka on `:8761`. That “safety” created the daily pain: school scripts expected `:8761`, Docker published `:18761`, jars never registered, gateway returned **503**.

## Solid rule (current)

1. **Local Docker publishes Eureka as `8761:8761`** (and Config `8888:8888`).
2. **All school jars register to `http://localhost:8761/eureka`.**
3. **One Eureka per day** — either Docker discovery *or* `start-platform.ps1` jar discovery, never both.
4. If gateway is Docker and school services are host jars, advertise **`host.docker.internal`** (not `127.0.0.1`).

Override only if something else already owns `:8761`:

```env
# D:\sugamFlow\.env or .env.local — avoid unless necessary
DISCOVERY_SERVICE_HOST_PORT=8761
CONFIG_SERVICE_HOST_PORT=8888
```

## After pulling this change (existing Docker stack)

Recreate discovery/config so the host map switches from 18761→8761:

```powershell
cd D:\sugamFlow
docker compose up -d --force-recreate discovery-service config-service
cd D:\school
.\scripts\start-services.ps1 -Restart -SkipBuild -AdvertiseIp host.docker.internal
```

Legacy `:18761` is still probed as a fallback with a yellow warning so old containers do not strand you mid-day.

## Related 503 causes (not ports)

- Wrong advertise IP (`127.0.0.1` with Docker gateway)
- Services not started / not registered yet
- Split-brain: jar Eureka on 8761 + Docker Eureka still on 18761
