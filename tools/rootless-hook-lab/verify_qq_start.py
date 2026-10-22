"""Compare QQ startup without an account in the computer-only emulator."""
import hashlib, json, sys, time
import lab

package='com.tencent.mobileqq'
report={'serial':lab.SERIAL,'scope':'Startup/interface loading only; no account login or real messages'}
paths=[('original',lab.WORKSPACE/'.codex/hook-research/qq-9.3.70-original.apk'),('patched',lab.LAB/'build/patched-qq/qq-9.3.70-original-487-lspatched.apk')]
for stage,apk in paths:
    print('QQ stage',stage,flush=True)
    resume=stage=='original' and '--resume-original' in sys.argv
    if resume:
        path=lab.adb('shell','pm','path',package).stdout.strip().splitlines()[0].removeprefix('package:')
        actual=lab.adb('shell','sha256sum',path,timeout=45).stdout.split()[0]
        expected=hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()
        if actual!=expected:raise RuntimeError('Installed original does not match control APK')
        print('Resuming verified original APK after background install completed',flush=True)
    else:
        lab.adb('uninstall',package,check=False)
        result=lab.adb('install','--no-streaming',str(apk),timeout=480)
        print('Install',result.stdout.strip(),flush=True)
    lab.adb('logcat','-c')
    resolve=lab.adb('shell','cmd','package','resolve-activity','--brief',package).stdout.strip().splitlines()
    component=next((line for line in reversed(resolve) if line.startswith(package+'/')),None)
    if not component:raise RuntimeError('QQ launcher not found')
    start=lab.adb('shell','am','start','-W','-n',component,timeout=90)
    time.sleep(5)
    process=lab.adb('shell','pidof',package,check=False).stdout.strip()
    logs=lab.adb('logcat','-d','-s','XunjiHookLab:I','AndroidRuntime:E','LSPatch:V','LSPosed:V','LSPosed-Bridge:V').stdout
    (lab.LAB/('qq-'+stage+'-startup.log')).write_text(logs,encoding='utf-8')
    state={'launch_output':start.stdout,'process_running':bool(process),'module_loaded':'MODULE_LOADED QQ interface probe only' in logs,'interface_ok':'QQ_INTERFACE_OK' in logs}
    report[stage]=state
    print(stage,'process_running',state['process_running'],'module_loaded',state['module_loaded'],'interface_ok',state['interface_ok'],flush=True)
    lab.adb('shell','screencap','-p','/sdcard/qq-hook-lab.png')
    lab.adb('pull','/sdcard/qq-hook-lab.png',str(lab.LAB/('qq-'+stage+'-startup.png')))
    (lab.LAB/'qq-startup-report.json').write_text(json.dumps(report,indent=2))
    if not process:raise RuntimeError('QQ process exited; inspect isolated startup logs before proceeding')
print('QQ original/patched startup comparison recorded.',flush=True)
