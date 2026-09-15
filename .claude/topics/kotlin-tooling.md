# Kotlin Tooling

## Build

- Use `./gradlew assemble` to build the jar without running tests.

## ktfmt

- CI runs `infra/script/ktfmt.sh --dry-run --all` on PRs — unformatted Kotlin will fail the check.
- Do NOT run the script with `--all` locally — it would include pre-existing unformatted files and pollute the PR diff.
- After editing Kotlin files, run `infra/script/ktfmt.sh` **before committing** — it formats only files that differ from HEAD in the working tree. Once committed, the script finds nothing; pass the file explicitly (`infra/script/ktfmt.sh path/to/File.kt`) if you forgot. The IntelliJ ktfmt plugin (format on save) is an alternative.

## Detekt

- `./gradlew detekt` does not exist — Detekt runs as a standalone CLI jar in CI, not as a Gradle task.
- CI workflow (`.github/workflows/detekt.yml`) downloads `detekt-cli-1.23.1-all.jar`, creates a baseline from main, then checks the branch against it — only new violations fail.
- To replicate locally: download the jar, run with `--create-baseline` on main, stash/pop, then run again with `--baseline`. Config is at `infra/detekt.yml`.
- When moving code to a new file, carry any `@Suppress` annotations from the original class/method — they don't follow automatically.
