param(
    [ValidateSet("1.21.1", "26.2")]
    [string]$Version = "1.21.1",
    [switch]$All
)

$Tools = $PSScriptRoot
$Root = Split-Path $Tools -Parent

function Build-One {
    param([string]$Ver)

    if ($Ver -eq "1.21.1") {
        $jdk = Join-Path $Tools "jdk\jdk-21.0.12.1+1"
        $proj = "qianhun_1211"
    } else {
        $jdk = Join-Path $Tools "jdk\jdk-25.0.4.1+1"
        $proj = "qianhun_262"
    }

    $javaExe = Join-Path $jdk "bin\java.exe"
    if (-not (Test-Path $javaExe)) {
        Write-Host "[ERROR] JDK not found: $jdk" -ForegroundColor Red
        return 1
    }

    $env:JAVA_HOME = $jdk
    $env:Path = "$jdk\bin;$env:Path"

    $gradle = Join-Path $Tools "gradle-9.5.1\bin\gradle.bat"
    $projectDir = Join-Path $Root $proj

    Write-Host "============================================"
    Write-Host " Qianhun mod - MC $Ver / Fabric"
    Write-Host " JDK    : $jdk"
    Write-Host " Project: $projectDir"
    Write-Host "============================================"

    & $gradle -p $projectDir build --console=plain
    $code = $LASTEXITCODE

    if ($code -eq 0) {
        Write-Host "[DONE] Build OK: $proj\build\libs\" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] exit code $code" -ForegroundColor Red
    }
    return $code
}

if ($All) {
    $c1 = Build-One "1.21.1"
    $c2 = Build-One "26.2"
    Write-Host ""
    Write-Host "1.21.1 exit code : $c1"
    Write-Host "26.2   exit code : $c2"
    exit ([Math]::Max($c1, $c2))
} else {
    exit (Build-One $Version)
}
