# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-23T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-104-impl-docs-passkey-readme-privacy-policy-suppo
- HEAD commit: ae6797cf62a0f2754da32b9affbedbfbc9cbe6a2
- Compared to: develop..HEAD

Notes:
- No `CLAUDE.md` exists in this repo, so no `## Feature Flag Protocol` node is
  declared. Treated as opt-out: flag-observation checks not applied (NFR 1.1).
- No `tasks.md` / `design.md` exists for this spec (requirements.md only). The
  boundary baseline is therefore requirements.md NFR 1 (only the 4 doc files).
  Normal 3-category review applied.

## Verified Requirements

- 1.1 — README.md Features list adds `**PassKey provider** via the Android Credential Manager API` bullet (README.md:18-20).
- 1.2 — New bullet placed after `Custom fields` (line 14-15) and `Detected-fields suggestions` (Autofill-related, line 16-17), before `Material 3` line (line 21); not at top. Existing bullets unchanged in wording/order (README.md:9-21).
- 1.3 — README bullet states "requires Android 14 / API 34 or newer" (README.md:20).
- 1.4 — README new content uses "PassKey" / "PassKeys" only; no lowercase/single-cap forms in added prose.
- 2.1 — docs/privacy-policy.md `## PassKeys` states private key material is AES-GCM encrypted on-device with the encryption key stored in Android Keystore (privacy-policy.md added section).
- 2.2 — Same section states PassKey data incl. private keys "never leaves your device... not transmitted or uploaded to the developer or to any third party".
- 2.3 — Same section states KeyNest provides no export or backup for PassKeys, matching the export-prohibited policy.
- 2.4 — "Last updated" bumped May 18, 2026 → May 23, 2026 in docs/privacy-policy.md.
- 3.1 — docs/support.md FAQ "I lost my phone (or did a factory reset)..." entry: PassKeys unrecoverable, must re-register on each relying party.
- 3.2 — docs/support.md FAQ "Can I move my PassKeys to another device?" entry: cross-device sync/transfer not supported.
- 3.3 — docs/support.md FAQ "How do I stop KeyNest from acting as my PassKey provider?" entry: directs to OS Settings (concise + OS-settings guidance with example path and a "wording varies" caveat).
- 3.4 — Support FAQ new content uses "PassKey" / "PassKeys" for KeyNest's own feature.
- 4.1 — CONTRIBUTING.md `## PassKey development` states Android 14 (API 34)+ required.
- 4.2 — CONTRIBUTING.md states PassKey changes must not add `INTERNET` permission or network IO (offline boundary, same instrumentation enforcement).
- 4.3 — CONTRIBUTING.md states the spelling convention (write as "PassKey", capital P/K).
- 5.1 — All added feature prose uses "PassKey". The two non-conforming spellings are acceptable: the CONTRIBUTING convention sentence quotes the disallowed forms to define the rule (meta-reference), and the Support unregister FAQ reproduces Android's own verbatim OS menu labels ("passwords and passkeys", "Passwords, passkeys & accounts") — both are not KeyNest feature prose, so they do not violate 5.1.
- 5.2 — All additions written in English, matching existing docs.
- 5.3 — No biometric-fallback prose introduced, so no fallback wording was required (5.3 is conditional "Where ... mentioned"; vacuously satisfied).
- 5.4 — New content reinforces on-device-only / no-external-transmission boundary; no contradicting statements added.
- NFR 1.1 — Only the 4 doc files (+ spec docs) changed; no `app/`, `.kt`, `.xml`, `.gradle` touched (verified via `git diff --name-only develop..HEAD`).
- NFR 1.2 — No code path touched; instrumentation CI unaffected.
- NFR 2.1 — Front matter (`--- title: ... ---`) and heading hierarchy preserved; additions are new sections / appended bullets.
- NFR 2.2 — README Documentation/Build/CI/Contributing/Security/License/Acknowledgements sections unchanged in content and order.

## Findings

なし

## Summary

Documentation-only change; all numeric AC (1.1–5.4) and NFRs verified against the diff and existing files. Diff is confined to the 4 documented files (no boundary deviation, no code change so no test required). The two intentional non-"PassKey" spellings are verbatim OS labels and a convention meta-reference, not feature prose, so 5.1 holds.

RESULT: approve
