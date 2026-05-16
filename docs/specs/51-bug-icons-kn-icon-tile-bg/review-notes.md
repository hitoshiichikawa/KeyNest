# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-05-16T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-51-impl-bug-icons-kn-icon-tile-bg
- HEAD commit: 72f33b08ee7a9f8185b97d384060354b45f633b7
- Compared to: develop..HEAD
- Feature Flag Protocol 採否: opt-out（通常の 3 カテゴリ判定のみ）
- tasks.md / design.md: 未作成（Architect 起動なしの bug 修正 Issue。boundary は requirements.md > Scope > 対象ファイル を根拠に照合）

## Verified Requirements

- 1.1 — `app/src/main/res/layout/credential_list_item.xml` の FrameLayout（@dimen/kn_icon_tile_lg）から `android:background="@drawable/kn_icon_tile_bg"` が削除されている（diff L67-72 / HEAD layout L72-74 で `layout_marginEnd` 直後に `>` が来て background 属性なし）
- 1.2 — `app/src/main/res/layout/credential_list_recent_item.xml` の FrameLayout（36dp）から `android:background="@drawable/kn_icon_tile_bg"` が削除されている（diff で確認）
- 1.3 — `app/src/main/res/layout/package_picker_row_item.xml` の FrameLayout（@dimen/kn_icon_tile_sm）から `android:background="@drawable/kn_icon_tile_bg"` が削除されている（diff で確認）
- 2.1 — 3 layout の親 FrameLayout から background が外れた結果、`IconLoader.loadInto` が `setImageDrawable(realDrawable)` で AdaptiveIconDrawable を適用した際に円形マスク外側が透明になる（layout 改変で達成、`IconLoaderTest.resolve_packageInstalled_returnsPackageManagerDrawable` 既存 pass で実アイコン適用経路は維持）
- 2.2 — `InitialLetterDrawable` は未変更で 12dp 角丸 + kn_blue_500 + 白文字を自己描画（`InitialLetterDrawableTest` 8/8 pass 維持。impl-notes.md より）
- 2.3 — `IconLoader.loadInto` の cache miss 経路で `imageView.setImageDrawable(null)` を呼ぶ既存挙動を維持（IconLoader.kt の diff は KDoc/コメントのみ、Kotlin ロジックは無変更）
- 3.1 — `IconLoaderTest` 11/11 pass（impl-notes.md「実行コマンドと結果」節）
- 3.2 — `InitialLetterDrawableTest` 8/8 pass（同上）
- 3.3 — `PackagePickerLayoutTokensTest` 25/25 pass / `CredentialListLayoutTokensTest` 19/19 pass（同上。後述「Findings なし」の注記参照）
- NFR 1.1 — `IconLoader.DEFAULT_CACHE_CAPACITY = 64` 変更なし（IconLoader.kt diff にロジック変更なし）
- NFR 1.2 — cache hit 経路の同期 `setImageDrawable` 維持（同上）
- NFR 1.3 — `withContext(ioDispatcher)` 経路維持（同上）
- NFR 1.4 — `InitialLetterDrawable` の intrinsic 寸法 / 45% グリフ比率は無変更ファイル

## Findings

なし

## Summary

Issue #46 hotfix 後に残存していた AdaptiveIconDrawable 円形マスク × `kn_icon_tile_bg` 角丸矩形
の境界差分（四隅の青透過）を、対象 3 layout から `android:background` 属性を削除することで
解消している。requirements.md の AC（Req 1.1〜1.3 / 2.1〜2.3 / 3.1〜3.3）はすべて diff と
impl-notes.md のテスト実行結果で観測可能に裏付けられている。`IconLoader.kt` の差分は KDoc / 内部
コメントのみで挙動変更なし、`CredentialListAdapter.kt` の差分も `loadInto` 呼び出し直前の
コメント更新のみ（requirements.md Scope の「Kotlin（必要に応じて）」範囲内とみなせる）。

impl-notes.md 確認事項 1 で Developer が flag した「`PackagePickerLayoutTokensTest` /
`CredentialListLayoutTokensTest` の 3 つの `xml.contains("@drawable/kn_icon_tile_bg")` アサー
ションが、削除理由を記録するコメント本文内の同 substring に偶発一致して pass している」状況は
本 reviewer も diff で確認した（comment 内に `@drawable/kn_icon_tile_bg` 文字列が残っている）。
ただしこれは「視覚仕様 pin が soft match へ格下げされた」観測であり、reviewer の 3 カテゴリ
（AC 未カバー / missing test / boundary 逸脱）いずれにも該当しない:

- requirements.md Req 3.x は「全 pass 維持」を求めており、impl-notes.md の実行結果で全 pass を
  確認できているため AC 未カバーではない
- requirements.md は本変更に対する新規テストの追加 AC を持っていない（Req 3.x はあくまで
  「既存テストの互換」）ため missing test ではない
- 変更ファイルはすべて requirements.md > Scope > 対象ファイル の範疇に収まっているため
  boundary 逸脱ではない

impl-notes.md 確認事項 1 の (a) / (b) / (c) は **spec 側の後続作業**（PM / Architect 起動 +
後続 Issue で token テストの contract 更新）として人間レビュワー / PjM の判断に委ねるべき
領分であり、本 reviewer の reject 根拠ではない。確認事項 2〜4（resolve 中の空白許容 /
InitialLetterDrawable の存在意義 / 旧 BitmapDrawable アイコン）も requirements.md の Out of
Scope または Open Questions として明示済みで、本 PR の AC カバレッジに影響しない。

RESULT: approve
