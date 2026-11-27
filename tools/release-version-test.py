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

    def test_main_version_push_publishes(self):
        self.assertEqual(module.validate(self.props, 'push', 'refs/heads/main', ['v1.43.81']), ('v1.43.82', True))
        with self.assertRaises(ValueError):
            module.validate(self.props, 'push', 'refs/heads/topic', [])

    def test_dispatch_requires_main(self):
        with self.assertRaises(ValueError):
            module.validate(self.props, 'workflow_dispatch', 'refs/heads/topic', [])
        self.assertEqual(module.validate(self.props, 'workflow_dispatch', 'refs/heads/main', [])[1], True)

    def test_pr_never_publishes(self):
        self.assertFalse(module.validate(self.props, 'pull_request', 'refs/pull/1/merge', [])[1])

    def test_same_commit_published_release_is_allowed(self):
        self.assertEqual(
            module.validate(self.props, 'push', 'refs/tags/v1.43.82', ['v1.43.81', 'v1.43.82']),
            ('v1.43.82', True),
        )

    def test_main_bumps_past_latest_tag_even_if_source_pin_is_stale(self):
        props = dict(self.props)
        module.select_main_version(props, 'v1.43.90', 'new', lambda _: 2026112801, lambda _: 'old')
        self.assertEqual(props['versionName'], '1.43.91')
        self.assertGreater(int(props['versionCode']), 2026112801)

    def test_rerun_reuses_version_for_same_commit(self):
        props = dict(self.props)
        module.select_main_version(props, 'v1.43.90', 'same', lambda _: 2026112801, lambda _: 'same')
        self.assertEqual(props, {'versionName': '1.43.90', 'versionCode': '2026112801'})

    def test_bad_version_or_code_fails(self):
        for props in [dict(self.props, versionName='1.2'), dict(self.props, versionCode='0')]:
            with self.assertRaises(ValueError):
                module.validate(props, 'pull_request', '', [])

if __name__ == '__main__':
    unittest.main()
