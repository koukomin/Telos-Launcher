#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location('check', 'tools/release-apk-check.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ApkCheckTest(unittest.TestCase):
    def test_selected_native_abi_required(self):
        for entries, valid in [(['lib/armeabi-v7a/engine.so'], True),
                               ([], False),
                               (['lib/arm64-v8a/engine.so'], False),
                               (['lib/armeabi-v7a/engine.so', 'lib/x86/engine.so'], False)]:
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as root:
                apk = Path(root) / 'test.apk'
                with ZipFile(apk, 'w') as archive:
                    for entry in entries:
                        archive.writestr(entry, b'fixture')
                if valid:
                    module.verify(apk, 'armeabi-v7a')
                else:
                    with self.assertRaises(ValueError):
                        module.verify(apk, 'armeabi-v7a')


if __name__ == '__main__':
    unittest.main()
