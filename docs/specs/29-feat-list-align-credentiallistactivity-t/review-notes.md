# Review Notes

<!-- idd-claude:review round=2 model=claude-opus-4-7 timestamp=2026-05-14T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-29-impl-feat-list-align-credentiallistactivity-t
- HEAD commit: 749f62610a57b751b5dc8a2d3234fc1cddaf9cfa
- Compared to: develop..HEAD
- Round: 2 / 最大 2（前回 round=1 は `RESULT: reject`）
- Feature Flag Protocol: opt-out（CLAUDE.md `## Feature Flag Protocol` 節 `**採否**: opt-out`）→ flag 観点の確認は適用せず、通常の 3 カテゴリで判定
- spec ディレクトリには `requirements.md` / `impl-notes.md` のみ存在し、`design.md` / `tasks.md` は無い（Architect 不起動の simple Issue 経路）。`_Boundary:_` 境界制約は `tasks.md` 不在のため適用対象外。AC カバレッジと missing test のみで判定
- 本 round では round=1 の Findings 2 件（Finding 1 = Req 8.1 / 8.3 補足文 TextView 欠落、Finding 2 = Req 8.1 / 8.5 visibility 切替の missing test）の解消有無に重点を置きつつ、他 AC のリグレッション有無も再確認

## Verified Requirements

> Round 1 で verify 済みの項目は既に round=1 ノートで詳述済みのため、ここでは
> Round 1 → Round 2 で変更があった項目（Req 8.x）と、リグレッションが無いことを
> 確認した代表的項目を列挙する。詳細は round=1 ノートを参照。

### Round 2 で確認した Round 1 Findings の解消

- **8.1**（Initial → 5 要素 = hero + 見出し + **補足文** + CTA + footer を縦方向中央寄せで描画）— `credential_list_activity.xml` の `empty_state_container`（LinearLayout, gravity=center, orientation=vertical）配下に **5 要素すべてが揃った**:
  1. `empty_state_hero`（FrameLayout: kn_empty_hero_glow + ic_launcher 92dp）
  2. `empty_view`（headline, `Text.KeyNest.TitleM`, text=`credential_list_empty`）
  3. **`empty_state_body`（NEW: `Text.KeyNest.Body` + `kn_text_2` + text=`credential_list_empty_body`）**
  4. `empty_state_cta`（`Widget.KeyNest.Button.Primary` + ic_plus_24 + action_add_credential）
  5. `empty_state_footer`（`Text.KeyNest.Caption` + `kn_text_3` + credential_list_empty_security_note）
  `CredentialListLayoutTokensTest.activityLayout_emptyStateContainsHeroAndPrimaryCta`（既存）+ `activityLayout_emptyStateContainsBodyCopyWithKnText2`（NEW）+ `CredentialListEmptyStateTest.applyEmptyStateVisibility_initial_showsHeroHeadlineBodyCtaAndFooter`（NEW）で 5 要素の存在と VISIBLE 切替を pin
- **8.3**（補足文に `Text.KeyNest.Body` + `kn_text_2`）— `empty_state_body` TextView に `style="@style/Text.KeyNest.Body"` + `android:textColor="@color/kn_text_2"` を **同一要素**で適用。`CredentialListLayoutTokensTest.activityLayout_emptyStateContainsBodyCopyWithKnText2` が `<TextView ... @+id/empty_state_body ... />` ブロック内で両トークンの存在を regex で pin。`CredentialListEmptyStateTest.applyEmptyStateVisibility_initial_bodyTextResolvesEmptyBodyString` が `credential_list_empty_body` の文字列解決を pin
- **8.5**（NoMatch → headline のみ表示、hero / CTA / **body** / footer は非表示）— `EmptyStateRenderer.applyEmptyStateVisibility()` の `NoMatch` 枝で `hero` / `body` / `cta` / `footer` を `View.GONE`、`container` / `headline` のみ `View.VISIBLE` に設定し、`headline.setText(R.string.credential_list_empty_no_match)` を呼ぶ。`CredentialListEmptyStateTest.applyEmptyStateVisibility_noMatch_hidesHeroBodyCtaAndFooter` および `_initialThenNoMatch_flipsBodyAndCtaAndFooterToGone` が visibility と headline text の両方を Robolectric で直接 assert
- **8（boundary）**（`emptyKind == null` → container 全体を GONE）— `applyEmptyStateVisibility()` の `null` 枝で `container.visibility = View.GONE`。`CredentialListEmptyStateTest.applyEmptyStateVisibility_null_hidesTheEntireContainer` が defensive pattern（事前に Initial で VISIBLE にしてから null を投入）で actively flips back to GONE を verify

### Round 1 でカバー済み（Round 2 でリグレッションなし）

