# Contributing

Contributions are welcome! This project is actively developed in the open.

## Reporting Issues

Open an issue at https://github.com/hitoshiichikawa/KeyNest/issues with
as much detail as you can provide.

- **Bugs**: Android version, KeyNest version (Settings → About), steps to
  reproduce, expected vs. actual behaviour. Logcat snippets help — please
  scrub any credential values before posting.
- **Feature requests**: describe the use case and the expected behaviour.
  If the feature involves a new permission or any network IO, please flag
  that explicitly.

For security-sensitive reports, **do not** open a public Issue — see
[SECURITY.md](SECURITY.md).

## Pull Requests

1. Fork the repository
2. Create a feature branch off `develop` (not `main`)
3. Make your changes with tests
4. Run `./gradlew test` (and `./gradlew lint` if your change touches UI /
   resources)
5. Open a PR against `develop` with a short description of what changed
   and why

`main` is the release branch that is published to Google Play. PRs always
target `develop`; the maintainer batches `develop → main` release PRs.

### CI: instrumentation tests on Android 14 (API 34)

Non-draft PRs targeting `main` / `develop` (and pushes to those branches)
trigger the `Instrumentation Tests (Android 14 / API 34)` GitHub Actions
workflow, which boots an API 34 emulator and runs
`./gradlew connectedDebugAndroidTest`. Mark a PR as **draft** while it is
still WIP to skip the emulator job; flipping it to **ready for review**
re-triggers the workflow.

## Code style

- **Kotlin**: official style (4-space indent, trailing commas allowed, no
  wildcard imports)
- **Single responsibility**: functions do one thing; keep them under
  ~40 lines where practical
- **Tests required** for any logic change (`app/src/test` for unit tests,
  `app/src/androidTest` for instrumentation)
- **Error handling**: wrap in domain-specific exceptions, never swallow
  failures silently
- **No `INTERNET` permission** — this is enforced by an instrumentation
  test (`InternetPermissionAbsenceTest`). Any change that requires network
  IO will be rejected at review

## Sensitive data handling

- Never log credential values (use `SafeLogger`; there is a static audit
  test that fails the build if a `Log.*` call appears in the credential
  path)
- Never commit `.keystore` / `.jks` / API keys / production credentials
- The Room schema is exported under `app/schemas/<applicationId>.data.KeyNestDatabase/`
  and tracked in the repo for migration test reproducibility

## License

By contributing you agree that your contributions will be licensed under
the same [MIT License](LICENSE) that covers the project.
