# Live smoke for academic-structure-service (classes, sections, subjects, timetable).
# Prerequisites: Eureka + gateway + school services running; NAT-01 admin credentials.
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
  return Invoke-RestMethod @params
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
  'X-Branch-Id' = 'main'
  'X-Academic-Session-Id' = 'smoke-tests'
}

Write-Host 'Bootstrap...'
$boot = Invoke-Json -Method Get -Url "$Gateway/api/academic/bootstrap" -Headers $h
if (-not $boot.data.organizationId) { throw 'Bootstrap missing organizationId' }
Write-Host "  org=$($boot.data.organizationId) canManage=$($boot.data.canManage)"

Write-Host 'Create class Grade 8...'
$class = Invoke-Json -Method Post -Url "$Gateway/api/academic/classes" -Headers $h -Body @{
  name = 'Grade 8'
  code = 'G8'
  sequenceNo = 8
}
$classId = $class.data.id
if (-not $classId) { throw 'Class create failed' }

Write-Host 'Create section 8-A...'
$section = Invoke-Json -Method Post -Url "$Gateway/api/academic/sections" -Headers $h -Body @{
  classId = $classId
  name = 'A'
  code = '8A'
  studentLabel = 'Grade 8-A'
  classTeacherUsername = 'teacher_demo-school'
}
$sectionId = $section.data.id
if (-not $sectionId) { throw 'Section create failed' }

Write-Host 'Create subject Mathematics...'
$subject = Invoke-Json -Method Post -Url "$Gateway/api/academic/subjects" -Headers $h -Body @{
  name = 'Mathematics'
  code = 'MATH'
  subjectType = 'CORE'
}
$subjectId = $subject.data.id
if (-not $subjectId) { throw 'Subject create failed' }

Write-Host 'Create teaching assignment...'
$asg = Invoke-Json -Method Post -Url "$Gateway/api/academic/assignments" -Headers $h -Body @{
  sectionId = $sectionId
  subjectId = $subjectId
  teacherUsername = 'teacher_demo-school'
  classTeacher = $true
}
if (-not $asg.data.id) { throw 'Assignment create failed' }

Write-Host 'Update class / section / subject...'
$classU = Invoke-Json -Method Put -Url "$Gateway/api/academic/classes/$classId" -Headers $h -Body @{
  name = 'Grade 8'
  code = 'G8-U'
  sequenceNo = 8
}
if ($classU.data.code -ne 'G8-U') { throw 'Class update failed' }
$sectionU = Invoke-Json -Method Put -Url "$Gateway/api/academic/sections/$sectionId" -Headers $h -Body @{
  classId = $classId
  name = 'A'
  code = '8A'
  studentLabel = 'Grade 8-A'
  classTeacherUsername = 'teacher_demo-school'
  room = 'R201'
}
if ($sectionU.data.room -ne 'R201') { throw 'Section update failed' }
$subjectU = Invoke-Json -Method Put -Url "$Gateway/api/academic/subjects/$subjectId" -Headers $h -Body @{
  name = 'Mathematics'
  code = 'MATH'
  subjectType = 'CORE'
}
if ($subjectU.data.name -ne 'Mathematics') { throw 'Subject update failed' }

Write-Host 'List endpoints...'
$classes = Invoke-Json -Method Get -Url "$Gateway/api/academic/classes" -Headers $h
$sections = Invoke-Json -Method Get -Url "$Gateway/api/academic/sections" -Headers $h
$subjects = Invoke-Json -Method Get -Url "$Gateway/api/academic/subjects" -Headers $h
$assignments = Invoke-Json -Method Get -Url "$Gateway/api/academic/assignments" -Headers $h
if (@($classes.data | Where-Object { $_.id -eq $classId }).Count -lt 1) { throw 'Class missing from list' }
if (@($sections.data | Where-Object { $_.id -eq $sectionId }).Count -lt 1) { throw 'Section missing from list' }
if (@($subjects.data | Where-Object { $_.id -eq $subjectId }).Count -lt 1) { throw 'Subject missing from list' }
if (@($assignments.data | Where-Object { $_.id -eq $asg.data.id }).Count -lt 1) { throw 'Assignment missing from list' }

Write-Host 'Teacher scope...'
$scope = Invoke-Json -Method Get -Url "$Gateway/api/academic/teacher-scope?username=teacher_demo-school" -Headers $h
$labels = @($scope.data.studentLabels)
if ($labels -notcontains 'Grade 8-A') {
  throw "Expected Grade 8-A in teacher scope, got: $($labels -join ',')"
}
Write-Host '  RBAC: studentLabel Grade 8-A is in teacher scope (match student classSection/classApplied)'

Write-Host 'Ensure period 1 exists...'
$existingPeriods = Invoke-Json -Method Get -Url "$Gateway/api/academic/timetable/periods" -Headers $h
$period = @($existingPeriods.data) | Where-Object { [int]$_.periodNo -eq 1 } | Select-Object -First 1
if (-not $period) {
  $periodResp = Invoke-Json -Method Post -Url "$Gateway/api/academic/timetable/periods" -Headers $h -Body @{
    periodNo = 1
    label = 'Period 1'
    startTime = '09:00'
    endTime = '09:45'
  }
  $period = $periodResp.data
}
$periodId = $period.id
if (-not $periodId) { throw 'Period ensure failed' }
Write-Host "  periodId=$periodId (reused or created)"

Write-Host 'Replace section timetable...'
$tt = Invoke-Json -Method Put -Url "$Gateway/api/academic/timetable/sections/$sectionId" -Headers $h -Body @{
  # Demo teacher may already have a Monday/P1 slot elsewhere; allowConflicts for smoke.
  allowConflicts = $true
  slots = @(
    @{
      dayOfWeek = 1
      periodId = $periodId
      subjectId = $subjectId
      teacherUsername = 'teacher_demo-school'
      room = 'R101'
    }
  )
}
if (@($tt.data).Count -lt 1) { throw 'Timetable replace returned no slots' }

Write-Host 'List slots...'
$slots = Invoke-Json -Method Get -Url "$Gateway/api/academic/timetable/slots?sectionId=$sectionId" -Headers $h
if (@($slots.data).Count -lt 1) { throw 'No slots listed for section' }

Write-Host ''
Write-Host 'OK - academic structure + timetable smoke passed.'
Write-Host "  class=$classId section=$sectionId subject=$subjectId period=$periodId"
