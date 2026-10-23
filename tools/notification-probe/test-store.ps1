param([string]$Serial,[string]$SdkRoot="$env:LOCALAPPDATA\Android\Sdk",[string]$IcsFile)
$ErrorActionPreference='Stop'
if(-not $Serial){throw 'Specify the test device with -Serial. Tests install a separate cn.xunji.cardtests package; they never open the real app data.'}
$probeRoot=$PSScriptRoot
$buildDir=Join-Path $probeRoot 'build\store-tests'
$toolsDir=Join-Path $SdkRoot 'build-tools\36.0.0'
$platformJar=Get-ChildItem (Join-Path $SdkRoot 'platforms') -Filter android.jar -Recurse | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
$env:JAVA_HOME=Split-Path (Split-Path (Get-Command javac.exe).Source)
function Invoke-Checked([string]$File,[string[]]$Arguments){& $File @Arguments;if($LASTEXITCODE -ne 0){throw "$File failed: $LASTEXITCODE"}}
New-Item -ItemType Directory -Force "$buildDir\classes","$buildDir\dex" | Out-Null
Invoke-Checked javac @('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',$platformJar,'-d',"$buildDir\classes","$probeRoot\src\cn\xunji\probe\CardStore.java","$probeRoot\src\cn\xunji\probe\CardPolicy.java","$probeRoot\src\cn\xunji\probe\SchedulePolicy.java","$probeRoot\src\cn\xunji\probe\ExtractionPolicy.java","$probeRoot\src\cn\xunji\probe\CoursePolicy.java","$probeRoot\src\cn\xunji\probe\WakeupImport.java","$probeRoot\src\cn\xunji\probe\IcsImport.java","$probeRoot\tests\StoreInstrumentation.java")
Invoke-Checked jar @('cf',"$buildDir\classes.jar",'-C',"$buildDir\classes",'.')
Invoke-Checked "$toolsDir\d8.bat" @('--lib',$platformJar,'--min-api','28','--output',"$buildDir\dex","$buildDir\classes.jar")
Invoke-Checked "$toolsDir\aapt2.exe" @('link','-o',"$buildDir\unsigned.apk",'-I',$platformJar,'--manifest',"$probeRoot\tests\AndroidManifest.xml")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[System.IO.Compression.ZipFile]::Open("$buildDir\unsigned.apk",'Update')
try{[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,"$buildDir\dex\classes.dex",'classes.dex') | Out-Null;if($IcsFile){[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Resolve-Path -LiteralPath $IcsFile).Path,'assets/private-course.ics') | Out-Null}}finally{$archive.Dispose()}
Invoke-Checked "$toolsDir\zipalign.exe" @('-f','4',"$buildDir\unsigned.apk","$buildDir\aligned.apk")
if(-not(Test-Path "$probeRoot\build\test.keystore")){throw 'Run build.ps1 first to generate the local test signing key.'}
Invoke-Checked "$toolsDir\apksigner.bat" @('sign','--ks',"$probeRoot\build\test.keystore",'--ks-pass','pass:android','--out',"$buildDir\store-tests.apk","$buildDir\aligned.apk")
$adb=Join-Path $SdkRoot 'platform-tools\adb.exe'
Invoke-Checked $adb @('-s',$Serial,'install','--no-streaming','-r','-i','com.android.shell',"$buildDir\store-tests.apk")
$output=& $adb -s $Serial shell am instrument -w cn.xunji.cardtests/cn.xunji.probe.StoreInstrumentation
$output | Write-Output
if($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch 'PASS: .* Android SQLite'){throw 'Android SQLite checks failed'}
