param([string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference = 'Stop'
$probeRoot = $PSScriptRoot
$buildDir = Join-Path $probeRoot 'build'
$toolsDir = Join-Path $SdkRoot 'build-tools\36.0.0'
$platformJar = Join-Path $SdkRoot 'platforms\android-37.0\android.jar'
if (-not (Test-Path -LiteralPath $platformJar)) {
    $platformJar = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'platforms') -Filter android.jar -Recurse |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $platformJar) { throw 'Android SDK platform android.jar is missing.' }
function Invoke-Checked([string]$File, [string[]]$Arguments) {
    & $File @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$File failed with exit code $LASTEXITCODE" }
}
$javaCompiler = (Get-Command javac.exe).Source
$javaTool = (Get-Command java.exe).Source
$jarTool = (Get-Command jar.exe).Source
$keyTool = (Get-Command keytool.exe).Source
# Android batch tools require JAVA_HOME; derive it from the verified compiler.
$env:JAVA_HOME = Split-Path (Split-Path $javaCompiler)
foreach ($dir in @('classes', 'test-classes', 'dex', 'gen')) {
    New-Item -ItemType Directory -Path (Join-Path $buildDir $dir) -Force | Out-Null
}
# Remove generated class files so deleted anonymous classes cannot remain in the APK.
$compiledDir = [System.IO.Path]::GetFullPath((Join-Path $buildDir 'classes'))
$buildPrefix = [System.IO.Path]::GetFullPath($buildDir).TrimEnd('\') + '\'
if (-not $compiledDir.StartsWith($buildPrefix, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Compiled classes directory is outside the build directory.' }
Get-ChildItem -LiteralPath $compiledDir -Filter '*.class' -File -Recurse | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
$policy = Join-Path $probeRoot 'src\cn\xunji\probe\CapturePolicy.java'
$selectionPolicy = Join-Path $probeRoot 'src\cn\xunji\probe\SelectionPolicy.java'
$test = Join-Path $probeRoot 'tests\CapturePolicyTest.java'
$refreshPolicy = Join-Path $probeRoot 'src\cn\xunji\probe\OverlayRefreshGate.java'
$refreshTest = Join-Path $probeRoot 'tests\OverlayRefreshTest.java'
Invoke-Checked $javaCompiler @('-encoding', 'UTF-8', '-d', "$buildDir\test-classes", $policy, $selectionPolicy, $refreshPolicy, $test, $refreshTest, "$probeRoot\src\cn\xunji\probe\MessageText.java", "$probeRoot\tests\MessageTextTest.java", "$probeRoot\src\cn\xunji\probe\NapCatPolicy.java", "$probeRoot\tests\NapCatPolicyTest.java")
Invoke-Checked $javaTool @('-cp', "$buildDir\test-classes", 'cn.xunji.probe.CapturePolicyTest')
Invoke-Checked $javaTool @('-cp', "$buildDir\test-classes", 'cn.xunji.probe.OverlayRefreshTest')
Invoke-Checked $javaTool @('-cp', "$buildDir\test-classes", 'cn.xunji.probe.MessageTextTest')
Invoke-Checked $javaTool @('-cp', "$buildDir\test-classes", 'cn.xunji.probe.NapCatPolicyTest')
Invoke-Checked $javaCompiler @('-encoding','UTF-8','-d',"$buildDir\test-classes","$probeRoot\src\cn\xunji\probe\ExtractionPolicy.java","$probeRoot\tests\ExtractionPolicyTest.java")
Invoke-Checked $javaTool @('-cp',"$buildDir\test-classes",'cn.xunji.probe.ExtractionPolicyTest')
Invoke-Checked $javaCompiler @('-encoding','UTF-8','-d',"$buildDir\test-classes","$probeRoot\src\cn\xunji\probe\ExtractionPolicy.java","$probeRoot\src\cn\xunji\probe\CardPolicy.java","$probeRoot\tests\CardPolicyTest.java")
Invoke-Checked $javaTool @('-cp',"$buildDir\test-classes",'cn.xunji.probe.CardPolicyTest')
Invoke-Checked $javaCompiler @('-encoding','UTF-8','-d',"$buildDir\test-classes","$probeRoot\src\cn\xunji\probe\SchedulePolicy.java","$probeRoot\tests\SchedulePolicyTest.java")
Invoke-Checked $javaTool @('-cp',"$buildDir\test-classes",'cn.xunji.probe.SchedulePolicyTest')
Invoke-Checked $javaCompiler @('-encoding','UTF-8','-d',"$buildDir\test-classes","$probeRoot\src\cn\xunji\probe\SchedulePolicy.java","$probeRoot\tests\DeadlinePolicyTest.java")
Invoke-Checked $javaTool @('-cp',"$buildDir\test-classes",'cn.xunji.probe.DeadlinePolicyTest')
Invoke-Checked $javaCompiler @('-encoding','UTF-8','-d',"$buildDir\test-classes","$probeRoot\src\cn\xunji\probe\CoursePolicy.java","$probeRoot\tests\CoursePolicyTest.java")
Invoke-Checked $javaTool @('-cp',"$buildDir\test-classes",'cn.xunji.probe.CoursePolicyTest')
Invoke-Checked $javaCompiler @('-encoding','UTF-8','-d',"$buildDir/test-classes","$probeRoot/src/cn/xunji/probe/SchedulePolicy.java","$probeRoot/src/cn/xunji/probe/EventPolicy.java","$probeRoot/src/cn/xunji/probe/TimeText.java","$probeRoot/tests/EventPolicyTest.java")
Invoke-Checked $javaTool @('-cp',"$buildDir/test-classes",'cn.xunji.probe.EventPolicyTest')
Invoke-Checked "$toolsDir\aapt2.exe" @('compile', '--dir', "$probeRoot\res", '-o', "$buildDir\resources.zip")
Invoke-Checked "$toolsDir\aapt2.exe" @('link', '-o', "$buildDir\unsigned.apk", '-I', $platformJar,
    '--manifest', "$probeRoot\AndroidManifest.xml", '--java', "$buildDir\gen", '-A', "$probeRoot\assets", '--version-code', '37',
    '--version-name', '0.36-delivery-date-consistency', "$buildDir\resources.zip")
$sources = @(Get-ChildItem -LiteralPath "$probeRoot\src", "$buildDir\gen" -Filter '*.java' -Recurse |
    Select-Object -ExpandProperty FullName)
Invoke-Checked $javaCompiler (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options',
    '-classpath', $platformJar, '-d', "$buildDir\classes") + $sources)
Invoke-Checked $jarTool @('cf', "$buildDir\classes.jar", '-C', "$buildDir\classes", '.')
Invoke-Checked "$toolsDir\d8.bat" @('--lib', $platformJar, '--min-api', '28', '--output', "$buildDir\dex", "$buildDir\classes.jar")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::Open("$buildDir\unsigned.apk", 'Update')
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$buildDir\dex\classes.dex", 'classes.dex') | Out-Null
} finally { $archive.Dispose() }
Invoke-Checked "$toolsDir\zipalign.exe" @('-f', '-p', '4', "$buildDir\unsigned.apk", "$buildDir\aligned.apk")
$keyStore = Join-Path $buildDir 'test.keystore'
if (-not (Test-Path -LiteralPath $keyStore)) {
    Invoke-Checked $keyTool @('-genkeypair', '-keystore', $keyStore, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'androiddebugkey', '-dname', 'CN=Xunji Local Test', '-keyalg', 'RSA', '-validity', '3650', '-noprompt')
}
$apk = Join-Path $buildDir 'xunji-notification-test.apk'
Invoke-Checked "$toolsDir\apksigner.bat" @('sign', '--ks', $keyStore, '--ks-pass', 'pass:android',
    '--key-pass', 'pass:android', '--out', $apk, "$buildDir\aligned.apk")
Invoke-Checked "$toolsDir\apksigner.bat" @('verify', '--verbose', $apk)
Invoke-Checked "$toolsDir\aapt.exe" @('dump', 'badging', $apk)
Write-Output "APK ready: $apk"
