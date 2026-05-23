---
title: Support
---

# Support

Last updated: May 23, 2026

Thank you for using KeyNest. If you need help, have a question, or want to
report a problem, please use one of the channels below.

## Contact

- Email: hitoshi.ichikawa@gmail.com
- GitHub Issues: [https://github.com/hitoshiichikawa/KeyNest/issues](https://github.com/hitoshiichikawa/KeyNest/issues)

Please include your device model, Android version, and a description of
the steps to reproduce the problem when reporting an issue.

## Known Issues

TBD.

A list of currently known issues will be published here as needed.

## Frequently Asked Questions (FAQ)

### PassKeys

**I lost my phone (or did a factory reset). Can I recover my PassKeys?**

No. PassKeys are stored only on the device, and KeyNest provides no export
or backup function for them. If the device is lost, wiped, or reset, the
PassKeys it held cannot be recovered. You will need to register a new
PassKey on each relying party (the website or app) using that service's own
account-recovery or re-registration flow.

**Can I move my PassKeys to another device?**

Not at this time. KeyNest does not support cross-device sync or transfer of
PassKeys, because the Credential Exchange Protocol is not yet implemented.
Each device keeps its own PassKeys, and you register PassKeys separately on
each device you want to use.

**How do I stop KeyNest from acting as my PassKey provider?**

PassKey providers are managed by Android, not inside KeyNest. Open the system
**Settings** app and look for the passwords and passkeys section — depending
on your Android version this is usually under something like
*Settings → Passwords, passkeys & accounts → Preferred service* (the exact
wording varies by device and OS version). From there you can change the
preferred provider or disable KeyNest as a PassKey provider. KeyNest also
offers a shortcut to this system screen from its in-app settings.
