# Security Policy

KeyNest stores passwords locally on the device and is therefore a sensitive
piece of software. Vulnerability reports are taken seriously and triaged
as a priority.

## Reporting a Vulnerability

Please use **GitHub's private vulnerability reporting** feature on this
repository:

➡ https://github.com/hitoshiichikawa/KeyNest/security/advisories/new

**Do not open a public GitHub Issue** for security-sensitive reports —
the issue tracker is public and a disclosure there would put existing
users at risk before a fix is available.

You can expect:

- Acknowledgement within 5 business days
- A disclosure timeline agreed once the issue is triaged
- Credit in the release notes (opt-in) once the fix ships

## In Scope

- Authentication / unlock flow flaws (biometric / device credential bypass)
- Encryption / Keystore misuse that exposes credential values
- Data leakage (any network IO, logcat output of sensitive values,
  unintended `IntentSender` exposure, etc.)
- Autofill flow vulnerabilities (locked-state ciphertext exposure,
  cross-app data leakage)
- Migration paths that downgrade encryption guarantees

## Out of Scope

- Issues that require an already-unlocked device with physical access
- Vulnerabilities in third-party libraries (please report to upstream
  first; we can re-raise here once an upstream fix is available)
- Issues that depend on a rooted or otherwise compromised device

## Disclosure

Once a fix is available we will:

1. Release a patched version to Google Play (and tag it on GitHub)
2. Publish a security advisory describing the issue and the affected
   versions
3. Credit the reporter unless they prefer to remain anonymous
