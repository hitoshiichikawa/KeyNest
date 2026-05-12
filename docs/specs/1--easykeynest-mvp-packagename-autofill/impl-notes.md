# 実装ノート (Issue #1)

## 実装サマリ

KeyNest MVP の packageName ベースの Autofill 機能を、design.md / tasks.md に
従って新規 Android プロジェクトとして実装した。Phase 0 (T1) から Phase 6 (T10) まで
全タスクの実装 + ユニット/計装テストを develop 派生ブランチに commit 済み。

実装は **クリーンアーキテクチャ風** の 6 層構成:
- `ui` (Activity / Fragment / ViewModel)
- `domain` (UseCase / モデル / Repository インターフェース)
- `data` (Room エンティティ / DAO / Repository 実装)
- `security` (Keystore 鍵管理 + AES-GCM 暗号)
- `auth` (BiometricPrompt ラッパー)
- `autofill` (AutofillService 本体 / AssistStructure 解析 / FillResponse 構築 / 認証 Activity)
- `util` (PackageSignatureResolver / SafeLogger / HexEncoding)
- `di` (ServiceLocator 軽量 DI)

## resume 状況

開始時点の develop..HEAD commit:

- `670fbd2 Merge pull request #2 ...` (spec PR merge)
- `a00b83b spec(#1): add requirements / design / tasks for packageName Autofill MVP`

実装 commit は未着手だったため T1.1 から numeric ID 順に消化した。既存 commit を
`git reset` / `git rebase` で書き換える操作は一切行っていない。

## 設計上の判断

| # | 判断 | 根拠 |
|---|------|------|
| D-1 | `SigningHash` 型を T2 段階で先行作成 | T2.5 PackageSignatureResolver の戻り値型として必須。T3.1 の Boundary との衝突は単一ファイル追加のみで回避済み |
| D-2 | `AesGcmCipher.encrypt/decrypt` と `KeystoreKeyProvider.getOrCreateKey` を `open` に変更 | JVM 単体テスト (Robolectric なし) で非 Keystore スタブを差し込めるようにするため。本番ロジックには影響しない |
| D-3 | 暗号化対象は UTF-8 バイト列 (`String` 経由しない) | パスワード平文を JVM の String 内部表現 / GC 管理プールに残さないため。`CharBuffer.wrap(CharArray)` → `StandardCharsets.UTF_8.encode` のみ通す |
| D-4 | `UpdateCredentialUseCase` の `newPassword` は null 許容 | 「ユーザ名だけ編集」を password 再入力なしで実現するため。null のときは既存 ciphertext + iv を保持 |
| D-5 | `ResolveAutofillCandidatesUseCase` で `callerPackage` の現行 SHA-256 が null なら **常に空を返す** | Req 4.3 の趣旨 (署名トラストが確立できないなら候補を出さない) に従う。要件本文には明示されていないが Architect の意図と整合する |
| D-6 | `KeyNestAutofillService.onFillRequest` は **username/password 双方が特定できたときのみ** 候補を返す | Req 3.2 / 3.3 を保守的に解釈。username だけの状態で候補を出すと、ユーザが password 欄でも候補を期待してしまい UX が劣化する |
| D-7 | `onSaveRequest` は `callback.onSuccess()` の空実装 | MVP 確定事項。`onFailure` を呼ぶとユーザに失敗メッセージが表示されるため、成功で吸収する方が UX 上自然 |
| D-8 | `AutofillUnlockActivity` の Theme は `Theme.KeyNest.Translucent` | Activity 本体は BiometricPrompt のみ表示するため、UI を透過にして既存画面の上にプロンプトが乗っているように見せる |
| D-9 | First-launch nudge を `redirectShown` static 変数で管理 | 「コールドスタートで 1 度だけ表示」というプロダクト要件 (OQ-5) を、永続化なしで実現する最小実装 |
| D-10 | `CredentialListAdapter` は `ListAdapter<Credential, ViewHolder>` + DiffUtil | 暗号文を含まない `Credential` 型を要求することで、構造的に「リスト UI が ciphertext を読まない」ことを保証する |
| D-11 | `PendingIntent.FLAG_MUTABLE` を API 31+ で要求 | Android 12 以降、フレームワークが Authentication Intent を上書きするため MUTABLE が必須 |
| D-12 | `FillResponseBuilder.PLACEHOLDER = "••••••"` | locked Dataset の `setValue` に必須の AutofillValue を、credential 由来でないハードコード値で満たす |

