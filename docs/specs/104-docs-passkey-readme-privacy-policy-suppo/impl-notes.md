# Implementation Notes — Issue #104 (Phase 8: PassKey public docs)

## Scope

Documentation-only change. No app source / tests / resources / build config
touched (NFR 1.1, 1.2). Four files updated:

- `README.md`
- `docs/privacy-policy.md`
- `docs/support.md`
- `CONTRIBUTING.md`

## Changed files

| File | Change |
| --- | --- |
| `README.md` | Added a `PassKey provider` bullet to the Features list, placed after the Autofill-related items and `Custom fields` / `Detected-fields suggestions`, before the Material 3 line. |
| `docs/privacy-policy.md` | Bumped "Last updated" to May 23, 2026; added a new `## PassKeys` section covering AES-GCM + Android Keystore encryption, no off-device transmission, and no export/backup. |
| `docs/support.md` | Bumped "Last updated" to May 23, 2026; replaced the FAQ `TBD.` placeholder with a `### PassKeys` FAQ group (3 Q&A entries). |
| `CONTRIBUTING.md` | Added a `## PassKey development` section (API 34+ prerequisite, no `INTERNET`/network IO, PassKey spelling convention) before `## Sensitive data handling`. |

## AC coverage

| AC | How satisfied |
| --- | --- |
| 1.1 | README Features now states PassKey is provided "via the Android Credential Manager API". |
| 1.2 | New bullet placed after `Custom fields` and `Detected-fields suggestions` (Autofill-related), not at the top (confirmed Option B). Existing bullets unchanged in wording and order. |
| 1.3 | README bullet states "requires Android 14 / API 34 or newer". |
| 1.4 | README new content uses "PassKey" only. |
| 2.1 | Privacy `## PassKeys` states private key material is AES-GCM encrypted with the key stored in Android Keystore. |
| 2.2 | Privacy `## PassKeys` states PassKey data (incl. private keys) never leaves the device / is not transmitted or uploaded to developer or third party. |
| 2.3 | Privacy `## PassKeys` states KeyNest provides no export or backup for PassKeys, matching the existing export-prohibited policy. |
| 2.4 | "Last updated" changed May 18, 2026 → May 23, 2026. |
| 3.1 | Support FAQ "I lost my phone..." entry: PassKeys unrecoverable, re-register on each RP. |
| 3.2 | Support FAQ "Can I move my PassKeys to another device?" entry: not supported, Credential Exchange Protocol not yet implemented. |
| 3.3 | Support FAQ "How do I stop KeyNest from acting as my PassKey provider?" entry: directs to OS Settings (concise + OS-settings guidance, per Open Question 2 safe interpretation). |
| 3.4 | Support FAQ new content uses "PassKey" for KeyNest's feature consistently. |
| 4.1 | CONTRIBUTING states PassKey requires Android 14 (API 34)+, physical device recommended. |
| 4.2 | CONTRIBUTING states PassKey changes must not add `INTERNET` permission or network IO (offline boundary, same instrumentation enforcement). |
| 4.3 | CONTRIBUTING states the PassKey spelling convention (capital P, K). |
| 5.1 | All new feature prose uses "PassKey". See note below on the two intentional exceptions. |
| 5.2 | All additions written in English, matching existing docs. |
| 5.3 | Biometric fallback was not introduced in any new prose, so no fallback wording was needed. (If a future edit references it, use "biometric (or Device Credential: PIN/Pattern if no biometric is set) fallback" per #89.) |
| 5.4 | New content reinforces the on-device-only / no-external-transmission boundary; no contradicting statements added. |
| NFR 1.1 | Only the 4 doc files changed (verified via `git diff --stat`). |
| NFR 1.2 | No code path touched; instrumentation CI unaffected. |
| NFR 2.1 | Front matter (`--- title: ... ---`) and heading hierarchy preserved; additions are new sections or appended bullets. |
| NFR 2.2 | README Documentation/Build/CI/Contributing/Security/License/Acknowledgements sections unchanged in content and order. |

## Implementation judgments

- **Open Question 1 (README bullet granularity):** followed existing Features
  style (bold label + short description). Single bold-labelled bullet with a
  brief multi-clause description, consistent with neighbours.
- **Open Question 2 (OS unregister steps detail):** kept to a concise pointer
  to the OS settings with an example menu path and an explicit "wording varies
  by device and OS version" caveat, rather than hard-coding one OS version's
  exact labels.
- **Req 5.1 intentional non-"PassKey" spellings (acceptable, not violations):**
  1. The spelling-convention sentence in CONTRIBUTING quotes the disallowed
     forms (`"passkey" or "Passkey"`) to define the rule — these are
     meta-references, not feature prose.
  2. The Support unregister FAQ reproduces Android's own OS menu labels
     ("passwords and passkeys section", "Passwords, passkeys & accounts"),
     which are platform-supplied strings. Renaming them would mislead users
     trying to find the menu. The convention governs KeyNest's own PassKey
     references, not verbatim OS labels.

## 確認事項 (open points for PR reviewer / PM)

- Support FAQ unregister steps use an illustrative OS menu path
  (`Settings → Passwords, passkeys & accounts → Preferred service`). The exact
  label differs across OEM skins / Android versions; treated as guidance per
  Open Question 2. Confirm this level of detail is acceptable.
- privacy-policy.md and support.md both had "Last updated: May 18, 2026".
  Req 2.4 only mandates the privacy policy date bump; support.md's date was
  also updated to May 23, 2026 since its content materially changed (FAQ
  added). If the project prefers leaving support.md's date untouched when not
  explicitly required, this can be reverted — flagging for awareness.

## 残課題 / derivable follow-ups

- No design.md / tasks.md exists for this spec (requirements.md only); worked
  directly from AC. No per-task progress tracking applicable.
- Out-of-scope items (SECURITY.md, Terms of Service, store listing,
  translations) intentionally untouched.

STATUS: complete
