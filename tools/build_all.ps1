# Builds one BetterSound jar per Minecraft version into dist/ (retries on flaky network).
param([string[]]$Versions)
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$table = Get-Content "$root\versions.properties" | Where-Object { $_ -match '^\d' }
if (-not $Versions) { $Versions = $table | ForEach-Object { ($_ -split '=')[0] } }
New-Item -ItemType Directory -Force "$root\dist" | Out-Null
$results = @()
foreach ($v in $Versions) {
    $row = $table | Where-Object { $_ -like "$v=*" }
    $adapter = (($row -split '=')[1] -split '\|')[0]
    $dir = if ($adapter -eq 'yarn') { 'legacy' } else { 'modern' }
    $ok = $false
    for ($i = 1; $i -le 5 -and -not $ok; $i++) {
        Push-Location "$root\$dir"
        $log = & .\gradlew.bat build dist "-Pmc=$v" --console=plain 2>&1
        $code = $LASTEXITCODE
        Pop-Location
        if ($code -eq 0) { $ok = $true }
        elseif ($log -match 'error:') { $log | Select-String 'error:' -Context 0,2 | Out-Host; break }
    }
    $results += "$v`t$(if ($ok) {'OK'} else {'FAILED'})"
    Write-Host "$v -> $(if ($ok) {'OK'} else {'FAILED'})"
}
Remove-Item "$root\dist\*-sources.jar" -ErrorAction SilentlyContinue
$results -join "`n"
