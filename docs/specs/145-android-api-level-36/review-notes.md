# Review Notes

<!-- idd-claude:review round=1 model=claude-opus-4-7 timestamp=2026-07-22T00:38:23Z -->

## Reviewed Scope

- Branch: claude/issue-145-impl-android-api-level-36
- HEAD commit: 423cebd7ba0f4c5ec4432b6adbf09453c4c888dc
- Compared to: develop..HEAD
- 種別: design-less impl（`tasks.md` / `design.md` 不在。`_Boundary:_` アノテーション無し）
- Feature Flag Protocol: 対象 repo に `CLAUDE.md` 不在 → opt-out 解釈（通常の 3 カテゴリ判定）
- 変更ファイル: `app/build.gradle.kts` / `gradle.properties` / `docs/specs/145-.../{requirements,impl-notes}.md`（テストソース・実装コードの変更なし）

## Verified Requirements

- 1.1 — `app/build.gradle.kts:64` `targetSdk = 36`（マージド manifest で `targetSdkVersion="36"` を impl-notes が実測記録）
- 1.2 — `app/build.gradle.kts:56` `compileSdk = 36`（`assembleDebug` BUILD SUCCESSFUL / `suppressUnsupportedCompileSdk=36` で警告抑止）
- 1.3 — Play Console 実配信での外部検証項目。`targetSdk=36` 化がポリシー準拠の実装本体。sandbox では検証不能な旨を impl-notes に記録
- 1.4 — 同上（Play Console 側の外部検証。実装本体は `targetSdk=36`）
- 2.1 — `app/build.gradle.kts:60` `minSdk = 26`（未変更。Req 2.1 の「26 以下」を満たす）
- 2.2 — API 26 前提コードに変更なし。既存 unit test 922 件 pass による静的検証
- 2.3 — "unsupported OS version" 相当のエラー生成コードを追加していない（config のみ変更）
- 3.1 — Autofill flow 実装に変更なし。既存 Autofill unit test 群 pass で等価挙動を担保
- 3.2 — Credential Provider（パスキー）実装に変更なし。既存 unit test pass
- 3.3 — 生体認証プロンプト分岐に変更なし。既存 unit test pass
- 3.4 — 各画面（クレデンシャル一覧/編集・設定・Danger Zone・OSS ライセンス・オートフィル有効化）の表示ロジックに変更なし。既存 unit test pass
- 3.5 — API 36 behavior change（edge-to-edge / predictive back）は既存 #128 対応・`enableOnBackInvokedCallback=true` で整備済み。新たな挙動差を生む変更なし
- 4.1 — impl-notes 記録: `./gradlew test` BUILD SUCCESSFUL / testDebugUnitTest & testReleaseUnitTest 各 tests=922 failures=0 errors=0
- 4.2 — `git diff --name-only develop..HEAD` にテストソースの変更なし（test 配下不変を確認）
- 4.3 — pre-existing failure 0 件（該当事案なし。N/A）
- 5.1 — `signingConfigs`（`build.gradle.kts:35-46` の keystore 読み出し）未変更。既存 upload key で従来通り署名
- 5.2 — `versionCode = 3`（未変更）。「3 以上の整数」を満たす。「公開版より厳密に大」は release 時運用手順であり Out of Scope（増分方針は既存運用踏襲）に整合。impl-notes 確認事項でリリース担当へ増分確認を明記
- 5.3 — `applicationId = "io.github.hitoshiichikawa.keynest"`（未変更）
- 5.4 — Play Console 実配信での外部検証項目（target API 以外の拒否理由の不在）。config 不変で既存ポリシー準拠を維持
- NFR 1.1 — `assembleDebug` 成立により release ビルド前段要件を充足。内部テストトラック配信可能な構成レベルを維持
- NFR 1.2 — 実機/emulator 検証はヘッドレス環境で未実施。検証範囲・未実施部分・Play Console 内部テスト配信での最終確認方針を impl-notes に記録（AC が許容する「impl-notes.md に記録する」を充足）
- NFR 2.1 — Room スキーマ / 保存形式 / migration 定義に変更なし。既存 migration test を含む 922 件 pass
- NFR 2.2 — 再入力・再ログインを要求する変更なし（config のみ）

## Findings

なし

## Summary

design-less impl（config のみ変更）。全 numeric AC について、環境内で検証可能なもの（1.1/1.2/2.1/4.x/5.1/5.3/NFR 2.x）は diff・既存テスト・impl-notes 実測で確認でき、Play Console 依存の外部検証項目（1.3/1.4/5.4/NFR 1.1）は `targetSdk=36` 化という実装本体が対応済みで sandbox 検証不能な旨が記録されている。テストソース・実装コードは無変更で Req 4.2 を満たし、新規挙動追加が無いため missing test に該当せず、`_Boundary:_` 不在かつ Out of Scope（minSdk 据え置き・依存 major bump 回避・CI 不変）を逸脱しないため boundary 逸脱も無し。

RESULT: approve