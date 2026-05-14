# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-14T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-24-impl-bug-credentiallistactivity-illegalargume
- HEAD commit: 744614d83a9990a3e38cdabe3e0ca80ebadbff9f
- Compared to: develop..HEAD
- Feature Flag Protocol: opt-out（CLAUDE.md L233）— flag 観点細目は適用しない
- tasks.md: 存在しない（小規模 bug-fix のため Architect 未起動）。boundary 判定は requirements.md
  の Scope / Out of Scope を基準に評価。

## Verified Requirements

- 1.1 — `Theme.KeyNest` parent が M3 化（themes.xml L110）し、13 個の M3 attribute slot が
  Theme.KeyNest に揃ったため、M3 widget / `?attr/textAppearance*` 参照のクラッシュ根因が
  機械的に消滅。実機検証は impl-notes 確認事項に明記。
- 1.2 — 同上（root cause がコード上で除去された）。
- 1.3 — `Material3ThemeMigrationTest.themeKeyNest_inheritsFromMaterial3DayNightNoActionBar` +
  `themeKeyNest_declaresEveryMaterial3TypeScaleAttributeSlot`。
- 1.4 — `themeKeyNest_declaresEveryMaterial3TypeScaleAttributeSlot`（M3 attribute 13 slot wiring
  を pinning）。
- 2.1 — `Material3ThemeMigrationTest.themeKeyNest_inheritsFromMaterial3DayNightNoActionBar` /
  `themeKeyNest_doesNotInheritFromMaterialComponents`。
- 2.2 — `Material3ThemeMigrationTest.themeKeyNest_declaresEveryMaterial3TypeScaleAttributeSlot`
  / `themeKeyNest_doesNotDeclareAnyMaterial2TextAppearanceSlotName`。
- 2.3 — `Material3ThemeMigrationTest.every13TextAppearanceKeyNestStyle_inheritsFromItsMaterial3Counterpart`
  / `themeKeyNest_doesNotRetainAnyMaterialComponentsTextAppearanceParent`。
- 2.4 — `Material3ThemeMigrationTest.every13TextAppearanceKeyNestStyle_keepsManropeFontFamilyOverride`
  / `FontTypefaceWiringTest.keyNestTheme_overridesAndroidFontFamily_atThemeLevel`。
- 2.5 — `Material3ThemeMigrationTest` 内の `expectedMigrations: List<Triple>` が 13 行の
  M2→M3 公開対応表を明示的に pinning。
- 3.1 — diff stat（`git diff --stat develop..HEAD`）が示す通り、4 対象レイアウト
  （`credential_list_activity.xml` / `settings_activity.xml` / `oss_licenses_item.xml`）は無変更。
  `danger_zone_activity.xml` の 1 行差分はコメント内 `--` → `;` の escape のみで widget/attribute
  参照に影響なし。
- 3.2 — 同上（attribute 参照は書き換えられていない）。
- 3.3 — Manrope override が 13 style + theme-level の双方で保持されていることを
  `every13TextAppearanceKeyNestStyle_keepsManropeFontFamilyOverride` および
  `FontTypefaceWiringTest` が pinning。
- 3.4 — 振る舞いコード（Activity / Repository / UseCase の実装）は無変更。差分は theme XML +
  test compile blocker のみ。
- 3.5 — `Material3ThemeMigrationTest.themeKeyNestTranslucent_isUnchangedByThisMigration`。
- 4.1〜4.9 — 間接担保（Req 2.1 + 2.2 成立により M3 widget・attribute の resolution は決定論的
  に成功）。実機・エミュレータ確認は impl-notes 確認事項 #1 で PR レビュー時の手動検証として
  人間へ委譲（unit test では emulator を起動できないため妥当）。
- 5.1 — impl-notes に `BUILD SUCCESSFUL in 23s` の実測ログ。`danger_zone_activity.xml` の
  `--` escape は `mergeDebugResources` を通すために必須（同種の修正は merged commit `e76c8d9`
  で `credential_edit_activity.xml` に対して既存運用済み）。
- 5.2 — 274/279 が pass。fail 中 5 件は `PackageSignatureResolverTest` /
  `LockedFillResponseSecurityTest` の PackageManager mockk NPE。差分には PackageManager / Autofill /
  Signature 系の touch が一切なく（diff name-only で確認）、theme.xml と原因経路が無関係である
  ため、Issue #24 が「以前 pass していたテストを fail させた」ものではない。Req 5.2 の
  「本修正以前から pass している全テスト」を満たす。
- 5.3 — `FontTypefaceWiringTest` の 4 ケースは M2 attribute 名から M3 attribute 名へ rename
  しただけで assert 強度は維持（同じ「該当 slot が `TextAppearance.KeyNest.*` を指し
  `@font/manrope` を経由」を pinning）。`BundledFontResourcesTest` の `.named()` →
  `assertWithMessage()` 置換は Truth 1.4 API 削除への mechanical 追随で、メッセージ・判定とも同等。
  `ResolveAutofillCandidatesUseCaseTest` の missing override 追加は interface 拡張（Issue #9 / #10）
  への追随で、UseCase の挙動検証意図（NFR 3.2: 下層例外吸収）は維持。いずれも「assert を緩める」
  方向の変更ではない。
- NFR 1.1 / 1.2 — 視覚的回帰の最終判定は実機目視。impl-notes 確認事項 #1 / #2 に明記され
  PR レビュー時の人間判断へ委譲。
- NFR 2.1 — Manrope 適用維持は `Material3ThemeMigrationTest` + `FontTypefaceWiringTest` で pinning。
- NFR 3.1 — Req 1.x と同じく root cause がコード上で除去されていることで担保。

## Findings

なし

## Summary

`Theme.KeyNest` の Material 2 → Material 3 移行が、requirements.md の AC（Req 1〜5 / NFR 1〜3）
すべてに対して、機械検証可能な対応物（`Material3ThemeMigrationTest` 8 ケース新規 +
`FontTypefaceWiringTest` の M3 attribute 名追随）または妥当な人間判断委譲（実機検証項目）で
カバーされている。Req 3.1 / 3.2 が要求する「4 対象レイアウト無変更」も diff stat レベルで充足
（`danger_zone_activity.xml` の差分はコメント内 `--` escape のみで widget/attribute 参照に影響
しない）。peripheral な 3 ファイル（`danger_zone_activity.xml` / `BundledFontResourcesTest` /
`ResolveAutofillCandidatesUseCaseTest`）の修正は Req 5.1 / 5.3 で明示的に許可された「ブロッカー
解除」と「assert を緩めない方向の追随」に該当し、boundary 逸脱に当たらない。Feature Flag
Protocol は opt-out のため flag 観点細目は適用しない。

RESULT: approve
