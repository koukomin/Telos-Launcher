#!/usr/bin/env python3
import importlib.util
import unittest

spec = importlib.util.spec_from_file_location('release_version', 'tools/release-version.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ReleaseVersionTest(unittest.TestCase):
    props = {'versionName': '1.43.82', 'versionCode': '2026110506'}

    def test_matching_tag_publishes(self):
        self.assertEqual(module.validate(self.props, 'push', 'refs/tags/v1.43.82', ['v1.0.66', 'v1.43.81']), ('v1.43.82', True))

    def test_mismatched_or_old_tag_fails(self):
        for ref, tags in [('refs/tags/v1.0.67', []), ('refs/tags/v1.43.82', ['v1.43.83'])]:
            with self.assertRaises(ValueError):
                module.validate(self.props, 'push', ref, tags)

    def test_dispatch_requires_main(self):
        with self.assertRaises(ValueError):
            module.validate(self.props, 'workflow_dispatch', 'refs/heads/topic', [])
        self.assertEqual(module.validate(self.props, 'workflow_dispatch', 'refs/heads/main', [])[1], True)

    def test_pr_never_publishes(self):
        self.assertFalse(module.validate(self.props, 'pull_request', 'refs/pull/1/merge', [])[1])

    def test_bad_version_or_code_fails(self):
        for props in [dict(self.props, versionName='1.2'), dict(self.props, versionCode='0')]:
            with self.assertRaises(ValueError):
                module.validate(props, 'pull_request', '', [])

if __name__ == '__main__':
    unittest.main()