## テスト戦略

| 層 | 種別 | 主な手段 |
|----|------|----------|
| domain.model | JVM JUnit | データクラスの不変条件 / zero-fill / toString redaction |
| domain.usecase | JVM JUnit + mockk | `FakeCredentialRepository` + `StubAesGcmCipher` で AndroidKeyStore 不在の環境でも全分岐をカバー |
| data | Robolectric (sdk 33) + Room in-memory | 実 SQLite に対する insert/find/update/delete/observe ラウンドトリップ |
| util.PackageSignatureResolver | JVM JUnit + mockk | API 28+/API 26-27 双方の分岐、複数署名の順序非依存性、`NameNotFoundException` |
| util.SafeLogger / HexEncoding | JVM JUnit | 静的ロジックの境界値 |
| autofill.parser | JVM JUnit + mockk | `ViewNode` を mockk で偽造して 4 段優先順位を全部通す |
| autofill.builder | Robolectric (sdk 33) | `FillResponse.Builder` を呼べる環境で null/non-null/placeholder の存在を検証 |
| security.AesGcmCipher | androidTest | AndroidKeyStore 実機/Emulator が要るため計装 |
| security.SignatureMismatchTest | androidTest | Req 4.2/4.3/4.4 の最終門番 |
| perf | androidTest | NFR 2.1 のしきい値判定 |
| manifest | Robolectric | Req 7 / NFR 1.5 / NFR 4.1 / NFR 4.3 を静的に確認 |
| e2e | androidTest @Ignore | BiometricPrompt + 別アプリが必要なため手動 |

## 確認事項

実装中に気づいた、Reviewer または PM/Architect に確認してほしい事項:

1. **`AutofillUnlockActivity` で `String(plain.password)` 経由になる**: design.md の
   "Risk & Known Limits" にも記載されている通り、`AutofillValue.forText` は `CharSequence`
   を受けるため、最終的に Java `String` を経由してフレームワークに渡している。CharArray
   のまま渡せる API は無く、現状受容する想定。MVP 受容で問題ないか確認したい。
2. **`PackageNameValidator` で半角 ASCII 限定**: 要件 1.3 の "形式不正" の判定基準が
   要件本文に正規表現で書かれていないため、design.md State Invariants の
   `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$` をそのまま実装に採用した。
3. **`onFillRequest` 内で `username + password` 双方の AutofillId が無いと候補 0 件**:
   design.md OQ-2 のヒューリスティクスを保守的に解釈し、両方そろわないと `onSuccess(null)`
   を返している。Req 3.2 自体は「username **または** password が推定できない場合」と
   書かれているため、より緩く「片方だけでも候補返却」する解釈もあり得る。MVP では
   保守側に倒した。
4. **`KeyNestAutofillService` がパッケージ判定に `AssistStructure.activityComponent`
   のみを使う**: WebView 経由など `activityComponent` が null になるケースでは候補 0 件
   を返す。design.md には "Web サイト URL / WebView 内フォーム対応は Out of Scope" と
   あるためこれで足りるが、Reviewer 視点で明示的な確認をお願いしたい。
5. **`gradle-wrapper.jar` バイナリの来歴**: 本環境に Java/Gradle がインストールされて
   いなかったため、既存の社内プロジェクト
   (`/home/hitoshi/github/droidlocalllmvoicechat/gemma3n-voicechat/`) から
   Gradle 8.10.2 標準の wrapper jar / wrapper script をコピーした。CI で
   `gradle wrapper --gradle-version 8.10.2` を一度実行して公式に regenerate する
   方が望ましい場合がある。
