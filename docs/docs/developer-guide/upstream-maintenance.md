# Upstream maintenance

The daily Sentinel compares approved upstream repositories with the versions and
commits in `.artifacts/UPSTREAM_REGISTRY.json`. Its Actions summary links to
newer releases or commits and reports incomplete checks separately. The same
workflow runs Kotlin and Actions CodeQL analysis and Gradle dependency security
verification.

For an upstream change, use `python tools/ai-cli.py locate <feature>` to find the
Telos entry points. Adapt the smallest relevant source changes in a PR. Record the
upstream revision, license, behavioral differences, and affected checks. Update
the baseline after integrating the change. Keep credentials and signing keys
outside upstream analysis jobs.

The weekly dependency workflow publishes Gradle's dependency report as an artifact.
