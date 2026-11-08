param(
    [string]$GameDir = "",
    [string]$D3dxDll = "",
    [int]$Jobs = 3
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if ([string]::IsNullOrWhiteSpace($GameDir)) {
    $GameDir = Join-Path $Root "..\Need for Speed Most Wanted 2005"
}
$GameDir = (Resolve-Path -LiteralPath $GameDir).Path

if ([string]::IsNullOrWhiteSpace($D3dxDll)) {
    $candidate = Join-Path $GameDir "d3dx9_26.dll"
    if (Test-Path -LiteralPath $candidate) {
        $D3dxDll = $candidate
    } else {
        $D3dxDll = Join-Path $env:WINDIR "SysWOW64\d3dx9_26.dll"
    }
}
$D3dxDll = (Resolve-Path -LiteralPath $D3dxDll).Path

$SpeedExe = @("speed.exe", "Speed.exe") |
    ForEach-Object { Join-Path $GameDir $_ } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if (!$SpeedExe) { throw "speed.exe / Speed.exe was not found in $GameDir" }

$ExpectedHash = "80774c2e5d619b4f120b48d4462896fd504c263399d203a238769cffde1d253c"
$ActualHash = (Get-FileHash -LiteralPath $SpeedExe -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualHash -ne $ExpectedHash) {
    throw "The selected speed.exe is not the verified reference. SHA256: $ActualHash"
}
foreach ($folder in @("CARS", "FRONTEND", "GLOBAL", "LANGUAGES", "MOVIES", "NIS", "SOUND", "TRACKS")) {
    if (!(Test-Path -LiteralPath (Join-Path $GameDir $folder) -PathType Container)) {
        throw "Missing game data folder: $folder"
    }
}
if (!(Test-Path -LiteralPath $D3dxDll -PathType Leaf)) { throw "Missing d3dx9_26.dll: $D3dxDll" }

$Pcrecomp = Join-Path $Root "..\pcrecomp"
$Nfsu2 = Join-Path $Root "..\nfsu2-recompiled"
$Android = Join-Path $Root "android"
if (!(Test-Path -LiteralPath (Join-Path $Pcrecomp "tools\lift\lift32.py"))) {
    throw "Missing patched pcrecomp sibling checkout: $Pcrecomp"
}
if (!(Test-Path -LiteralPath (Join-Path $Nfsu2 "ports\dxvk_android\CMakeLists.txt"))) {
    throw "Missing nfsu2-recompiled DXVK Android port: $Nfsu2"
}
if (!(Test-Path -LiteralPath (Join-Path $Nfsu2 "build\dxvk-source\include\native"))) {
    throw "DXVK source is not prepared at $Nfsu2\build\dxvk-source"
}
if (!(Test-Path -LiteralPath (Join-Path $Android "gradlew.bat"))) {
    throw "Gradle wrapper is missing from $Android"
}
if (!$env:ANDROID_HOME -and !$env:ANDROID_SDK_ROOT -and !(Test-Path -LiteralPath (Join-Path $Android "local.properties"))) {
    throw "Set ANDROID_HOME / ANDROID_SDK_ROOT or create android\local.properties with sdk.dir"
}

$env:PCRECOMP = (Resolve-Path $Pcrecomp).Path
$AssetStage = Join-Path $Android "app\src\main\assets\nfsmw"
Push-Location $Root
try {
    New-Item -ItemType Directory -Force -Path "work" | Out-Null
    & python -c "import pefile, capstone"
    if ($LASTEXITCODE -ne 0) {
        throw "Python dependencies missing. Install them with: python -m pip install pefile capstone"
    }
    & python -c "import pefile,sys; p=pefile.PE(sys.argv[1]); assert p.FILE_HEADER.Machine == 0x14c and p.OPTIONAL_HEADER.Magic == 0x10b and p.OPTIONAL_HEADER.ImageBase == 0x400000 and p.OPTIONAL_HEADER.SizeOfImage == 0x24a000, 'Wrong d3dx9_26.dll build; expected x86 PE32, base 0x400000, image size 0x24a000'" $D3dxDll
    if ($LASTEXITCODE -ne 0) { throw "d3dx9_26.dll does not match the expected 32-bit DirectX runtime image" }

    function Invoke-PythonStep([string[]]$StepArgs) {
        Write-Host ">>> python $($StepArgs -join ' ')" -ForegroundColor Cyan
        & python @StepArgs
        if ($LASTEXITCODE -ne 0) { throw "Python step failed: $($StepArgs[0])" }
    }

    Invoke-PythonStep @("scripts\analyze_binary.py", $SpeedExe, "work")

    Invoke-PythonStep @("scripts\data_seeds.py", "work\speed.analysis.exe", "work\seeds_data.json", "--merge", "config\forced_seeds.json")
    Invoke-PythonStep @("scripts\run_lift.py", "catalog", "work\speed.analysis.exe", "work\seeds_data.json", "work\catalog_probe.json")
    Invoke-PythonStep @("scripts\code_seeds.py", "work\speed.analysis.exe", "work\catalog_probe.json", "work\seeds_code.json", "--merge", "work\seeds_data.json")
    Invoke-PythonStep @("scripts\run_lift.py", "catalog", "work\speed.analysis.exe", "work\seeds_code.json", "work\catalog.json")
    Invoke-PythonStep @("scripts\run_lift.py", "lift", "work\speed.analysis.exe", "work\catalog.json", "src\recomp\gen", "--split", "400")

    Invoke-PythonStep @("scripts\prepare_module.py", $D3dxDll, "0x00B00000", "work\d3dx.analysis.dll", "work\d3dx.seeds.json")
    Invoke-PythonStep @("scripts\run_lift.py", "catalog", "work\d3dx.analysis.dll", "work\d3dx.seeds.json", "work\d3dx.catalog.json", "--seeds-only")
    Invoke-PythonStep @("scripts\run_lift.py", "lift", "work\d3dx.analysis.dll", "work\d3dx.catalog.json", "src\recomp\gen_d3dx", "--prefix", "d3dx_")

    Invoke-PythonStep @("scripts\abk_module.py", $GameDir, "work\abk.analysis.dll", "work\abk.seeds.json", "work\abk_blocks.c")
    Invoke-PythonStep @("scripts\run_lift.py", "catalog", "work\abk.analysis.dll", "work\abk.seeds.json", "work\abk.catalog.json", "--seeds-only")
    Invoke-PythonStep @("scripts\run_lift.py", "lift", "work\abk.analysis.dll", "work\abk.catalog.json", "src\recomp\gen_abk", "--prefix", "abk_")

    # Bundle this owner's local game data inside the personal APK. Nothing is uploaded to GitHub.
    if (Test-Path -LiteralPath $AssetStage) { Remove-Item -LiteralPath $AssetStage -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $AssetStage | Out-Null
    $GameFiles = @(Get-ChildItem -LiteralPath $GameDir -File -Recurse -Force)
    $BundleBytes = [long]0
    foreach ($file in $GameFiles) {
        if ($file.Name -ieq "d3dx9_26.dll") { continue }
        $BundleBytes += [long]$file.Length
    }
    $BundleBytes += [long](Get-Item -LiteralPath $D3dxDll).Length
    $MaxSingleApkBytes = [long](3.5 * 1024 * 1024 * 1024)
    if ($BundleBytes -gt $MaxSingleApkBytes) {
        throw ("Game data is {0:N2} GB. This one-APK build is capped at 3.5 GB to avoid ZIP/APK size-boundary failures." -f ($BundleBytes / 1GB))
    }
    Write-Host (">>> Bundling {0:N2} GB of personal game files into the APK" -f ($BundleBytes / 1GB)) -ForegroundColor Cyan
    $ManifestLines = New-Object 'System.Collections.Generic.List[string]'
    foreach ($file in $GameFiles) {
        if ($file.Name -ieq "d3dx9_26.dll") { continue }
        $Relative = $file.FullName.Substring($GameDir.TrimEnd('\').Length).TrimStart('\','/').Replace('\','/')
        $Destination = Join-Path $AssetStage ($Relative.Replace('/', '\'))
        $DestinationDirectory = Split-Path -Parent $Destination
        if (!(Test-Path -LiteralPath $DestinationDirectory)) {
            New-Item -ItemType Directory -Force -Path $DestinationDirectory | Out-Null
        }
        Copy-Item -LiteralPath $file.FullName -Destination $Destination -Force
        $ManifestLines.Add(([long]$file.Length).ToString() + [char]9 + $Relative)
    }
    $D3dxDestination = Join-Path $AssetStage "d3dx9_26.dll"
    Copy-Item -LiteralPath $D3dxDll -Destination $D3dxDestination -Force
    $ManifestLines.Add(([long](Get-Item -LiteralPath $D3dxDll).Length).ToString() + [char]9 + "d3dx9_26.dll")
    $ManifestPath = Join-Path $AssetStage ".install_manifest.txt"
    [System.IO.File]::WriteAllLines($ManifestPath, [string[]]$ManifestLines, (New-Object System.Text.UTF8Encoding($false)))

    Push-Location $Android
    try {
        Write-Host ">>> Gradle assembleDebug (workers: $Jobs)" -ForegroundColor Cyan
        & .\gradlew.bat --no-daemon --max-workers=$Jobs assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "Android Gradle build failed" }
    } finally {
        Pop-Location
    }

    $Apk = Join-Path $Android "app\build\outputs\apk\debug\app-debug.apk"
    if (!(Test-Path -LiteralPath $Apk)) { throw "Gradle finished but APK was not found: $Apk" }
    $FinalApk = Join-Path $Root "MostWantedOffline.apk"
    Copy-Item -LiteralPath $Apk -Destination $FinalApk -Force
    $SizeMB = [math]::Round((Get-Item -LiteralPath $FinalApk).Length / 1MB, 1)
    Write-Host "BUILD SUCCESS: $FinalApk ($SizeMB MB)" -ForegroundColor Green
    Write-Host "This is a debug-signed personal build, not a public release signing key."
} finally {
    if ($AssetStage -and (Test-Path -LiteralPath $AssetStage)) {
        Remove-Item -LiteralPath $AssetStage -Recurse -Force
    }
    Pop-Location
}
