We have only one translations file in use, `infra/src/main/resources/i18n/translations.fi.json` (the .en file is a
stub). Place all UI messages there.

For UI (`ui/`) changes, type-check with `npx tsc --noEmit -p tsconfig.json`, test with `npx jest`, and format with
`npx prettier --write <files>` (config is `.prettierrc.json` at the repo root). UI tests are found under `ui/test/`.

After Kotlin changes, format with `infra/script/ktfmt.sh`. It formats the changed Kotlin files in the workdir state.

Assume by default that the environment is correctly set up both to run integration tests with the gradlew in
`infra/`, and hence, run integration tests whenever appropriate. Assume by default that there's a backend running
and able to be queried at `localhost:8080`.

Avoid writing more comments than the code style already has.

# Geoviite concepts

## Layout assets and layout contexts

We maintain a track layout ("paikannuspohja") consisting of a small number of types of layout assets: Track numbers
(LayoutTrackNumber), location tracks, switches, km posts, operational points.

Layout assets exist in a layout context, and may be visible in other layout contexts by common visibility rules.
Layout asset version rows are keyed by { id, layout_context_id, version }, aka LayoutRowVersion.

Assets have three notions of existence:

- Business-logic lifecycle state: Based on `state`, `state_category` or similar. `includeDeleted` parameters and the
  `LayoutAsset#exists` method reference this.
- Actual existence in a given context at a given time. An asset that only ever existed in a draft context can simply
  be fully deleted. They do leave behind a trace in the asset type's version table, as a version row with the `deleted`
  flag set.
- Cancellation from design, that is, setting `design_asset_state = 'CANCELLED'` on an asset row. Cancellation rows are
  for most purposes treated as the same as the row simply not existing, but for publication validation are treated
  as ordinary publishable changes.

Generally, fetchers that take a LayoutRowVersion return the exact asset row identified by that version, while fetchers
that take a layout context implement the common visibility rules, including the above different notions for whether
an asset exists.