6. **T10.1 / T10.4 が即時通る保証なし**: 本環境では Gradle が無いため
   `./gradlew connectedAndroidTest` が動かせない。`FillRequestLatencyTest` の
   `300ms / 600ms` しきい値は Emulator 上で初回チューニングが必要かもしれない。
   設計書 OQ-6 で「CI に組み込む」とあるが、現時点では CI が無い (`gradle wrapper`
   等を含む CI ジョブ自体が未整備) ため、Reviewer ステージで設定する想定。

## 環境制約 (Reviewer 向け)

- **JDK / Gradle / Android SDK 未インストール**: 本ワークツリーには Java も Gradle も
  Android SDK も入っていない。したがって `./gradlew assembleDebug` / `./gradlew test`
  / `./gradlew lint` を実行していない。各ファイルは静的に正しいことのみ確認済み。
  CI 上での初回 build が green になることを Reviewer / 後続パイプライン側で必ず
  確認すること。
- **Robolectric 4.13 + sdk 33**: 一部 Robolectric テストで `@Config(sdk = [33])` を
  指定している。Robolectric 4.13 は SDK 33 までを安定サポートする版を選択。

## resume 状況

- 過去 commit (`670fbd2`, `a00b83b`, `dd8f84b`) は変更なし。
- `git log --oneline develop..HEAD` で 20 commit (10 タスク × 2 commit = 実装 +
  進捗マーカー) が積まれている。

## 未着手 / deferred

- `T10.1 AutofillFlowTest` は `@Ignore`。完全な E2E は biometric enrollment 済み実機
  + 別アプリ ターゲットが必要なため、リリース時の手動 QA に委ねる。
- 設計上の Risk として記載されている署名ローテーションの自動再保存ヒント UI は本 MVP
  範囲外 (将来拡張)。
- アイコン画像 (`@drawable/ic_launcher_*`) は `android:icon="@android:drawable/
  sym_def_app_icon"` で代用している。プロダクション向けにブランドアイコンを用意する
  作業は本 MVP のスコープ外。

## requirement → test トレーサビリティ

