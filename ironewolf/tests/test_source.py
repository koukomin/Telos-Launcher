# SPDX-License-Identifier: GPL-3.0-or-later
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ironewolf_engine', ROOT / 'engine.py')
engine = importlib.util.module_from_spec(spec)
spec.loader.exec_module(engine)


class SourceContractTest(unittest.TestCase):
    def test_patch_names_are_raw_with_ubuntu_yq(self):
        engine.working_source(engine.checked_source())
        with tempfile.TemporaryDirectory() as directory:
            fake_yq = Path(directory) / 'yq'
            fake_yq.write_text('#!/bin/bash\nif [[ "$1" == "-r" ]]; then echo fixture.patch; else echo \'"fixture.patch"\'; fi\n')
            fake_yq.chmod(0o755)
            env = engine.os.environ.copy()
            env.update(IRONFOX_DIRNAME='/usr/bin/dirname', IRONFOX_PATCH='/usr/bin/patch',
                       IRONFOX_YQ=str(fake_yq), IRONFOX_PATCHES=directory,
                       IRONFOX_UP_AC=str(Path(directory) / 'missing'))
            # Source the real patched driver; only its tool verification helpers are stubbed.
            script = '''set -eu
set_verbosity() { :; }
verify_exec() { :; }
verify_dir_with_env() { :; }
source "$1"
[[ "${PATCH_FILES[0]}" == fixture.patch ]]
[[ "${AS_PATCH_FILES[0]}" == fixture.patch ]]
[[ "${GLEAN_PATCH_FILES[0]}" == fixture.patch ]]
'''
            subprocess.run(['bash', '-c', script, str(engine.SOURCE / 'scripts/prebuild.sh'),
                            str(engine.SOURCE / 'scripts/patches.sh')], env=env, check=True)

    def test_branding_applies_to_locked_upstream_without_changing_original(self):
        pin = engine.checked_source()
        engine.working_source(pin)
        source = engine.SOURCE
        config = (source / 'patches/gecko-overlay/ironfox/ironfox.configure').read_text()
        self.assertIn('return "org.telos.ironewolf"', config)
        build = (source / 'scripts/build-if.sh').read_text()
        self.assertIn('applicationId "org.telos"', build)
        self.assertIn("fenix_app_id_suffix='ironewolf'", build)
        env = (source / 'scripts/env_common.sh').read_text()
        self.assertIn("IRONFOX_NAME='Ironewolf'", env)
        for script in ('scripts/env_common.sh', 'scripts/build-if.sh',
                       'configs/mozconfigs/branding/ironfox.mozconfig'):
            subprocess.run(['bash', '-n', str(source / script)], check=True)
        engine.checked_source()  # Original remains pinned and clean.

    def test_unprepared_or_stale_preparation_cannot_build(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(engine, 'STATE', Path(directory)):
            with self.assertRaisesRegex(RuntimeError, 'not prepared'):
                engine.require_prepared({'commit': '0' * 40})
            (Path(directory) / 'prepared.json').write_text('{}')
            with self.assertRaisesRegex(RuntimeError, 'not prepared'):
                engine.require_prepared({'commit': '0' * 40})

    def test_compiles_source_and_never_signs_or_installs(self):
        with patch.dict(engine.os.environ, {'IRONFOX_CI': '1'}):
            env = engine.environment()
        self.assertEqual(env['IRONFOX_RELEASE'], '1')
        self.assertEqual(env['IRONFOX_SIGN'], '0')
        self.assertEqual(env['IRONFOX_SIGN_SKIP_ADB'], '1')
        self.assertNotIn('IRONFOX_CI', env)
        self.assertEqual(env['IRONEWOLF_ARCHIVE_GECKOVIEW'], '1')


if __name__ == '__main__':
    unittest.main()
