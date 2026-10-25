"""Manage Xunji's isolated Windows NapCat demo, without changing the installed QQ."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import urllib.request

BASE = Path(__file__).resolve().parents[2]
STATE = BASE / '.codex' / 'napcat-desktop'
RUNTIME = STATE / 'runtime'
ADB = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
SERIAL = os.environ.get('XUNJI_ADB_SERIAL', '')
sys.stdout.reconfigure(encoding='utf-8', errors='replace')


def owned_processes():
    # Filter by this exact private runtime; never terminate the user's ordinary QQ.
    executable = str(RUNTIME / 'node.exe').replace("'", "''")
    script = ("@(Get-CimInstance Win32_Process | Where-Object { $_.ExecutablePath -eq '"
              + executable + "' } | Select-Object -ExpandProperty ProcessId) | ConvertTo-Json -Compress")
    result = subprocess.run(['powershell', '-NoProfile', '-Command', script], capture_output=True, text=True, check=True)
    value = json.loads(result.stdout) if result.stdout.strip() else []
    return value if isinstance(value, list) else [value]


def device_serial():
    if SERIAL:
        return SERIAL
    output = subprocess.check_output([str(ADB), 'devices'], text=True, encoding='utf-8', errors='replace')
    devices = [line.split()[0] for line in output.splitlines() if len(line.split()) == 2 and line.split()[1] == 'device']
    if len(devices) != 1:
        raise RuntimeError('Connect one authorized phone, or select a device with --serial SERIAL')
    return devices[0]


def link():
    subprocess.run([str(ADB), '-s', device_serial(), 'reverse', 'tcp:3000', 'tcp:13001'], check=True)
    print('USB route: phone 3000 -> desktop 13001')


def request(action):
    tokens = json.loads((STATE / 'local-secrets.json').read_text())
    req = urllib.request.Request('http://127.0.0.1:13001/' + action, b'{}', headers={
        'Content-Type': 'application/json', 'Authorization': 'Bearer ' + tokens['onebot']})
    with urllib.request.urlopen(req, timeout=8) as response:
        result = json.load(response)
    if result.get('status') != 'ok' or result.get('retcode') != 0:
        raise RuntimeError('NapCat action not ready')
    return result['data']


def status():
    print('Owned NapCat processes:', len(owned_processes()))
    try:
        result = request('get_status')
        online = bool(result.get('online') and result.get('good'))
        print('QQ online:', online)
        if online:
            account = str(request('get_login_info').get('user_id', ''))
            if account.isdigit():
                (STATE / 'account.json').write_text(json.dumps({'account': account}))
            print('Group count:', len(request('get_group_list')))
        print('Online status alone does not prove message delivery.')
        return online
    except Exception as error:
        print('OneBot not ready:', type(error).__name__)
        return False


def start():
    if owned_processes():
        print('Using the existing owned NapCat process.')
        link()
        status()
        return
    if not (RUNTIME / 'node.exe').exists():
        raise RuntimeError('Install the verified v4.18.41 Windows Node release first; see README.md')
    module = RUNTIME / 'napcat/napcat.mjs'
    original = module.with_suffix('.mjs.original')
    if not original.exists():
        original.write_bytes(module.read_bytes())
    source = original.read_text(encoding='utf-8')
    for site in ('this.context.wrapper.NodeQQNTWrapperUtil.getNTUserDataInfoConfig()',
                 't.NodeQQNTWrapperUtil.getNTUserDataInfoConfig()'):
        if source.count(site) != 1:
            raise RuntimeError('Unexpected QQ data path source; refusing to start')
        source = source.replace(site, '(process.env.XUNJI_QQ_DATA || ' + site + ')')
    module.write_text(source, encoding='utf-8')
    credential_file = STATE / 'local-secrets.json'
    if not credential_file.exists():
        legacy = STATE.parent / 'napcat-setup/local-secrets.json'
        onebot = json.loads(legacy.read_text())['onebot'] if legacy.exists() else secrets.token_hex(24)
        credential_file.write_text(json.dumps({'onebot': onebot, 'webui': secrets.token_hex(24)}))
    tokens = json.loads(credential_file.read_text())
    config = RUNTIME / 'napcat/config'
    config.mkdir(parents=True, exist_ok=True)
    def save(name, data):
        (config / name).write_text(json.dumps(data, indent=2), encoding='utf-8')
    save('webui.json', {'host': '127.0.0.1', 'port': 16098, 'token': tokens['webui'], 'loginRate': 5})
    save('onebot11.json', {'network': {'httpServers': [{
        'name': 'xunji-usb', 'enable': True, 'host': '127.0.0.1', 'port': 13001,
        'messagePostFormat': 'array', 'token': tokens['onebot'], 'debug': False}],
        'httpClients': [], 'websocketServers': [], 'websocketClients': []},
        'enableLocalFile2Url': False, 'parseMultMsg': False})
    save('napcat.json', {'fileLog': False, 'consoleLog': True,
                        'consoleLogLevel': 'error', 'fileLogLevel': 'error'})
    profile = STATE / 'qq-profile'
    profile.mkdir(exist_ok=True)
    env = os.environ.copy()
    env['NAPCAT_WORKDIR'] = str(RUNTIME / 'napcat')
    env['XUNJI_QQ_DATA'] = str(profile)
    args = [str(RUNTIME / 'node.exe'), str(RUNTIME / 'index.js')]
    account_file = STATE / 'account.json'
    if account_file.exists():
        account = json.loads(account_file.read_text()).get('account', '')
        if str(account).isdigit():
            args.extend(['-q', str(account)])
    with (STATE / 'stdout.log').open('wb') as out, (STATE / 'stderr.log').open('wb') as err:
        process = subprocess.Popen(args, cwd=RUNTIME, env=env, stdout=out, stderr=err,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
    (STATE / 'process.json').write_text(json.dumps({'pid': process.pid, 'runtime': str(RUNTIME)}))
    print('Started owned NapCat PID', process.pid)
    link()
    print('Keep the computer awake and USB connected. Normal desktop QQ must stay exited.')
    print('Check status after login/synchronization; a new login may require scanning a QR code.')


def stop():
    identifiers = owned_processes()
    if identifiers:
        subprocess.run(['powershell', '-NoProfile', '-Command',
                        'Stop-Process -Id ' + ','.join(str(int(pid)) for pid in identifiers)], check=True)
    print('Stopped only the isolated NapCat Node processes.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'status', 'link', 'stop'])
    parser.add_argument('--serial', default=SERIAL, help='ADB device serial; auto-selects the only authorized device otherwise')
    parser.add_argument('--adb', type=Path, default=ADB, help='Path to adb.exe when the SDK is installed elsewhere')
    args = parser.parse_args()
    SERIAL, ADB = args.serial, args.adb
    try:
        {'start': start, 'status': status, 'link': link, 'stop': stop}[args.action]()
    except Exception as error:
        print('ERROR:', str(error))
        sys.exit(1)