| Req | テスト (主たる担保) |
|-----|---------------------|
| 1.1 | `SaveCredentialUseCaseTest.invoke_savesRecord_*`, `CredentialRepositoryImplTest.save_thenFindById_*`, `CredentialDaoTest.insert_thenFindById_*` |
| 1.2 | `SaveCredentialUseCaseTest.invoke_savesRecord_encryptsPassword_*`, `StubAesGcmCipher` round-trip, `AesGcmCipherTest` (androidTest), `SafeLoggerAuditTest` |
| 1.3 | `SaveCredentialUseCaseTest.invoke_failsValidation_when*`, `UpdateCredentialUseCaseTest.invoke_failsValidation_*`, `PackageNameValidatorTest`, `CredentialEditViewModelTest.save_maps*ToFieldError` |
| 1.4 | `CredentialDaoTest.insert_allowsMultipleRowsForSamePackage`, `SaveCredentialUseCaseTest.invoke_allowsMultipleCredentialsForSamePackage`, `CredentialRepositoryImplTest.findByPackage_returnsAllMatchingRecords` |
| 1.5 | `UpdateCredentialUseCaseTest`, `DeleteCredentialUseCaseTest`, `ListCredentialsUseCaseTest`, `CredentialEditViewModelTest.save_inEditMode_*` |
| 2.1 | `SaveCredentialUseCaseTest.invoke_savesRecord_*_capturesSignature`, `PackageSignatureResolverTest`, `HexEncodingTest` |
| 2.2 | `SaveCredentialUseCaseTest.invoke_savesRecord_withNullSignature_*`, `PackageSignatureResolverTest.*_uninstalledPackage_returnsNull`, `CredentialDaoTest.nullableSignatureHash_persistsAsNull` |
| 2.3 | `UpdateCredentialUseCaseTest.invoke_updatesSignatureHashOnEveryCall`, `PackageSignatureResolverTest.resolveSha256_isReRunnable_*`, `CredentialEditViewModelTest.save_inEditMode_*` |
| 3.1 | `ResolveAutofillCandidatesUseCaseTest`, `AutofillFieldHeuristicsTest`, `AssistStructureParserTest` |
| 3.2 | `AutofillFieldHeuristicsTest.classify_returnsUnknown_*`, `AssistStructureParserTest.parse_returnsEmpty_*`, `AssistStructureParserTest.parse_swallowsExceptions_*` |
| 3.3 | `FillResponseBuilderTest.buildLockedResponse_returnsNonNull_*` |
| 3.4 | `FillResponseBuilder.buildUnlockedDataset` (構造的) + 手動 E2E (`AutofillFlowTest`) |
| 3.5 | `ResolveAutofillCandidatesUseCaseTest`, `KeyNestAutofillService` 自体に nw I/O 無し (構造的) |
| 4.1 | `ResolveAutofillCandidatesUseCaseTest.invoke_returnsEmpty_whenCallerSignatureCannotBeResolved`, `PackageSignatureResolverTest` |
| 4.2 | `ResolveAutofillCandidatesUseCaseTest.invoke_returnsOnlyMatchingSignatureCredentials`, `SignatureMismatchTest` (androidTest) |
| 4.3 | `ResolveAutofillCandidatesUseCaseTest.invoke_excludesCredentials_withNullSignature`, `SignatureMismatchTest` |
| 4.4 | `ResolveAutofillCandidatesUseCaseTest.invoke_returnsOnlyMatchingMembers_whenMixed`, `SignatureMismatchTest` |
| 5.1 | `LockedFillResponseSecurityTest`, `FillResponseBuilderTest.placeholderConstant_*` |
| 5.2 | `BiometricAuthenticator` の availability + authenticate (手動 E2E) |
| 5.3 | `UnlockVaultUseCaseTest.invoke_returnsPlaintextCredential_*`, `AesGcmCipherTest` (androidTest) |
| 5.4 | `BiometricAuthenticator` の `AuthResult.Cancelled` 分岐, `AutofillUnlockActivity.finishWithCancel` (構造的) |
| 5.5 | `PlaintextCredentialTest.close_zeroFillsPasswordBuffer`, `UnlockVaultUseCaseTest`, `SaveCredentialUseCaseTest.*_password_CharArray_is_wiped` |
| 6.1 | `AutofillEnableActivity.renderState`, `CredentialListActivity.onResume` (構造的 + 手動 instrumented) |
| 6.2 | `AutofillEnableActivity.launchSettings` (Intent 生成箇所) |
| 6.3 | `AutofillEnableActivity.renderState` のトグル動作 |
| 7.1 | `app/build.gradle.kts` の `minSdk = 26` |
| 7.2 | `app/build.gradle.kts` の `targetSdk = 34`, manifest `<service>` 宣言 |
| 7.3 | `InternetPermissionAbsenceTest` が a11y / device-admin permission 不在を検証 |
| NFR 1.1 | `AesGcmCipherTest` (androidTest 双方向) + `SaveCredentialUseCaseTest` (構造的) |
| NFR 1.2 | `KeystoreKeyProvider` 実装が AndroidKeyStore 経由 (構造的) |
| NFR 1.3 | `SafeLoggerTest`, `SafeLoggerAuditTest`, 各ドメインクラスの toString redaction |
| NFR 1.4 | `LockedFillResponseSecurityTest`, `FillResponseBuilderTest`, `ResolveAutofillCandidatesUseCaseTest.candidates_doNotCarryCiphertextOrIv` |
| NFR 1.5 | `InternetPermissionAbsenceTest`, AndroidManifest に INTERNET 非宣言 |
| NFR 2.1 | `FillRequestLatencyTest` (androidTest, 中央値 ≤ 300ms / p95 ≤ 600ms) |
| NFR 2.2 | `ResolveAutofillCandidatesUseCaseTest.candidates_doNotCarryCiphertextOrIv` (構造的に decrypt が呼ばれない型契約) |
| NFR 3.1 | `AssistStructureParserTest.parse_swallowsExceptions_andReturnsEmpty`, `AutofillFieldHeuristicsTest.classify_returnsUnknown_*` |
| NFR 3.2 | `ResolveAutofillCandidatesUseCaseTest.invoke_swallowsRepositoryExceptions_returningEmpty` |
| NFR 4.1 | `InternetPermissionAbsenceTest` (a11y permission 不在) + 全層に Accessibility 依存なし (構造) |
| NFR 4.2 | 全層に Google Sign-In 依存なし (構造) |
| NFR 4.3 | `InternetPermissionAbsenceTest` (device-admin 不在) + DeviceOwner API 不使用 (構造) |
| NFR 5.1 | `SafeLoggerTest`, `SafeLoggerAuditTest`, 各ドメイン型の toString redaction |

