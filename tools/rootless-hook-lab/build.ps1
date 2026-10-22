param([string]$SdkRoot="$env:LOCALAPPDATA\Android\Sdk")
$ErrorActionPreference='Stop'
$labSource=$PSScriptRoot
$labWork=[IO.Path]::GetFullPath((Join-Path $labSource '../../.codex/hook-lab'))
$labBuild=Join-Path $labWork 'build'
$labTools=Join-Path $SdkRoot 'build-tools/36.0.0'
$labPlatform=Join-Path $SdkRoot 'platforms/android-37.0/android.jar'
$labApi=Join-Path $labWork 'xposed-api-82.jar'
function Checked([string]$Exe,[string[]]$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "$Exe failed: $LASTEXITCODE"}}
$labJavac=(Get-Command javac.exe).Source
$env:JAVA_HOME=Split-Path (Split-Path $labJavac)
New-Item -ItemType Directory -Force -Path $labBuild | Out-Null
$labKey=Join-Path $labBuild 'lab-only.keystore'
if(!(Test-Path -LiteralPath $labKey)){Checked (Get-Command keytool.exe).Source @('-genkeypair','-keystore',$labKey,'-storepass','android','-keypass','android','-alias','lab','-dname','CN=Isolated Hook Lab','-keyalg','RSA','-validity','365','-noprompt')}
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($kind in @('target','module')){
 $out=Join-Path $labBuild $kind
 foreach($dir in @($out,"$out/classes","$out/dex")){New-Item -ItemType Directory -Force -Path $dir | Out-Null}
 $aaptArgs=@('link','-o',"$out/unsigned.apk",'-I',$labPlatform,'--manifest',"$labSource/$kind/AndroidManifest.xml",'--debug-mode')
 if($kind -eq 'module'){$aaptArgs+=@('-A',"$labSource/module/assets")}
 Checked "$labTools/aapt2.exe" $aaptArgs
 $sources=@(Get-ChildItem -LiteralPath "$labSource/$kind" -Filter '*.java' | Select-Object -ExpandProperty FullName)
 Checked $labJavac (@('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-cp',"$labPlatform;$labApi",'-d',"$out/classes")+$sources)
 Checked (Get-Command jar.exe).Source @('cf',"$out/classes.jar",'-C',"$out/classes",'.')
 Checked "$labTools/d8.bat" @('--lib',$labPlatform,'--classpath',$labApi,'--min-api','28','--output',"$out/dex","$out/classes.jar")
 $archive=[IO.Compression.ZipFile]::Open("$out/unsigned.apk",'Update')
 try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,"$out/dex/classes.dex",'classes.dex',[IO.Compression.CompressionLevel]::NoCompression)|Out-Null}finally{$archive.Dispose()}
 Checked "$labTools/zipalign.exe" @('-f','-p','4',"$out/unsigned.apk","$out/aligned.apk")
 Checked "$labTools/apksigner.bat" @('sign','--ks',$labKey,'--ks-key-alias','lab','--ks-pass','pass:android','--key-pass','pass:android','--out',"$labBuild/$kind.apk","$out/aligned.apk")
 Checked "$labTools/apksigner.bat" @('verify',"$labBuild/$kind.apk")
 Write-Output "Built isolated $kind.apk"
}
