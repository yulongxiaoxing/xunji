import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock

SCRIPT = Path(__file__).resolve().parents[1] / 'desktop.py'


class DesktopDeliveryTests(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('xunji_desktop_test', SCRIPT)
        self.app = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.app)
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.app.STATE = Path(self.temp.name) / '.codex' / 'napcat-desktop'
        self.app.RUNTIME = self.app.STATE / 'runtime'
        self.app.RUNTIME.mkdir(parents=True)
        (self.app.RUNTIME / 'napcat').mkdir()
        (self.app.RUNTIME / 'node.exe').touch()
        (self.app.RUNTIME / 'index.js').touch()
        (self.app.RUNTIME / 'napcat' / 'napcat.mjs').write_text(
            'this.context.wrapper.NodeQQNTWrapperUtil.getNTUserDataInfoConfig();'
            't.NodeQQNTWrapperUtil.getNTUserDataInfoConfig()', encoding='utf-8')

    def start_without_launching(self):
        with patch.object(self.app, 'owned_processes', return_value=[]), \
             patch.object(self.app, 'link'), \
             patch.object(self.app.subprocess, 'Popen', return_value=Mock(pid=123)), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            self.app.start()
        return output.getvalue()

    def test_clean_checkout_generates_private_credentials(self):
        output = self.start_without_launching()
        tokens = json.loads((self.app.STATE / 'local-secrets.json').read_text())
        self.assertGreaterEqual(len(tokens['onebot']), 32)
        self.assertGreaterEqual(len(tokens['webui']), 32)
        self.assertNotEqual(tokens['onebot'], tokens['webui'])
        self.assertNotIn(tokens['onebot'], output)
        config = json.loads((self.app.RUNTIME / 'napcat/config/onebot11.json').read_text())
        self.assertEqual(config['network']['httpServers'][0]['host'], '127.0.0.1')
        self.assertEqual(config['network']['httpServers'][0]['token'], tokens['onebot'])

    def test_existing_credentials_survive_start(self):
        tokens = {'onebot': 'existing-onebot-credential', 'webui': 'existing-webui-credential'}
        (self.app.STATE / 'local-secrets.json').write_text(json.dumps(tokens))
        self.start_without_launching()
        self.assertEqual(json.loads((self.app.STATE / 'local-secrets.json').read_text()), tokens)

    def test_legacy_onebot_is_migrated(self):
        legacy = self.app.STATE.parent / 'napcat-setup'
        legacy.mkdir()
        (legacy / 'local-secrets.json').write_text(json.dumps({'onebot': 'legacy-onebot-credential'}))
        self.start_without_launching()
        tokens = json.loads((self.app.STATE / 'local-secrets.json').read_text())
        self.assertEqual(tokens['onebot'], 'legacy-onebot-credential')

    def test_single_device_auto_selection(self):
        self.app.SERIAL = ''
        with patch.object(self.app.subprocess, 'check_output', return_value='List of devices attached\nphone-1\tdevice\n'), \
             patch.object(self.app.subprocess, 'run') as run, contextlib.redirect_stdout(io.StringIO()):
            self.app.link()
        self.assertEqual(run.call_args.args[0][1:3], ['-s', 'phone-1'])

    def test_ambiguous_devices_require_selection(self):
        self.app.SERIAL = ''
        with patch.object(self.app.subprocess, 'check_output', return_value='List of devices attached\na\tdevice\nb\tdevice\n'), \
             patch.object(self.app.subprocess, 'run') as run:
            with self.assertRaisesRegex(RuntimeError, '--serial'):
                self.app.link()
        run.assert_not_called()

    def test_explicit_device_is_respected(self):
        self.app.SERIAL = 'chosen-device'
        with patch.object(self.app.subprocess, 'run') as run, contextlib.redirect_stdout(io.StringIO()):
            self.app.link()
        self.assertEqual(run.call_args.args[0][1:3], ['-s', 'chosen-device'])


if __name__ == '__main__':
    unittest.main()