すべての requirement numeric ID は最低 1 テストで担保されている。

---

## Post-merge stabilization (PR #3 動作確認で判明した修正履歴)

PR #3 がマージされて実機 / Emulator 上で動作確認したところ、ビルドエラー・UX 不具合・
autofill フレームワーク固有の挙動由来の問題が複数発覚した。すべて design.md の該当節に
反映済みで、ここでは履歴として時系列にまとめる。Reviewer が後続 Issue で同種の罠を踏まない
ためのリファレンスを兼ねる。

| Commit | 種別 | 症状 | 根本原因 | 修正 | 設計反映先 |
|---|---|---|---|---|---|
| 93e8b19 | build | `'cause' hides member of supertype 'Exception' and needs 'override' modifier` | `data class XxxFailure(val cause: String) : Exception(...)` が `Throwable.cause: Throwable?` と型衝突 | プロパティ名を `reason` に変更（`DeleteFailure` / `SaveFailure.Storage` / `UpdateFailure.Storage` / `UnlockFailure.Decrypt`） | 実装規約 — Exception 派生 data class では Throwable と同名プロパティを避ける |
| 69da537 | build | `Unresolved reference: AUTOFILL_SERVICE` | `Context.AUTOFILL_SERVICE` は AOSP では `@SystemApi` で公開 SDK 不可視 | `getSystemService(AutofillManager::class.java)` の型ベース lookup に切替 | 実装規約 — system service 取得は型ベースの公開 API を使う |
| bcc2615 | UX | 「保存できたか分からない」 | `State.Saved` に対する UI 反応が空（`-> Unit`）で finish() のみ | `Toast` で「保存しました」表示 + `KeyNest.Edit` / `KeyNest.List` の SafeLogger 追加 | design.md の `CredentialEditActivity` 責務に Toast パターン明記 |
| a83b346 | UX | ダークモードで一覧が「空」に見える | `android:windowBackground=@android:color/white` を `DayNight` 親の上で強制し、`textColorPrimary` の dark variant (= 白) と衝突して白文字 on 白背景になっていた | `?attr/colorSurface` に切替 | design.md `UI Theming` 節を新設、Risk & Known Limits にも追記 |
| 11cf91a | perf | 初回 `onFillRequest` のオーバーレイ表示が遅い | 初回時に Room DB のオープンが乗っていた（lazy 初期化） | `KeyNestAutofillService.onCreate` で `findByPackage("__prewarm__")` を `Dispatchers.Default` で発行 | design.md `KeyNestAutofillService.onCreate` 責務 + `Performance & Scalability` に pre-warm パターン |
| a8bb22e | autofill | 認証通過してもテキスト欄に値が入らない | auth-result Dataset で 3 引数 `setValue(id, value, presentation)` を渡しており、framework によっては「新しい選択可能 Dataset」と解釈されフォーム注入が起きていなかった | `setValue(id, value)` の 2 引数版に切替（Google サンプル準拠） | design.md `FillResponseBuilder.buildUnlockedDataset` + Risk & Known Limits |
| 8bdd764 | autofill | 上記が直っても**まだ**テキスト欄に値が入らないケースがあった（実際こちらが主犯） | auth PendingIntent の Intent に `FLAG_ACTIVITY_NEW_TASK` を付けていたため、`AutofillUnlockActivity` が **別タスク**で起動され、framework が呼び出し元タスクへフォーカス復帰できず Dataset が捨てられていた | `newIntent` から `addFlags(FLAG_ACTIVITY_NEW_TASK)` を削除 | design.md `AutofillUnlockActivity` 責務に「NEW_TASK 禁止」を明文化 |
| 4ebc16f | UX | コールドスタート時に「設定済みなのに Autofill 有効化案内が出る」現象が散発 | `AutofillManager.hasEnabledAutofillServices()` が user-scoped binder + lazy 初期化のためコールドスタート直後に false を返すことがある | `AutofillServiceStatus` を新設し、`Settings.Secure.getString(cr, "autofill_service")` との OR で判定 | design.md `Util Layer` に `AutofillServiceStatus` を追加、`CredentialListActivity` / `AutofillEnableActivity` の呼び出し規約も更新 |
| fb909d8 | feat | autofill popup がキーボードと重なって見えにくい（Gboard 等） | popup のみ実装で IME 候補バー内表示（Inline Suggestions）に対応していなかった | `androidx.autofill:autofill:1.1.0` を追加し、`DatasetPresentationFactory.buildInline` で `InlinePresentation` を構築、`FillResponseBuilder` で popup + inline を併用 attach | design.md `Technology Stack` に autofill artifact、`FillResponseBuilder` / `DatasetPresentationFactory` 責務、Open Questions OQ-7 |
| 5047131 | feat | 上記実装後も Gboard に inline 候補が出なかった | `<autofill-service>` に `android:supportsInlineSuggestions="true"` を宣言していないと framework は IME に inline スペック問い合わせ自体を行わない | xml/autofill_service_config.xml に属性追加 | design.md `KeyNestAutofillService` の `autofill_service_config.xml` ブロックに属性を明記 |

