"""Run original/patched controls in the dedicated emulator, never on the phone."""
import json, pathlib, time
import lab

package='cn.xunji.hooklab.target'
build=lab.LAB/'build'
report={'serial':lab.SERIAL,'scope':'Java method hook in a synthetic target; no QQ reception tested'}
for prop in ['ro.build.version.release','ro.build.version.sdk','ro.product.cpu.abilist']:
    report[prop]=lab.adb('shell','getprop',prop).stdout.strip()
report['adb_identity']=lab.adb('shell','id').stdout.strip()
for stage,apk in [('baseline',build/'target.apk'),('patched',build/'proof-target/target-487-lspatched.apk')]:
    lab.adb('uninstall',package,check=False)
    lab.adb('install',str(apk),timeout=60)
    lab.adb('shell','am','start','-W','-n',package+'/.MainActivity',timeout=40)
    result=lab.adb('shell','run-as',package,'cat','files/result.txt').stdout.strip()
    report[stage]=result
    print(stage,result,flush=True)
    if result!=('ORIGINAL' if stage=='baseline' else 'HOOK_OK'):raise RuntimeError('Control result mismatch')
log=lab.adb('logcat','-d','-s','XunjiHookLab:I','AndroidRuntime:E').stdout
report['module_loaded_log']='MODULE_LOADED target' in log
if not report['module_loaded_log']:raise RuntimeError('Missing actual module-load log')
(lab.LAB/('api'+report['ro.build.version.sdk']+'-fixture-report.json')).write_text(json.dumps(report,indent=2))
lab.adb('shell','screencap','-p','/sdcard/hook-lab-proof.png')
lab.adb('pull','/sdcard/hook-lab-proof.png',str(lab.LAB/('api'+report['ro.build.version.sdk']+'-hook-result.png')))
print('PASS: actual original/patched results and module-load log recorded.',flush=True)
