# PoC Notes — Issue #103 / T-02

> Task: `Settings.Secure.getString("credential_service")` の実測。
> 関連: `design.md` §9.1-1 / §9.2 / `tasks.md` T-02

## 環境制約

本 Developer サイクルは sandbox 環境であり、Android 14 emulator (API 34) を
起動して `adb shell settings get secure credential_service` の実値を確認する
ことはできない。そのため本 PoC は **design.md §9.1-1 / §9.2 の AOSP source
分析結果を一次根拠として採用** し、Robolectric 単体テスト上で同等の挙動 (key
書き込み → 自パッケージ含有判定) が再現できることを T-04 で担保することで
代替する。

## 検討した結論パターン

design.md §9.1-1 が提示する 2 つの分岐:

- **(A) 想定通り**: 自パッケージ名が `:` 区切りで `credential_service` に含まれ、
  AOSP §9.1-1 のアルゴリズム通りで判定可能 → design.md §4.2 の判定アルゴリズム
  (`split(":")` → 各 entry `substringBefore("/")` → `packageName` 比較) を
  そのまま採用
- **(B) format が異なる / 別 key**: degraded mode に切替、常に `Disabled` を返す。
  `needs-decisions` で人間にエスカレーション

## 採用パターン: **A (Settings.Secure 経路採用)**

### 根拠

1. **AOSP source 確認 (design.md §9.1-1 が引用)**: Android 14 (API 34) の
   `frameworks/base/packages/SettingsLib` および
   `core/java/android/provider/Settings.java` で、Credential Manager で選択
   された provider は internal key `"credential_service"` に
   `ComponentName.flattenToString()` の `:` 区切りで保存されている。
   - フォーマット例 (Pixel emulator AOSP build):
     `io.github.hitoshiichikawa.keynest/io.github.hitoshiichikawa.keynest.credentialprovider.KeyNestCredentialProviderService:com.google.android.gms/com.google.android.gms.auth.api.credentials.PasswordsService`
   - `:` 区切りで `pkg/component` 形式のエントリが並ぶ
2. **Robolectric Shadow が `Settings.Secure.putString` で in-memory ContentResolver
   に書き込みでき、`Settings.Secure.getString` で読み出せる**ことを T-04 で
   確認する (テストでは AOSP 仕様準拠の format をエミュレートする)
3. **二次フォールバック (B 案 degraded mode)** は **本実装には組み込まない**。
   ただし例外時 (`SecurityException` / `RuntimeException`) は Req 3.5 通り
   `Disabled` を返す safe fallback で graceful degrade する (= 結果的に
   B 案と同等の挙動になる)

### 想定リスクと緩和

| Risk | 緩和 |
|------|------|
| `"credential_service"` key が OEM カスタマイズで別名 | catch → Disabled fallback (Req 3.5) で UI は破綻しない。ユーザーは OS 設定ボタンから確認可 |
| key 不在 (null / 空) | `isNullOrBlank()` 判定で `Disabled` を返す |
| 自パッケージ名がフォーマット末尾 / 先頭問わずマッチ | `:` split + 各エントリ `substringBefore("/")` 比較で位置に依存しない |
| `Settings.Secure` 経路が将来 Android バージョンで読めなくなる | catch-all `Throwable` で `Disabled` fallback (Req 3.5 / NFR 4.3) |

## 実機 / Emulator 検証

本 sandbox サイクルでは Android 14 emulator 起動不可のため、以下の代替で
担保する:

1. **`CredentialProviderStatusCheckerTest` (T-04)** で
   `Settings.Secure.putString(resolver, "credential_service", "<fixture>")`
   を用い、AOSP 仕様準拠の format に対して `Enabled` / `Disabled` を返すことを
   Robolectric `@Config(sdk = [34])` で検証する
2. **例外経路 (`mockkStatic(Settings.Secure::class)` で SecurityException throw)**
   が `Disabled` に正規化されることを確認する
3. **API 33 で OS API を呼ばない**ことを mock の `verify(exactly = 0)` で確認

## 結論

- **採用パターン: A**
- T-03 / T-04 は design.md §4.2 のアルゴリズム通り実装する
- 実機検証 (T-15.4) は本 sandbox サイクルでは実施不可。PR レビュー時に
  人間が API 34 emulator で目視確認することを `impl-notes.md` の確認事項に
  記載する
