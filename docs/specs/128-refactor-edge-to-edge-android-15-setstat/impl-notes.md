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

## 確認事項

- なし

STATUS: complete
