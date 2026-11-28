[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Destination,
    [string] $SparkJar,
    [string] $BallisticsJar
)

$ErrorActionPreference = 'Stop'
$projectDir = Split-Path -Parent $PSScriptRoot
$properties = ConvertFrom-StringData (Get-Content -Raw -LiteralPath (Join-Path $projectDir 'gradle.properties'))
$mc = $properties.minecraft_version
$loader = $properties.mod_loader
$sparkVersion = $properties.spark_core_version
$ballisticsVersion = $properties.ballistics_framework_version

if (!$SparkJar) {
    $SparkJar = Join-Path $projectDir "../Spark-Core/build/libs/SparkCore-$mc-$loader-$sparkVersion.jar"
}
if (!$BallisticsJar) {
    $BallisticsJar = Join-Path $projectDir "../BallisticsFramework/build/libs/ballistics_framework-$mc-$loader-$ballisticsVersion.jar"
}

$destinationPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Destination)
if (Test-Path -LiteralPath $destinationPath) {
    throw "Destination already exists: $destinationPath. Choose a new directory to avoid replacing jars in use."
}

$modules = @(
    @{ Group = 'io.github.solarmoonqaq'; Artifact = "spark-core-$loader"; Version = $sparkVersion; Jar = $SparkJar; ModId = 'spark_core' }
    @{ Group = 'io.github.sweetzonzi'; Artifact = "ballistics_framework-$mc-$loader"; Version = $ballisticsVersion; Jar = $BallisticsJar; ModId = 'ballistics_framework' }
)

Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($module in $modules) {
    $module.Jar = (Resolve-Path -LiteralPath $module.Jar).ProviderPath
    $zip = [IO.Compression.ZipFile]::OpenRead($module.Jar)
    try {
        $metadata = $zip.GetEntry('META-INF/neoforge.mods.toml')
        if (!$metadata) { throw "No NeoForge mod metadata in $($module.Jar)" }
        $reader = [IO.StreamReader]::new($metadata.Open())
        try { $modsToml = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $modId = [regex]::Escape($module.ModId)
        $version = [regex]::Escape($module.Version)
        if ($modsToml -notmatch "(?m)^\s*modId\s*=\s*`"$modId`"" -or
            $modsToml -notmatch "(?m)^\s*version\s*=\s*`"$version`"") {
            throw "Expected NeoForge mod $($module.ModId) $($module.Version) in $($module.Jar)"
        }
    } finally { $zip.Dispose() }
}

# Stage next to the destination so a failed copy never exposes a partial repository.
$stagingPath = "$destinationPath.tmp-$([guid]::NewGuid().ToString('N'))"
foreach ($module in $modules) {
    $group = $module.Group
    $artifact = $module.Artifact
    $version = $module.Version
    $target = Join-Path $stagingPath "$($group.Replace('.', '/'))/$artifact/$version"
    [void][IO.Directory]::CreateDirectory($target)
    Copy-Item -LiteralPath $module.Jar -Destination (Join-Path $target "$artifact-$version.jar")
    $pom = @"
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group</groupId>
  <artifactId>$artifact</artifactId>
  <version>$version</version>
</project>
"@
    [IO.File]::WriteAllText((Join-Path $target "$artifact-$version.pom"), $pom)
}
[IO.Directory]::Move($stagingPath, $destinationPath)
Write-Output $destinationPath
