# Review Notes

<!-- idd-claude:review round=2 model=claude-opus-4-7 timestamp=2026-05-12T00:00:00Z -->

## Reviewed Scope

- Branch: claude/issue-5-impl-
- HEAD commit: 4b316304e0cbb74510684f992a15942032af5c92
- Compared to: develop..HEAD

注記:
- 本リポジトリには `CLAUDE.md` および `docs/specs/5-/tasks.md` /
  `docs/specs/5-/design.md` が存在しないため、Feature Flag Protocol 採否は
  `opt-out` 相当（無宣言）として扱い、flag 観点の確認は行わなかった。
- `tasks.md` が存在しないため `_Boundary:_` アノテーションによる境界判定は
  行えず、boundary は `requirements.md` Section 2.2 / 4.10.2（UI 層のみ、
  機能ロジック・データ層・Autofill 挙動の変更禁止）から導出した。
- `impl-notes.md` に「Gradle / JDK / Android SDK が環境に無いため
  `./gradlew test` は未実行」と明記されているため AC 4.10.3 / NFR 3.1 の
  最終確認は PjM 環境に委ねる。Reviewer 側も同条件のため、コード差分の
  静的精査 + 新規テストの構造確認に留めた。
- Round 2 は Round 1 で出した 3 件の reject 指摘（Finding 1: AC 4.3.1 /
  4.2.2、Finding 2: AC 4.4.6、Finding 3: AC 4.5.4）の解消を重点的に確認した。

## Verified Requirements

### Round 1 で issue を出していた AC（Round 2 で重点確認）

- 4.3.1 / 4.2.2 — `af3c180` で `credential_list_item.xml` に
  `@+id/text_package` を独立した 3 行目として追加し
  (`fontFamily="monospace"` + `TextAppearance.KeyNest.Mono` + 11sp +
  `kn_text_3` 色)、`CredentialListAdapter.kt` の bind を
  `binding.textSubtitle.text = item.username` /
  `binding.textPackage.text = item.packageName` に分離。これにより
  package 名が独立行で monospace 表示される。`@+id/text_subtitle` /
  `@+id/text_label` は維持され NFR 3.2 違反なし。
- 4.4.6 — `1c5eaa8` で `CredentialEditActivity.kt` の `onCreate` で
  `binding.btnDelete.visibility = if (editingId != null) View.VISIBLE else View.GONE`
  を設定し、`onDeleteClicked()` で `AlertDialog` 確認 → `viewModel.delete(id)`
  を呼び出す経路を実装。`CredentialEditViewModel.kt` に
  `delete(credentialId: Long)` を追加して既存
  `DeleteCredentialUseCase` を呼ぶだけの薄いラッパとし、navigation
  SharedFlow で activity 終了。`ServiceLocator.deleteCredentialUseCase`
  は既存（追加なし）、Factory の 4 引数化に伴い既存テスト
  `CredentialEditViewModelTest` のコンストラクタ呼び出しも 1 行ずつ
  更新（テストの観点は不変）。
- 4.5.4 — `e7b021d` で `drawable/bg_picker_row_selected.xml`（selector:
  `state_selected=true` → `@color/kn_surface_tint`、それ以外
  `@android:color/transparent`）と `drawable/ic_kn_check.xml`
  (primary tint check) を新規作成。`package_picker_row.xml` のルートに
  selector を `android:background` で適用し、ripple は
  `android:foreground="?attr/selectableItemBackground"` に退避。
  行末に `@+id/check_icon` ImageView (`visibility="gone"`、20dp) を追加。
  `PackagePickerBottomSheet.Adapter` を `internal class` に格上げし
  `selectedPosition`（初期 `RecyclerView.NO_POSITION`）を保持、
  `handleRowTap(position, packageName)` で previous / new 双方を
  `notifyItemChanged` した後に既存 `onClick(packageName)` を呼ぶ。
  `bind` で `itemView.isSelected` と `checkIcon.visibility` を反映。
  既存「タップ → dismiss」モデルの順序は完全維持。

### その他の AC（Round 1 で verified、Round 2 でも変化なし）

