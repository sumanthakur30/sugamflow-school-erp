<#
.SYNOPSIS
  Fresh-build and push ALL School ERP Docker images to Docker Hub.

.DESCRIPTION
  Uses Dockerfile.school-service (fat-JAR selection fix — excludes *-copy.jar).
  Run on Windows from D:\school after Docker Desktop login.

.PARAMETER ImagePrefix
  Docker Hub namespace (default sumanthakur30)

.PARAMETER ImageTag
  Tag for every image (default 1.0.2). Also set the same IMAGE_TAG on EC2 .env.school.production

.PARAMETER NoCache
  Force docker build --no-cache (slower, cleanest)

.PARAMETER SkipPush
  Build only; do not docker push

.PARAMETER Phase
  a = Phase A only | b = Phase A+B | all = every deployable module including audit/rules/reports

.EXAMPLE
  cd D:\school
  .\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.2 -NoCache

.EXAMPLE
  .\scripts\build-push-all-school-images.ps1 -ImageTag 1.0.2 -Phase b
#>
[CmdletBinding()]
param(
  [string]$ImagePrefix = "sumanthakur30",
  [string]$ImageTag = "1.0.2",
  [switch]$NoCache,
  [switch]$SkipPush,
  [ValidateSet("a", "b", "all")]
  [string]$Phase = "all"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$Root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $Root "Dockerfile.school-service"))) {
  $Root = (Get-Location).Path
}
Set-Location $Root

if (-not (Test-Path ".\Dockerfile.school-service")) {
  throw "Run from D:\school (Dockerfile.school-service not found). Current: $(Get-Location)"
}

$PhaseA = @(
  "school-settings-service",
  "subscription-service",
  "form-builder-service",
  "workflow-service",
  "academic-structure-service",
  "staff-service",
  "student-service",
  "admission-service",
  "fee-service"
)

$PhaseB = @(
  "attendance-service",
  "exam-service",
  "payroll-service",
  "library-service",
  "hostel-service",
  "transport-service",
  "school-notification-config-service"
)

$Extra = @(
  "audit-service",
  "rule-engine-service",
  "report-builder-service"
)

$Modules = switch ($Phase) {
  "a" { $PhaseA }
  "b" { $PhaseA + $PhaseB }
  "all" { $PhaseA + $PhaseB + $Extra }
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " School images build + push" -ForegroundColor Cyan
Write-Host " Root:   $Root"
Write-Host " Prefix: $ImagePrefix"
Write-Host " Tag:    $ImageTag"
Write-Host " Phase:  $Phase ($($Modules.Count) modules)"
Write-Host " NoCache:$NoCache  SkipPush:$SkipPush"
Write-Host "========================================" -ForegroundColor Cyan

docker version | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Docker is not available. Start Docker Desktop." }

if (-not $SkipPush) {
  Write-Host "`nChecking Docker Hub login (docker login -u $ImagePrefix if this fails)..." -ForegroundColor Yellow
}

$failed = [System.Collections.Generic.List[string]]::new()
$ok = [System.Collections.Generic.List[string]]::new()
$minJarBytes = 5MB

foreach ($Module in $Modules) {
  $image = "${ImagePrefix}/${Module}:${ImageTag}"
  Write-Host "`n-------- BUILD $Module -> $image --------" -ForegroundColor Green

  $buildArgs = @(
    "build",
    "-f", "Dockerfile.school-service",
    "--build-arg", "MODULE=$Module",
    "-t", $image
  )
  if ($NoCache) { $buildArgs += "--no-cache" }
  $buildArgs += "."

  & docker @buildArgs
  if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED: $Module" -ForegroundColor Red
    $failed.Add($Module) | Out-Null
    continue
  }

  # Verify fat JAR (avoid 249KB *-copy.jar disaster)
  $sizeLine = docker run --rm --entrypoint sh $image -c "wc -c < /app/app.jar" 2>$null
  $size = 0
  if ($sizeLine) { [void][long]::TryParse(($sizeLine.ToString().Trim()), [ref]$size) }
  Write-Host "  /app/app.jar size = $size bytes"
  if ($size -lt $minJarBytes) {
    Write-Host "  REJECTED: jar too small (likely thin/copy jar). Not pushing $Module." -ForegroundColor Red
    $failed.Add("$Module (jar $size bytes)") | Out-Null
    continue
  }

  $manifestCheck = docker run --rm --entrypoint sh $image -c "java -jar /app/app.jar --version 2>&1 | head -3" 2>&1
  if ("$manifestCheck" -match "no main manifest attribute") {
    Write-Host "  REJECTED: no main manifest attribute. Not pushing $Module." -ForegroundColor Red
    $failed.Add("$Module (no Main-Class)") | Out-Null
    continue
  }

  if (-not $SkipPush) {
    Write-Host "  PUSH $image" -ForegroundColor Yellow
    docker push $image
    if ($LASTEXITCODE -ne 0) {
      Write-Host "PUSH FAILED: $Module" -ForegroundColor Red
      $failed.Add("$Module (push)") | Out-Null
      continue
    }
  }

  $ok.Add($Module) | Out-Null
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host " DONE. OK=$($ok.Count)  FAIL=$($failed.Count)" -ForegroundColor Cyan
if ($ok.Count) {
  Write-Host "Built:" -ForegroundColor Green
  $ok | ForEach-Object { Write-Host "  - $_" }
}
if ($failed.Count) {
  Write-Host "Failed:" -ForegroundColor Red
  $failed | ForEach-Object { Write-Host "  - $_" }
  exit 1
}

Write-Host @"

Next — EC2 (/opt/school):
  1) Set IMAGE_TAG=$ImageTag in .env.school.production
  2) WinSCP docker-compose.school.ec2-rds.yml if changed
  3) bash scripts/ec2/04-pull-recreate-all-school.sh

Or manually:
  cd /opt/school
  docker compose -f docker-compose.school.ec2-rds.yml --env-file .env.school.production pull
  PHASE=b bash scripts/ec2/02-school-stop.sh
  PHASE=b bash scripts/ec2/02-school-start.sh

"@
