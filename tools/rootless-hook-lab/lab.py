"""Computer-only emulator operations. Never sends writes to a physical phone."""
import os, pathlib, subprocess, sys, time

WORKSPACE=pathlib.Path(__file__).resolve().parents[2]
LAB=WORKSPACE/'.codex/hook-lab'
SDK=pathlib.Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'
ADB=SDK/'platform-tools/adb.exe'
API37='--api37' in sys.argv
if API37:sys.argv.remove('--api37')
SERIAL='emulator-5590' if API37 else 'emulator-5588'
NAME='XunjiRootless17Preflight' if API37 else 'XunjiRootless16'

def adb(*args, timeout=40, check=True):
    if not SERIAL.startswith('emulator-'):raise RuntimeError('Physical devices forbidden')
    if not args or args[0] not in {'shell','install','uninstall','pull','push','logcat','emu'}:raise RuntimeError('Only emulator-scoped lab operations are allowed')
    probe=subprocess.run([str(ADB),'-s',SERIAL,'shell','getprop','ro.kernel.qemu'],capture_output=True,text=True,timeout=10)
    if probe.returncode or probe.stdout.strip()!='1':raise RuntimeError('Target is not a confirmed emulator')
    result=subprocess.run([str(ADB),'-s',SERIAL,*args],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=timeout)
    if check and result.returncode:raise RuntimeError(result.stderr or result.stdout)
    return result

def start():
    image=SDK/'system-images/android-37.0/google_apis_playstore/x86_64' if API37 else LAB/'system-image-36/x86_64'
    if not API37 and not (LAB/'image-verified.json').exists():raise RuntimeError('Official image verification marker missing')
    if not (image/'system.img').exists():raise RuntimeError('Verified image not extracted yet')
    avds=LAB/'avds';directory=avds/(NAME+'.avd');directory.mkdir(parents=True,exist_ok=True)
    config={'avd.ini.encoding':'UTF-8','AvdId':NAME,'avd.ini.displayname':NAME,'abi.type':'x86_64','hw.cpu.arch':'x86_64','hw.cpu.ncore':'2','hw.ramSize':'1536','hw.lcd.width':'720','hw.lcd.height':'1280','hw.lcd.density':'320','hw.gpu.enabled':'yes','hw.gpu.mode':'swiftshader','image.sysdir.1':str(image)+os.sep,'tag.id':'google_apis','tag.display':'Google APIs','PlayStore.enabled':'false','disk.dataPartition.size':'6G','hw.keyboard':'yes','hw.mainKeys':'no','fastboot.forceColdBoot':'yes'}
    (directory/'config.ini').write_text('\n'.join(k+'='+v for k,v in config.items())+'\n')
    (avds/(NAME+'.ini')).write_text('avd.ini.encoding=UTF-8\npath='+str(directory)+'\ntarget=android-'+('37.0' if API37 else '36')+'\n')
    devices=subprocess.run([str(ADB),'devices'],capture_output=True,text=True,timeout=10).stdout
    if SERIAL not in devices:
        env=dict(os.environ,ANDROID_AVD_HOME=str(avds),ANDROID_SDK_ROOT=str(SDK))
        with (LAB/'emulator-stdout.log').open('w') as out,(LAB/'emulator-stderr.log').open('w') as err:
            process=subprocess.Popen([str(SDK/'emulator/emulator.exe'),'-avd',NAME,'-port',SERIAL.split('-')[1],'-no-window','-no-audio','-no-snapshot','-no-boot-anim','-memory','1536','-cores','2','-gpu','swiftshader'],env=env,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)
        (LAB/'emulator-pid.txt').write_text(str(process.pid));print('Dedicated headless emulator started',process.pid,flush=True)
    for i in range(90):
        result=subprocess.run([str(ADB),'-s',SERIAL,'shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=10)
        if result.returncode==0 and result.stdout.strip()=='1':
            print('BOOT_COMPLETED',SERIAL,flush=True)
            for key in ['ro.build.version.release','ro.build.version.sdk','ro.product.cpu.abilist','ro.dalvik.vm.native.bridge']:
                print(key,adb('shell','getprop',key).stdout.strip(),flush=True)
            print('ADB identity',adb('shell','id').stdout.strip(),flush=True);return
        if i%5==0:print('Waiting for isolated emulator boot',i,flush=True)
        time.sleep(2)
    raise RuntimeError('Emulator did not boot within bounded wait; inspect task logs')

if __name__=='__main__':
    if len(sys.argv)==2 and sys.argv[1]=='start':start()
    elif len(sys.argv)>2 and sys.argv[1]=='adb':print(adb(*sys.argv[2:]).stdout,end='')
    else:raise SystemExit('Usage: lab.py start | adb <arguments>')
