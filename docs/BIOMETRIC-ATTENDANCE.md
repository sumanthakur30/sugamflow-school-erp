# Biometric attendance

SugamFlow owns attendance rules. A device only sends a punch. Raw fingerprint and face templates are not stored.

## Current architecture

Class attendance already lives in `attendance_session` and `attendance_mark` (`PRESENT`, `ABSENT`, `LATE`, `LEAVE`, `HALF_DAY`). Staff attendance is a monthly JSON sheet. Device adapters before this change were simulated and only wrote workflow records when `aiAttendanceEnabled` was on. That path is unchanged.

Biometric punches write the class roster directly when the enrollment has a section, and merge one staff row into the monthly sheet without replacing other employees. A submitted staff month is left unchanged.

## Device authentication

Every device request needs the serial number and the device API key (`key` query parameter or `X-Device-Key`). The serial is globally unique. The key is stored as a SHA-256 hash and shown once at creation or regeneration. An unknown serial is logged and rejected. A device from one school cannot write attendance for another school, because the tenant comes from the device record.

## ZKTeco ADMS

These paths are public at the gateway and rate limited. They return plain text.

- `GET /iclock/cdata?SN=&key=` handshake
- `POST /iclock/cdata?SN=&key=&table=ATTLOG` attendance log
- `GET /iclock/getrequest?SN=&key=` heartbeat and queued commands
- `GET|POST /iclock/devicecmd?SN=&key=` command result
- `GET /iclock/ping?SN=&key=`

ATTLOG lines are tab-separated: PIN, `yyyy-MM-dd HH:mm:ss`, status, verify. Short or unparseable lines are skipped. The device clock is interpreted in the device time zone (default `Asia/Kolkata`).

## Normalized push

`POST /api/attendance/biometric/push`

Headers: `X-Device-Serial`, `X-Device-Key`.

```json
{
  "personCode": "10045",
  "eventTime": "2026-10-08T08:32:21+05:30",
  "eventType": "CHECK_IN",
  "verificationType": "FACE",
  "source": "BIOMETRIC"
}
```

Verification values: `FACE`, `FINGERPRINT`, `RFID`, `CARD`, `PIN`, `FACE_FINGERPRINT`, `MOBILE`, `MANUAL`.

The same device, person, timestamp, and event type does not create a second attendance row or a second parent notification.

## Admin API

JWT required. Staff write roles only.

- `GET /api/attendance/biometric/devices`
- `POST /api/attendance/biometric/devices` — response includes `apiKey` once
- `PUT /api/attendance/biometric/devices/{id}`
- `DELETE /api/attendance/biometric/devices/{id}` — disables the device
- `POST /api/attendance/biometric/devices/{id}/key` — regenerates the key
- `DELETE /api/attendance/biometric/devices/{id}/key` — revokes the key and disables the device
- `GET /api/attendance/biometric/health`
- `GET /api/attendance/biometric/live`
- `GET|POST /api/attendance/biometric/enrollments`
- `POST /api/attendance/biometric/enrollments/{id}/disable`
- `GET /api/attendance/biometric/events?status=`
- `POST /api/attendance/biometric/events/{id}/retry`
- `POST /api/attendance/biometric/events/{id}/ignore`
- `GET|PUT /api/attendance/biometric/rules`
- `POST /api/attendance/biometric/devices/{id}/commands` — `TEST`, `SYNC_TIME`, `PULL`, `CLEAR_LOG`, `REBOOT`, `SYNC_USERS`. Reboot and clear log require `confirm: true`.
- `POST /api/attendance/biometric/days/{id}/corrections`
- `GET /api/attendance/biometric/summary?date=`
- `GET /api/attendance/biometric/reports/daily?date=` CSV
- `GET /api/attendance/biometric/reports/people?from=&to=&personType=` CSV

## Rules

`FIRST_LAST` (default), `DEVICE_STATUS`, `GATE` (device direction `IN` or `OUT`), `TIME_SPLIT`. Late is after school start plus grace. Early departure is an out before school end. Half day is when both punches exist and working minutes are below the configured threshold. Early departure is stored on the biometric day and mapped to roster `PRESENT` with a remark, because the class roster statuses stay the existing five values.

## Operators

School UI: Academics → Biometric Attendance (`/admin/biometric`).
