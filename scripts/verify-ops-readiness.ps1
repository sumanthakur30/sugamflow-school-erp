# Operational readiness go/no-go: health + liveness + readiness + prometheus + correlation id
# across platform + school microservices.
param(
  [switch]$Strict  # fail if any school service is down (default: warn + fail only on core)
)

$ErrorActionPreference = 'Continue'
$failed = 0
$warned = 0

$core = @(
  @{ Name = 'eureka'; Port = 8761; Path = '/actuator/health' },
  @{ Name = 'gateway'; Port = 9090; Path = '/actuator/health' },
  @{ Name = 'auth'; Port = 8085; Path = '/actuator/health' },
  @{ Name = 'notification'; Port = 8087; Path = '/actuator/health' }
)

$school = @(
  @{ Name = 'school-settings-service'; Port = 8181 },
  @{ Name = 'subscription-service'; Port = 8182 },
  @{ Name = 'form-builder-service'; Port = 8183 },
  @{ Name = 'workflow-service'; Port = 8184 },
  @{ Name = 'rule-engine-service'; Port = 8185 },
  @{ Name = 'report-builder-service'; Port = 8186 },
  @{ Name = 'school-notification-config-service'; Port = 8187 },
  @{ Name = 'audit-service'; Port = 8188 },
  @{ Name = 'admission-service'; Port = 8189 },
  @{ Name = 'fee-service'; Port = 8190 },
  @{ Name = 'student-service'; Port = 8191 },
  @{ Name = 'attendance-service'; Port = 8192 },
  @{ Name = 'exam-service'; Port = 8193 },
  @{ Name = 'library-service'; Port = 8194 },
  @{ Name = 'hostel-service'; Port = 8195 },
  @{ Name = 'transport-service'; Port = 8196 },
  @{ Name = 'payroll-service'; Port = 8197 },
  @{ Name = 'staff-service'; Port = 8198 },
  @{ Name = 'academic-structure-service'; Port = 8199 }
)

function Get-BodyText($content) {
  if ($null -eq $content) { return '' }
  if ($content -is [byte[]]) {
    return [System.Text.Encoding]::UTF8.GetString($content)
  }
  return [string]$content
}

function Probe([string]$Url) {
  try {
    $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 4
    return @{ Ok = $true; Status = [int]$r.StatusCode; Body = (Get-BodyText $r.Content); Headers = $r.Headers }
  } catch {
    $resp = $_.Exception.Response
    if ($resp) {
      return @{ Ok = $false; Status = [int]$resp.StatusCode; Body = $_.Exception.Message; Headers = @{} }
    }
    return @{ Ok = $false; Status = 0; Body = $_.Exception.Message; Headers = @{} }
  }
}

Write-Host '=== Platform core ==='
foreach ($s in $core) {
  $url = "http://localhost:$($s.Port)$($s.Path)"
  $p = Probe $url
  if ($p.Ok) {
    Write-Host ("OK   {0,-14} :{1} HTTP {2}" -f $s.Name, $s.Port, $p.Status)
  } elseif ($s.Name -eq 'notification' -and $p.Status -eq 503) {
    Write-Host ("WARN {0,-14} :{1} health 503 (delivery stack degraded)" -f $s.Name, $s.Port)
    $warned++
  } else {
    Write-Host ("FAIL {0,-14} :{1} {2}" -f $s.Name, $s.Port, $p.Body)
    $failed++
  }
}

