param(
    [Parameter(Mandatory=$true)][string]$PluginDirectory,
    [string]$JavaExecutable='java',
    [string]$YamlLibrary
)
$ErrorActionPreference='Stop'
$pluginRoot=(Resolve-Path -LiteralPath $PluginDirectory).Path
$serverRoot=Split-Path -Parent (Split-Path -Parent $pluginRoot)
if(-not $YamlLibrary){
    $libraryRoot=Join-Path $serverRoot 'libraries'
    $candidates=@()
    if(Test-Path -LiteralPath $libraryRoot){
        $candidates=@(Get-ChildItem -LiteralPath $libraryRoot -Filter 'snakeyaml-*.jar' -File -Recurse | Sort-Object FullName -Descending)
    }
    if($candidates.Count -eq 0){throw 'Supply -YamlLibrary with the path to the server SnakeYAML 2.x JAR'}
    $YamlLibrary=$candidates[0].FullName
}
$library=(Resolve-Path -LiteralPath $YamlLibrary).Path
$source=Join-Path $PSScriptRoot 'UpgradeSettings.java'
$resources=Join-Path (Split-Path -Parent $PSScriptRoot) 'src/main/resources'
$settings=Join-Path $resources 'settings.yml'
$notices=Join-Path $resources 'locale/en.yml'
foreach($required in @($source,$settings,$notices)){
    if(-not(Test-Path -LiteralPath $required -PathType Leaf)){throw ('Missing upgrade file: '+$required)}
}
& $JavaExecutable --class-path $library $source $pluginRoot $settings $notices
if($LASTEXITCODE -ne 0){throw 'The upgrade did not complete; inspect the diagnostic above'}
