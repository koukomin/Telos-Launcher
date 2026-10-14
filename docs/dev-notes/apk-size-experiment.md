# APK size measurement

Production release and nightly builds retain the existing stable class/member names by default.
`app/app/stable-names.pro` contains that policy. This change does not enable production obfuscation.

The **Measure APK size experiment** workflow builds two arm64 release APKs from the same source:

- baseline: `-Ptelos.obfuscationExperiment=false` (the default);
- candidate: `-Ptelos.obfuscationExperiment=true` (omit only the global name-preservation policy).

Both builds retain every reflection/native library keep rule, every locale, font, asset and feature.
SettingsActivity's class name is explicitly retained because the TV controller compares it with a
string constant. The experiment uses debug signing, no signing secrets and no release/publish job.
Do not install these APKs as production updates. Mapping files and JSON size reports are attached
for seven days; they are not evidence of correct runtime behavior on their own.

Run `python3 tools/performance/apk_size.py path/to.apk --output report.json` to reproduce a report.
The tool streams the APK checksum, reads ZIP metadata without extracting entries, and groups exact
compressed and uncompressed byte counts. ZIP/signing/alignment overhead is reported separately.
Comparisons must use the same ABI, source commit and optional SIP-library availability. Download
size reductions do not imply equivalent improvements in installed size, RAM or launch latency.

Before considering production obfuscation, compare the APKs and validate a minified build on-device,
including cold launch, backup/restore, custom themes, virtual-app routing, PDF, remote storage,
Shizuku/native integrations, media playback and account/session persistence. This experiment does
not remove or narrow dependency keep rules. It also leaves native libraries uncompressed so Android
can continue to map them directly from the APK without an additional extracted copy.

## Measured result, 2026-10-14

[CI run 38632481449](https://github.com/koukomin/Telos-Launcher/actions/runs/38632481449)
built both variants successfully from `adc7d6fe49562bb3594532c329f46b38b3862b87`,
with arm64 native libraries and identical version overrides:

| Variant | APK bytes | Compressed DEX bytes | Uncompressed DEX bytes |
| --- | ---: | ---: | ---: |
| Stable names (default) | 88,840,090 | 12,967,837 | 30,592,808 |
| Experimental obfuscation | 88,708,474 | 12,828,437 | 28,741,248 |

The download reduction is 131,616 bytes (0.148%). Native libraries remain 54,943,256
bytes in both APKs. This small download benefit does not justify enabling name obfuscation
without the device checks listed above; the production default stays unchanged.
The runtime allocation improvements do not materially reduce APK download size.