- 4.1.1 / 4.1.2 / 4.1.3 / 4.1.4 / 4.1.5 / 4.1.6 — トークン反映（Light /
  Dark `colors.xml`、`themes.xml` の `ShapeAppearance.KeyNest.*` 12/16/20/28dp、
  `colorPrimary` 値、hex 直書きゼロ、`windowBackground=?attr/colorSurface`）
- 4.2.1 / 4.2.3 — `TextAppearance.KeyNest.*` 役割の定義、body 系 13sp 以上
- 4.3.2 / 4.3.3 / 4.3.4 / 4.3.5 / 4.3.6 — カード border + 20dp radius、空状態、
  eyebrow + Vault app bar、primary CTA + FAB（`newIntent` 不変）、long-press delete 不変
- 4.4.1 / 4.4.2 / 4.4.3 / 4.4.4 / 4.4.5 / 4.4.7 — Edit AppBar、target app card、
  outlined field、password monospace + `.1em`、visibility toggle、
  `btn_pick_installed_app`
- 4.5.1 / 4.5.2 / 4.5.3 — 28dp top-radius + grab handle、title + eyebrow、
  IconTile + mono package row
- 4.6.1 / 4.6.2 / 4.6.3 — hero + 28sp title + 3 step、primary CTA + 既存 Intent、
  already-enabled chip
- 4.7.1 / 4.7.2 — Dataset row IconTile + 13sp label + 11sp subtitle、id 維持
- 4.8.1 / 4.8.2 — Adaptive icon (Shelter デザイン)
- 4.9.1 / 4.9.2 / 4.9.3 / 4.9.4 / 4.9.5 — 文言更新 + 既存 key 維持
- 4.10.1 / 4.10.2 / 4.10.3 / 4.10.4 — 機能ロジック非編集、Kotlin diff は
  `ui/list/` 2 ファイル + `ui/edit/` 3 ファイル に限定、`domain/` / `data/` /
  `security/` / `autofill/` / `di/` は無変更（`git diff develop..HEAD` で確認）、
  Manifest permission 不変
- NFR 1.1 / 1.3 / 2.1 / 2.2 / 3.2 / 4.2 — タップ領域、contentDescription、
  ダークモード fallback なし、id 維持、network 経路なし

### Round 2 で追加された新規テスト

- `CredentialEditViewModelTest.delete_removesRecord_andEmitsNavigation` —
  AC 4.4.6 の `vm.delete(id)` 経路を Fake repo 経由で検証
  （`repo.snapshot()` empty + `navigation` emit 1 回）
- `PackagePickerSelectionTest`（Robolectric、SDK 33）—
  AC 4.5.4 の `selectedPosition` 振る舞いを 5 ケースで検証:
  初期 `NO_POSITION` / 単発タップ / 行間遷移 /
  `NO_POSITION` ガード / `submitList` での選択リセット

## Findings

なし。

## Summary

Round 1 で reject とした 3 件の Finding（4.3.1 / 4.2.2 の package 行独立 +
monospace、4.4.6 の edit モード delete ボタン wiring、4.5.4 の picker row
選択状態 UI）は、いずれも対応コミット（`af3c180` / `1c5eaa8` / `e7b021d`）で
要件文に沿って実装され、対応する新規テスト 2 本（合計 6 ケース）も追加されて
いる。Kotlin 変更は `ui/` 配下に限定され、`domain/` / `data/` / `security/` /
`autofill/` / `di/` への波及はなく、AC 4.10.2 の境界は維持されている。
ViewModel に追加された `delete()` は既存 `DeleteCredentialUseCase` への
薄いラッパで、UseCase / Repository / DAO 自体は不変。Manifest の permission
にも変更がなく NFR 4.2 / AC 4.10.4 を満たす。テスト実行（`./gradlew
:app:testDebugUnitTest` / `:app:assembleDebug` / `:app:lintDebug`）は
Reviewer 環境にも JDK / SDK が無いため最終確認は PjM 環境に委ねるが、
コード差分・XML well-formedness・id 維持・依存追加なしの観点で実装は
合格水準にある。

RESULT: approve