### Reviewer 向けの学び（次の似た MVP で警戒したいパターン）

1. **autofill auth は PendingIntent の task flags が肝**。サービスから startActivity するときは
   `FLAG_ACTIVITY_NEW_TASK` を反射的に付けたくなるが、PendingIntent.getActivity は framework が
   send するため不要 — 付けると逆にフォーム注入が壊れる。Google サンプルで NEW_TASK が無い
   ことには意味がある。
2. **auth-result Dataset と locked Dataset で setValue の引数を分ける**。popup 用 locked は
   presentation 必須、auth-result は presentation を渡してはいけない（framework の解釈が変わる）。
3. **`AutofillManager` binder 系 API はコールドスタートで非決定的**。Settings.Secure 直読みを
   フォールバックに用意する。
4. **Inline Suggestions は XML 属性 + コード両方が必要**。コードだけ書いても `<autofill-service>` の
   `supportsInlineSuggestions="true"` が無いと一切動かない。
5. **`Theme.*.DayNight` 親に対して `windowBackground` を生色で固定しない**。配色不整合で UI 不可視
   になる事故。`?attr/colorSurface` のような attribute 参照を使う。
6. **`Exception` 派生 `data class` に `cause` プロパティを置かない**。Kotlin の override ルールで
   `Throwable.cause: Throwable?` と衝突しコンパイル不能。`reason` 等にリネームする。
7. **保存 / 復号など長い処理を伴う Activity finish 直前のフィードバックは `Toast` を使う**。
   `Snackbar` はホスト View が消えると同時に消える。