- **1.1 / 1.2 / 1.3 / 1.4**（画面ルート / 横余白 / 縦間隔 / dark mode）— layout XML 変更点は `empty_state_body` の追加のみで、ルート背景 / scroll 領域 padding / 縦間隔トークン / hex 直書き禁止に対する違反は無し
- **2.1–2.5**（検索バー）— 本 round では一切変更なし
- **3.1–3.6**（フィルター chip 行）— 本 round では一切変更なし
- **4.1–4.7**（最近使った carousel）— 本 round では一切変更なし
- **5.1–5.9**（メインリスト行カード）— 本 round では一切変更なし
- **6.1–6.5**（強度バー）— `StrengthBar.kt` / `StrengthBarTest.kt` 8 件は無変更
- **7.1–7.3**（署名 chip）— 本 round では一切変更なし
- **8.2**（見出し = TitleM）— `empty_view` の `Text.KeyNest.TitleM` は維持（Round 2 で他の TextView を追加しただけで、headline 自体は変更なし）
- **8.4**（CTA = `Widget.KeyNest.Button.Primary` + `CredentialEditActivity.newIntent` 起動）— `empty_state_cta` および `setUpEmptyStateCta()` の配線は本 round で無変更
- **9.1 / 9.2 / 9.3**（per-row overflow）— 本 round では一切変更なし
- **10.1**（全 View ID 保持）— round 2 で **追加**されたのは `empty_state_body` のみ。既存の `empty_state_container` / `empty_state_hero` / `empty_view` / `empty_state_cta` / `empty_state_footer` および要件で列挙された Issue #9 系 ID（`toolbar` / `input_search` / `layout_search` / `chip_group_filters` / 等）はすべて保持。`activityLayout_preservesAllIssue9Ids` / `rowLayout_preservesIssue9Ids` / `recentLayout_preservesIssue9Ids` は無変更で pass
- **10.2–10.8**（検索 / フィルター / 並び替え / overflow / 行タップ挙動 / SafeLogger / DiffUtil）— `renderEmptyView()` の refactor は visibility 設定ロジックを `applyEmptyStateVisibility()` に extract しただけで、`CredentialListActivity` の他のコールバック配線（`setUpSearch()` / `setUpFilters()` / `setUpSort()` / `setUpRecycler()` / `setUpOverflow()` 等）には変更なし。`CredentialListAdapter.DIFF` / `RecentlyUsedCarouselAdapter.DIFF` も無変更
- **11.1–11.4**（light / dark の `kn_*` 解決 / コントラスト）— `empty_state_body` は `@color/kn_text_2` のみを参照し直接 hex を持たないため、Phase 1 で確認済みの light / dark トークン解決経路をそのまま継承
- **NFR 1.1 / 1.2 / 1.3**（assembleDebug 成功 / 既存テスト維持 / Material3・FontTypefaceWiring の structural pin 違反なし）— impl-notes.md round 2 セクションに `BUILD SUCCESSFUL` および `311 tests completed, 5 failed`（失敗 5 件は Phase 1 pre-existing と一致、本 PR で新たに失敗したテストは無し）を記録
- **NFR 2.1 / 2.2 / 2.3**（48dp タッチサイズ / contentDescription / コントラスト）— `empty_state_body` は非インタラクティブな TextView のため、本 round で 48dp 規定 / contentDescription 規定への影響なし。コントラスト は `kn_text_2` on `kn_surface` (Phase 1 で 4.5:1 以上を満たすトークン値) を継承
- **NFR 3.1**（en / ja 両キー集合）— `credential_list_empty_body` を `values/strings.xml`（英語）と `values-ja/strings.xml`（日本語）の **両方**に追加。既存の `signature_match` / `signature_missing` / `strength_*` / `credential_list_empty_security_note` の en/ja 対称性も維持

## Findings

なし

## Summary

Round 1 で指摘した 2 件の Findings はいずれも完全に解消された。

1. **Finding 1**（Req 8.1 / 8.3 補足文 TextView 欠落 = AC 未カバー）→ `empty_state_body` TextView を `empty_state_container` 内の headline と CTA の間に追加。`Text.KeyNest.Body` + `kn_text_2` + `gravity=center` + `kn_space_3` margin + 新規 string `credential_list_empty_body`（en/ja 両方）を備える。Required Action の全項目（style / textColor / gravity / margin / 新規 string / visibility 切替 / 単体テスト）に完全準拠
2. **Finding 2**（Req 8.1 / 8.5 visibility 切替の missing test）→ `EmptyStateRenderer.kt` に top-level 関数 `applyEmptyStateVisibility()` を extract し、`CredentialListActivity.renderEmptyView()` が delegate する形に refactor。新規テスト `CredentialListEmptyStateTest.kt` で Robolectric (`@RunWith(AndroidJUnit4)` + `@Config(sdk = [33])`) で Initial / NoMatch / null / Initial→NoMatch 状態遷移を 5 件直接 assert。`CredentialListLayoutTokensTest` にも `activityLayout_emptyStateContainsBodyCopyWithKnText2` を追加し、layout 層で `Text.KeyNest.Body` + `kn_text_2` を pin

Round 2 で追加された 6 件のテスト（layout token 1 件 + Robolectric empty state 5 件）はすべて pass、本 PR で新たに失敗したテストは無し（Phase 1 pre-existing failure 5 件のみ）。他 AC（Req 1–7 / 9–11、NFR 1–3）にリグレッションは検出されず、`activityLayout_preservesAllIssue9Ids` 系の ID pin も無変更で pass している。

RESULT: approve
