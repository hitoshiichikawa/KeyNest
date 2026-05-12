# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-14-impl-feat-edit-sha-256
- HEAD commit: 7f0b1a9d6f418a4e2d566f06c562208c57ca2492
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out (CLAUDE.md 宣言値) — flag 観点の確認は適用外
- tasks.md / design.md は本 spec に存在せず（Architect 未起動の PM→Developer 直行ルート）、
  境界判定は変更ファイル群が Issue スコープ（Edit 画面 + util）に収まっているかを実質基準とした

## Verified Requirements

- 1.1 — `AdvancedDetails.NewMode.expanded=false` + layout `advanced_content` `visibility="gone"`。
  テスト: `CredentialEditAdvancedStateTest.advancedDetails_initialValue_isCollapsedAndNewMode`
- 1.2 — `CredentialEditViewModel.toggleAdvancedExpanded()` + `binding.advancedHeader.setOnClickListener`。
  テスト: `toggleAdvancedExpanded_flipsBetweenCollapsedAndExpanded`
- 1.3 — `renderAdvancedDetails` で `binding.advancedContent.visibility = View.GONE` を切替（同上テストで間接カバー）
- 1.4 — `chevronRotationFor(expanded)` + `binding.advancedChevron.rotation` 設定。
  テスト: `AdvancedSectionChevronTest`（3 ケース、両状態の rotation 不一致まで検証）
- 1.5 — ViewModel-scoped state + `load()` で toggle 保持 (`_advancedDetails.update { current.copy(...) }`)。
  テスト: `load_inEditMode_populatesAllAdvancedFields_andPreservesTogglestate`
- 2.1 — `AdvancedDetails.createdAt/updatedAt` + layout の `value_created_at` / `value_updated_at` 2 行
- 2.2 — `AdvancedDetailsFormatter.formatTimestamp(zone=ZoneId.systemDefault())`。
  テスト: `formatTimestamp_inUtc...`, `formatTimestamp_inJst...`, `formatTimestamp_epochZero...`
- 2.3 — `renderTimestampRow` の `else -> getString(R.string.advanced_value_unsaved)` 分岐
- 2.4 — `createdAt==updatedAt` でも両 row が独立に描画される。
  テスト: `load_inEditMode_whenSignatureMissing_setsHexAndCapturedAtToNull` 内で確認
- 3.1 — `AdvancedDetailsFormatter.formatSha256Hex` + `check(hex.length == SHA256_HEX_LENGTH)`。
  テスト: `formatSha256Hex_returns64CharLowercaseHex_forValidHash`, `..._isAllLowercase...`
- 3.2 — layout `btn_copy_signature_hex` (MaterialButton.OutlinedButton.Icon)
- 3.3 — `copySignatureHexToClipboard` で `clipboard.setPrimaryClip(SignatureClipboardPayload.build(...))`
  + `Toast.makeText(..., message_signature_hex_copied)`。
  テスト: `SignatureClipboardPayloadTest.build_preservesFullHexInClipboardItemText`
- 3.4 — `renderSignatureRow` 内 null branch (`isEnabled=false`, `visibility=GONE`, "Not captured")。
  テスト: `load_inEditMode_whenSignatureMissing_setsHexAndCapturedAtToNull`
- 3.5 — layout `value_signature_hex` に `android:fontFamily="monospace"`。
  テスト: `CredentialEditLayoutAuditTest.signatureHexValue_usesMonospaceFont`
- 4.1 — `renderTimestampRow(binding.valueSignatureCapturedAt, details.signatureCapturedAt, useNotCapturedPlaceholder=true)` 経由で `formatTimestamp` を共有
- 4.2 — 同 `renderTimestampRow` の `useNotCapturedPlaceholder=true` 分岐で "Not captured"
- 5.1 — layout `toggle_credential_id` (SwitchMaterial)
- 5.2 — `AdvancedDetails.NewMode.credentialIdVisible=false`。
  テスト: `advancedDetails_initialValue_isCollapsedAndNewMode`
- 5.3 / 5.4 — `renderCredentialIdRow` 内で `if (details.credentialIdVisible) id.toString() else placeholder`。
  テスト: `toggleCredentialIdVisible_flipsBetweenHiddenAndVisible`
- 5.5 — `renderCredentialIdRow` で `id==null` のとき `rowCredentialId.visibility = View.GONE`。
  テスト: `advancedDetails_initialValue_isCollapsedAndNewMode` で credentialId=null を確認
- 6.1 — 差分は Edit 画面 / util に限定。Save / Update use case、ドメインモデル、Repository、他画面いずれにも非干渉
- 6.2 — toggle は `_advancedDetails` のみ mutate、フォーム state を触らない。
  テスト: `toggleAdvancedExpanded_doesNotResetTransientFormState_norTriggerSave`
- 6.3 — layout `focusable=false`, `textIsSelectable=false`, `longClickable=false`（hex 行は copy 用に selectable 許容）。
  テスト: `CredentialEditLayoutAuditTest.advancedRows_areReadOnly_notFocusableNorEditable`
- NFR 1.1 — `SafeLogger.info(..., "preview=${SafeLogger.previewHex(hex)}")` のみで完全 hex を渡さない。
  テスト: `CredentialEditActivityLogAuditTest.activitySource_doesNotLogFullSignatureHex` /
  `..._doesNotMaterialiseFullHexIntoLoggableInterpolation`
- NFR 1.2 — `SafeLogger.previewHex` 既存実装（先頭 8 文字 + "..."）を採用
- NFR 1.3 — `renderCredentialIdRow` で `credentialIdVisible==false` 時は `••••` placeholder のみ描画、
  `id.toString()` は visible=true でのみ呼ぶ
- NFR 1.4 — Advanced セクションは password 関連 view / state に触れない
- NFR 2.1 — layout `advanced_header` と `toggle_credential_id` に `contentDescription` を付与。
  テスト: `advancedHeader_hasAccessibilityContentDescription` / `credentialIdToggle_hasAccessibilityContentDescription`
- NFR 2.2 — `View.visibility` 変化で Android Framework が AccessibilityEvent を自動発火（実装方針として妥当）
- NFR 2.3 — `btn_copy_signature_hex` / `toggle_credential_id` に `android:minWidth/minHeight="48dp"`。
  テスト: `copyButton_meetsAccessibilityTouchTarget_48dp` / `credentialIdToggle_meetsAccessibilityTouchTarget_48dp`
- NFR 3.1 — すべての文言を `@string/...` で参照（13 個の新規リソース追加）。
  テスト: `allAdvancedSectionStrings_areResources_notLiterals`

## Findings

なし。

## Summary

Issue #14 の AC（Req 1.1–6.3）および NFR 1.1–3.1 はすべて、Edit 画面 / `util` 内の
新規 / 既存ファイルへの追記で実装され、対応するテスト（pure JVM 5 ファイル + Robolectric 1
ファイル、計約 26 ケース）でカバーされている。差分は `app/src/main/java/com/example/keynest/ui/edit`
と `app/src/main/java/com/example/keynest/util` および対応する `app/src/main/res` 配下に閉じ、
既存の Save / Update use case・ドメインモデル・Repository・他画面には非干渉で
Req 6.1（既存挙動保持）を侵犯していない。Feature Flag Protocol は `opt-out` 宣言のため
flag 観点の追加チェックは不適用。AC 未カバー / missing test / boundary 逸脱
いずれも検出されなかった。

RESULT: approve
