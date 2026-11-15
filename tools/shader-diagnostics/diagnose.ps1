[CmdletBinding()]
param(
    [string]$Capture,
    [string]$Shader = 'saved',
    [ValidateSet('current', 'captured', 'budget', 'range', 'thickness', 'all')]
    [string]$Preset = 'current',
    [string]$Variants,
    [string]$Output,
    [string]$Baseline,
    [ValidateRange(0, 255)][int]$Threshold = 2,
    [ValidateCount(2, 2)][int[]]$Pixel,
    [int]$Light = 0,
    [string]$CompareLeft,
    [string]$CompareRight,
    [switch]$Help
)
$ErrorActionPreference = 'Stop'
$repositoryDirectory = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
if ($Capture -eq 'latest') {
    $latestCapture = Get-ChildItem -LiteralPath (Join-Path $repositoryDirectory 'run/screenshots') -Directory -Filter 'mm-light-*' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latestCapture) { throw 'No mm-light capture directory found under run/screenshots.' }
    $Capture = $latestCapture.FullName
}
# 在切换工作目录前解析用户给出的相对路径。
foreach ($name in @('Capture', 'Variants', 'Baseline', 'CompareLeft', 'CompareRight')) {
    $value = Get-Variable -Name $name -ValueOnly
    if ($value) { Set-Variable -Name $name -Value (Resolve-Path -LiteralPath $value).Path }
}
if ($Shader -notin @('saved', 'current')) { $Shader = (Resolve-Path -LiteralPath $Shader).Path }
if ($Output) { $Output = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Output) }
if (-not $Help -and -not $Capture -and -not ($CompareLeft -and $CompareRight)) {
    throw 'Provide -Capture, or both -CompareLeft and -CompareRight. Use -Help for CLI syntax.'
}
$gradleArguments = @('--offline', 'shaderDiagnostics')
if ($Help) { $gradleArguments += '-PshaderDiagnosticsHelp=true' }
else {
    $values = [ordered]@{
        Capture = $Capture; Shader = $Shader; Preset = $Preset; Variants = $Variants; Output = $Output
        Baseline = $Baseline; Threshold = "$Threshold"; Pixel = ($Pixel -join ','); Light = "$Light"
        CompareLeft = $CompareLeft; CompareRight = $CompareRight
    }
    foreach ($entry in $values.GetEnumerator()) {
        if ($entry.Value -ne '' -and $null -ne $entry.Value) { $gradleArguments += "-Pshader$($entry.Key)=$($entry.Value)" }
    }
}
Push-Location -LiteralPath $repositoryDirectory
try {
    & (Join-Path $repositoryDirectory 'gradlew.bat') @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Shader diagnostics failed (exit $LASTEXITCODE)." }
} finally { Pop-Location }
