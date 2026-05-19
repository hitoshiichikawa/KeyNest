# KeyNest

Offline-first Android password manager. All credentials are stored only
on-device and never transmitted to any external server. Sensitive data is
encrypted with AES-GCM using keys backed by the Android Keystore.

## Features

- **Local-only storage** — no `INTERNET` permission; accidental network IO
  fails fast at runtime
- **Android Autofill Framework** integration with inline suggestions on
  GBoard / supported keyboards
- **Biometric / device credential** unlock on the autofill path
- **Custom fields** for business apps that ask for member number / staff
  ID in addition to the usual username
- **Detected-fields suggestions** so newly registered credentials can pick
  field keys from what your Autofill flow has actually seen
- Material 3 + DayNight theming, adaptive launcher icon

## Documentation

Public-facing pages are hosted on GitHub Pages:

- [Privacy Policy](https://hitoshiichikawa.github.io/KeyNest/privacy-policy)
- [Terms of Service](https://hitoshiichikawa.github.io/KeyNest/terms)
- [Support](https://hitoshiichikawa.github.io/KeyNest/support)

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 17 + Android SDK API 34. `minSdkVersion` is 26.

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

For vulnerability reports, see [SECURITY.md](SECURITY.md).
**Please do not open a public Issue for security-sensitive reports.**

## License

MIT — see [LICENSE](LICENSE).

## Acknowledgements

Powered by [idd-claude](https://github.com/hitoshiichikawa/idd-claude),
an Issue-Driven Development automation framework for Claude Code.
