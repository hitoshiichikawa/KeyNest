# Implementation Notes

## Requirement Traceability

- Req 2.1, 3.1, 3.2, 3.3, 3.4, 5.3: Task 1 で追加した `ComponentActivity.enableEdgeToEdgeWithKnDefaults()`
  により、edge-to-edge 化と uiMode 連動の icon appearance 設定を一括提供。Req 5.3 の API <27 silent
  degrade は androidx.core 内部実装に委譲（クラッシュなし）。
- 他 Req（1.x / 2.2〜2.5 / 3.5 / 4.x / 5.1, 5.2, 5.4 / NFR）は後続タスク (2, 3, 4) で対処予定。

## Implementation Notes

### Task 1

- 採用方針: design.md の Service Interface 通り、既存 `View.applySystemBarsPadding()` を温存しつつ
  同ファイル `util/EdgeToEdgeInsets.kt` に `ComponentActivity` の拡張関数を 1 つ追加する形を採用。
- 重要な判断: `WindowCompat.getInsetsController(window, window.decorView)` は `ComponentActivity` の
  thisRef を介して `window` プロパティを参照できる（`Activity` 由来）。light/dark の真理値は
  `isAppearanceLightStatusBars = !isNightMode` の単一式で揃えて統一感を持たせた。design.md には
  独立 `private fun Context.isNightMode()` を切る案もあったが、現状の単一呼び出しでは inline 式の
  方が読みやすいと判断し、helper 化は見送り（必要なら後続タスクで refactor 可能）。
- 残存課題: なし（後続 task 3 で本 helper を 6 Activity の onCreate に組み込む）。

### Task 2

- 採用方針: design.md の File Structure Plan 通り、`Theme.KeyNest` 内の非推奨 4 attribute
  (`android:statusBarColor` / `android:navigationBarColor` / `android:windowLightStatusBar` /
  `android:windowLightNavigationBar`) のみを削除。親テーマ・13 textAppearance slot・
  Manrope override・colorScheme・Shape・Widget スタイル群は一切触れず NFR 1.1〜1.3 を担保。
- 重要な判断: 削除した 4 行の上にあった日本語コメント（"Phase 2 の前段階として system bar
  配色を kn_bg に揃える…"）も同時に Issue #128 由来の説明に置換した。元コメントは削除した
  attribute の存在意義を説明するもので、attribute と一体で意味を成すため孤立残置すると
  逆に混乱を招くと判断。新コメントでは「Android 15 で非推奨化したため撤去し、edge-to-edge
  と icon appearance は `enableEdgeToEdgeWithKnDefaults()` 経由で設定する」と次の参照先を
  明示することで、後続タスクとの繋がりを残す。`Theme.KeyNest.Translucent` は元から該当
  attribute なしのため touch せず（design.md 記載通り）。
- 残存課題: なし（task 3 で 6 opaque Activity の onCreate に helper を組み込む、task 4 で
  XML scan テスト追加でこの削除を pinning する予定）。

## 確認事項

- なし

STATUS: complete
