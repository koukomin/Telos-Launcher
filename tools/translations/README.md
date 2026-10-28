# Final Telos translation preparation

Run `python tools/translations/prepare.py` to inventory changed/new strings and strings newly exposed by UI changes relative to the handoff base. Base locales inherit gender-specific resources; regional English inherits default English. Existing translations unchanged since the base are flagged again when their English source changed. Greek is checked alongside the other locales.

The **Prepare final Telos translations** workflow defaults to inventory only. PR generation is enabled only when its last commit changes `tools/translations/activation.json` containing `{"generate": true, "functional_changes_complete": true}`. No activation file is checked in by default. Ordinary later pushes do not repeat inference. Manual dispatch is also supported. Enable `generate` **only after the functional changes are finished**, choosing the actual final branch/commit. It never modifies or commits resources automatically. The public runner must have at least 12 GiB RAM. Do not run the 3B model on the interactive development executor.

Inference uses Google's Apache-2.0 MADLAD-400 3B model at pinned revision `fa184c675da0b5c9e1c8694fccd4e12e2d422094`, BF16 CPU weights, sixteen independent locale partitions, two threads per runner, one string per batch and no oneDNN weight packing. Formats, rename templates, escaped characters and product names are protected. Failed/styled strings are reported for manual translation; there are no English fallback placeholders. XML files, cache, missing-key inventory and model/source provenance are uploaded even if generation fails.

Before copying generated resources into `core/i18n/src/main/res`, review meaning (especially delete/restore, permissions and account security), preserve any newer hand-written translations, check all placeholders against English, and run the Android build. A successful inference run proves syntax and token preservation, not semantic accuracy. Translation remains the final development step.

## Completed translations

The new feature strings have been translated directly by parallel Codex assistants for every supported base locale. Run `python tools/translations/validate.py` to check coverage, XML, duplicate IDs, Android quoting and format tokens. English regional variants and gender variants inherit their base resources. Native-speaker and device layout review remain useful; automated checks cannot prove stylistic quality. The optional model workflow writes artifacts only and does not replace these reviewed repository resources.

CI checks changed strings against the pull request base (`TELOS_TRANSLATION_BASE`), plus resource syntax across locales. This keeps new changes accountable without treating unrelated translation debt added on main as part of every PR. Without the environment variable, the historical full-audit baseline is retained.

## Embedded AppOps coverage

The inventory and final validation now cover both `core/i18n` and `libs/appops-next`, using the current release merge (`af228c22344bd1bd214db65d8bb16c502d654113`) as the default comparison base. All 49 host base locales are included, with Greek first. The original Simplified Chinese AppOps resources use their script-qualified folder; generated Traditional Chinese resources use `values-b+zh+Hant`.

Generation keeps module output separate: `translation-artifact/res` contains host resources and `translation-artifact/appops-res` contains embedded AppOps resources. Existing exact English matches can reuse already localized host wording without model inference. Placeholders, resource references, markup tokens and technical names are protected. The same explicit final-source activation gate remains required; no heavy inference runs merely because inventory support changed. Review and copy each module’s artifacts into its own resource root, then run coverage validation and Android builds.
