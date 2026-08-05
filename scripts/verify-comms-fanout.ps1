# Live smoke: Comms Hub guardian fan-out (IN_APP + optional EMAIL) with durable outbox.
param(
  [string]$Gateway = 'http://localhost:9090',
  [string]$ShopId = 'demo-school',
  [string]$Username = 'admin_demo-school',
  [string]$Password = 'password'
)

$ErrorActionPreference = 'Stop'

function Invoke-Json {
  param([string]$Method, [string]$Url, [hashtable]$Headers, $Body)
  $params = @{
    Method = $Method
    Uri = $Url
    Headers = $Headers
    ContentType = 'application/json'
  }
  if ($null -ne $Body) {
    $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
  }
  try {
    return Invoke-RestMethod @params
  } catch {
    $detail = $_.ErrorDetails.Message
    if (-not $detail -and $_.Exception.Response) {
      try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $detail = $reader.ReadToEnd()
      } catch { }
    }
    if ($detail) {
      throw "$Method $Url failed: $detail"
    }
    throw
  }
}

Write-Host "Logging in as $Username @ $ShopId..."
$login = Invoke-Json -Method Post -Url "$Gateway/api/v1/auth/login" -Headers @{} -Body @{
  username = $Username
  password = $Password
  shopId = $ShopId
}
if (-not $login.accessToken) { throw 'Login failed - no accessToken' }

$h = @{
  Authorization = "Bearer $($login.accessToken)"
  'X-Tenant-Id' = $ShopId
  'X-Shop-Id' = $ShopId
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = 'smoke-tests'
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$label = "Comms-$stamp"

Write-Host "Create class/section ($label)..."
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = "Comms $stamp"; code = "CM$stamp".Substring(0,[Math]::Min(12,"CM$stamp".Length)); sequenceNo = 97
}
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $class.data.id; name = 'A'; code = "CMA$stamp".Substring(0,[Math]::Min(12,"CMA$stamp".Length)); studentLabel = $label
}

Write-Host 'Enroll student + link guardian account...'
$adm = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications" -Headers $h -Body @{
  answers = @{
    fullName = "Comms Student $stamp"
    age = 12
    dob = '2014-01-15'
    fatherName = 'Mr Comms Father'
    motherName = 'Mrs Comms Parent'
    mobile = "96$stamp".Substring(0,10)
    email = "comms.$stamp@demo-school.local"
    classApplied = $label
    classSection = $label
    classGrade = '6'
    sectionLetter = 'A'
    documentsComplete = $true
    guardianFullName = 'Mrs Comms Parent'
    guardianRelation = 'Mother'
    guardianMobile = '9822223333'
  }
}
$app = $adm.data
for ($i = 1; $i -le 8; $i++) {
  if ($app.status -eq 'APPROVED') { break }
  $upd = Invoke-Json -Method Post -Url "$Gateway/api/admission/applications/$($app.id)/actions" -Headers $h -Body @{ action='APPROVE'; comment="comms $i" }
  $app = $upd.data
}
if ($app.status -ne 'APPROVED') { throw "Admission not approved: $($app.status)" }
$studentId = $app.enrolledStudentId

Invoke-Json -Method Put -Url "$Gateway/api/student/students/$studentId/guardians" -Headers $h -Body @{
  guardians = @(
    @{
      fullName = 'Mrs Comms Parent'
      relation = 'Mother'
      mobile = '9822223333'
      email = "comms.parent.$stamp@demo-school.local"
      authUsername = $Username
      isPrimary = $true
    }
  )
} | Out-Null

Write-Host 'Publish announcement...'
$title = "Comms alert $stamp"
$created = Invoke-Json -Method Post -Url "$Gateway/api/school/notification-config/comms/announcements" -Headers $h -Body @{
  title = $title
  body = "Hello parents - smoke announcement $stamp"
  channel = 'EMAIL'
  audience = 'PARENTS'
}
$row = $created.data
if (-not $row) { $row = $created }
Write-Host "  status=$($row.status) fanOut=$(ConvertTo-Json $row.fanOut -Compress)"
if ($row.status -notin @('SENT','PARTIAL_FAILED')) { throw "expected SENT/PARTIAL_FAILED, got $($row.status)" }
$summary = $row.fanOut
if (-not $summary) { $summary = $row.delivery[0] }
if ([int]$summary.sent -lt 1) { throw 'expected at least one SENT delivery' }

Write-Host 'Re-publish same content should still create a new announcement, but targets stay unique per announcement...'
$created2 = Invoke-Json -Method Post -Url "$Gateway/api/school/notification-config/comms/announcements" -Headers $h -Body @{
  title = "$title again"
  body = "Hello parents - smoke announcement $stamp again"
  channel = 'IN_APP'
  audience = 'PARENTS'
}
$row2 = $created2.data
if (-not $row2) { $row2 = $created2 }
if ([int]$row2.fanOut.sent -lt 1 -and [int]$row2.delivery[0].sent -lt 1) {
  throw 'second announcement should also fan out'
}

Write-Host 'Check authenticated in-app inbox...'
$inboxJson = (Invoke-WebRequest -UseBasicParsing -Uri "$Gateway/api/v1/notifications/in-app" -Headers $h).Content
$inbox = $inboxJson | ConvertFrom-Json
$hit = $inbox | Where-Object { $_.subject -eq $title } | Select-Object -First 1
if (-not $hit) { throw 'comms announcement missing from authenticated inbox' }
Write-Host "  inbox id=$($hit.id) subject=$($hit.subject)"

Write-Host ''
Write-Host 'OK - comms guardian fan-out smoke passed.'
Write-Host "  announcementId=$($row.id) studentId=$studentId"