Write-Host ''
Write-Host '=== School services (health / liveness / readiness / prometheus) ==='
$samplePort = $null
foreach ($s in $school) {
  $base = "http://localhost:$($s.Port)"
  $health = Probe "$base/actuator/health"
  if (-not $health.Ok) {
    $msg = "DOWN :$($s.Port) $($health.Body)"
    if ($Strict) {
      Write-Host "FAIL $($s.Name) $msg"; $failed++
    } else {
      Write-Host "WARN $($s.Name) $msg"; $warned++
    }
    continue
  }
  $live = Probe "$base/actuator/health/liveness"
  $ready = Probe "$base/actuator/health/readiness"
  $prom = Probe "$base/actuator/prometheus"
  $liveOk = $live.Ok -and ($live.Body -match 'UP')
  $readyOk = $ready.Ok -and ($ready.Body -match 'UP')
  $promOk = $prom.Ok -and ($prom.Body -match 'jvm_memory|process_uptime|http_server_requests|jvm_')
  $flags = @()
  if (-not $liveOk) { $flags += 'liveness' }
  if (-not $readyOk) { $flags += 'readiness' }
  if (-not $promOk) { $flags += 'prometheus' }
  if ($flags.Count -eq 0) {
    Write-Host ("OK   {0,-34} :{1} health/live/ready/prom" -f $s.Name, $s.Port)
    if (-not $samplePort) { $samplePort = $s.Port }
  } elseif ($health.Ok -and ($live.Status -eq 404 -or $prom.Status -eq 404)) {
    # Old process still running without Phase 23 actuator exposure.
    Write-Host ("WARN {0,-34} :{1} health UP but probes missing (restart with new jar) [{2}]" -f $s.Name, $s.Port, ($flags -join ','))
    $warned++
    if ($Strict) { $failed++ }
  } else {
    Write-Host ("FAIL {0,-34} :{1} missing={2}" -f $s.Name, $s.Port, ($flags -join ','))
    $failed++
  }
}

Write-Host ''
Write-Host '=== Correlation id echo ==='
if ($samplePort) {
  $reqId = 'ops-ready-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
  try {
    $r = Invoke-WebRequest -Uri "http://localhost:$samplePort/actuator/info" -UseBasicParsing -TimeoutSec 4 -Headers @{ 'X-Request-Id' = $reqId }
    $echo = $r.Headers['X-Request-Id']
    if (-not $echo) { $echo = $r.Headers['x-request-id'] }
    if ($echo -eq $reqId) {
      Write-Host "OK   X-Request-Id echoed on :$samplePort ($echo)"
    } else {
      Write-Host "WARN X-Request-Id on actuator/info was '$echo' (expected $reqId)"
      $warned++
    }
  } catch {
    Write-Host "WARN correlation probe: $($_.Exception.Message)"; $warned++
  }
} else {
  Write-Host 'WARN no school service up to probe correlation id'; $warned++
}

Write-Host ''
Write-Host '=== Gateway correlation (settings theme) ==='
$reqId2 = 'ops-gw-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
try {
  $login = Invoke-RestMethod -Method Post -Uri 'http://localhost:9090/api/v1/auth/login' -ContentType 'application/json' -Body (@{
    username = 'admin_demo-school'; password = 'password'; shopId = 'demo-school'
  } | ConvertTo-Json) -TimeoutSec 8
  $token = $login.accessToken
  $h = @{
    Authorization = "Bearer $token"
    'X-Tenant-Id' = 'demo-school'
    'X-Branch-Id' = 'main'
    'X-Request-Id' = $reqId2
  }
  $r = Invoke-WebRequest -Uri 'http://localhost:9090/api/config/design-studio/theme' -Headers $h -UseBasicParsing -TimeoutSec 8
  $echo = $r.Headers['X-Request-Id']
  if (-not $echo) { $echo = $r.Headers['x-request-id'] }
  if ($echo -eq $reqId2 -or ("$echo" -split ',' | ForEach-Object { $_.Trim() }) -contains $reqId2) {
    Write-Host "OK   gateway->settings echoed X-Request-Id=$echo"
  } elseif ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300) {
    Write-Host "WARN theme OK but X-Request-Id not echoed (got '$echo') - restart settings with new common jar"
    $warned++
  } else {
    Write-Host "FAIL gateway theme HTTP $($r.StatusCode)"; $failed++
  }
} catch {
  Write-Host "WARN gateway correlation: $($_.Exception.Message)"; $warned++
}

Write-Host ''
Write-Host "Summary: failed=$failed warned=$warned"
if ($failed -gt 0) {
  Write-Host 'FAIL  verify-ops-readiness'
  exit 1
}
Write-Host 'PASS  verify-ops-readiness'
