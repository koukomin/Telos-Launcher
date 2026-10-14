# Final Telos translation preparation

Run `python tools/translations/prepare.py` to inventory changed/new strings and strings newly exposed by UI changes relative to the handoff base. Base locales inherit gender-specific resources; regional English inherits default English. Existing translations unchanged since the base are flagged again when their English source changed. Greek is checked alongside the other locales.

The **Prepare final Telos translations** workflow defaults to inventory only. PR generation is enabled only when its last commit changes `tools/translations/activation.json` containing `{"generate": true, "functional_changes_complete": true}`. No activation file is checked in by default. Ordinary later pushes do not repeat inference. Manual dispatch is also supported. Enable `generate` **only after the functional changes are finished**, choosing the actual final branch/commit. It never modifies or commits resources automatically. The public runner must have at least 12 GiB RAM. Do not run the 3B model on the interactive development executor.

Inference uses Google's Apache-2.0 MADLAD-400 3B model at pinned revision `fa184c675da0b5c9e1c8694fccd4e12e2d422094`, BF16 CPU weights, four independent locale partitions, two threads per runner, one string per batch and no oneDNN weight packing. Formats, rename templates, escaped characters and product names are protected. Failed/styled strings are reported for manual translation; there are no English fallback placeholders. XML files, cache, missing-key inventory and model/source provenance are uploaded even if generation fails.

Before copying generated resources into `core/i18n/src/main/res`, review meaning (especially delete/restore, permissions and account security), preserve any newer hand-written translations, check all placeholders against English, and run the Android build. A successful inference run proves syntax and token preservation, not semantic accuracy. Translation remains the final development step.

## Completed translations

The new feature strings have been translated directly by parallel Codex assistants for every supported base locale. Run `python tools/translations/validate.py` to check coverage, XML, duplicate IDs, Android quoting and format tokens. English regional variants and gender variants inherit their base resources. Native-speaker and device layout review remain useful; automated checks cannot prove stylistic quality. The optional model workflow writes artifacts only and does not replace these reviewed repository resources.
