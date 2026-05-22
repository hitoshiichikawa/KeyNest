# Implementation Notes — Issue #101

> Issue: feat(passkey): 既存 credential 一覧画面に PassKey 表示を統合
> Branch: `claude/issue-101-impl-feat-passkey-credential-passkey`
> Base: `develop`

## 完了サマリ

すべての未完了タスク（T-01〜T-11）を消化した。

| Task | commit | 主成果 |
|---|---|---|
| T-01 | 6a396cd | DAO + Repository `listAll()` + 単体テスト |
| T-02 | dde0937 | sealed `CredentialListItem` + `PasskeyDisplayModel` + `KindFilter` + Sorting + UseCase |
| T-03〜T-07 | dde0937 | ViewModel/UiState 拡張 + Adapter multi-viewType + Activity wire-up + drawable / strings |
| T-08 + T-10 | ceae157 | 新規 ViewModel/Mapper/Sort テスト + 既存テスト型追従 |
| T-09 | 981f3ba | Adapter Robolectric Test（15 ケース） |

検証コマンド結果:
- `./gradlew :app:compileDebugKotlin`: SUCCESSFUL
- `./gradlew :app:assembleDebug`: SUCCESSFUL
- `./gradlew :app:testDebugUnitTest`: 844 tests / 843 pass / 1 fail
  （fail = `AppInfoProviderTest.get_returnsVersionNameFromBuildGradle` の pre-existing failure、
  `versionName=1.0.0` を test は `0.1.0` で期待。develop 由来。本 Issue 範囲外）
- `./gradlew :app:lintDebug`: pre-existing `PackageSignatureResolver.kt`（API level 28 issue）で fail。
  本 Issue 新規ファイル / 変更ファイル由来の new error / warning なし
  （`credential_list_passkey_a11y_icon` が UnusedResources warning 1 件、design §10.1 で
  「将来用途分離のため発番」と確定済の future-reserved key）。

## 進捗マーカーについて

`tasks.md` は `## T-XX` 見出し形式でタスクを定義しており、`- [ ]` のマークダウン
checkbox を **タスク見出し側に持たない**。書き換え禁止領域の規約に従い、本ファイル側で
タスク完了状況を追記する形に統一する（tasks.md は読み取り専用扱い）。

末尾 (line 1267〜) の「既存テスト非破壊チェックリスト」は **PR レビュー時に Reviewer
が記入する欄** で、Developer の進捗マーカーではないため、本実装フェーズでは触らない。

## Task 完了サマリ

## Task T-01: PasskeyDao.listAll + PasskeyRepository.listAll + テスト

- 実装ファイル:
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/data/dao/PasskeyDao.kt`
    （`listAll()` Flow query 追加）
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/repository/PasskeyRepository.kt`
    （`listAll()` interface method 追加）
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/data/repository/PasskeyRepositoryImpl.kt`
    （DAO delegate 実装）
- テスト:
  - 新規 `app/src/test/java/io/github/hitoshiichikawa/keynest/data/PasskeyDaoTest.kt`
    （Robolectric sdk=34 + in-memory Room、6 ケース: 空 emit / 単件 / 複数件 / ordering /
     tiebreaker / discoverable+non-discoverable）
  - 既存 `PasskeyRepositoryTest.kt` に 2 ケース追加（delegate / 空 emit）
- 設計との差分: なし
- 確認事項:
  - `PasskeyDaoTest` で `sampleEntity` の `userHandle` を `credentialId.hashCode()` 由来で
    生成して `(rpId, userHandle)` UNIQUE 制約を回避（design でこの helper の細部は未指定
    だが、複数件 insert を成立させるための必然対応）
  - Turbine は KeyNest 既存リポジトリで未導入のため採用せず、`Flow.first()` で
    シングルショット観測に統一（design §12.5 が Turbine 採用を「導入されていなければ
    本 Issue で追加」と書いていたが、`tasks.md` T-01 は「Turbine が未導入なら本タスク内で
    追加してよい」程度の言及で必須ではない。既存テストとの整合 / 依存最小化を優先し
    Turbine 導入は見送り）
- commit: 6a396cd

## Task T-02: CredentialListItem + PasskeyDisplayModel + KindFilter + Sorting + ListPasskeysUseCase

- 実装ファイル（すべて新規）:
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListItem.kt`
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/PasskeyDisplayModel.kt`
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/KindFilter.kt`
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListSorting.kt`
  - `app/src/main/java/io/github/hitoshiichikawa/keynest/domain/usecase/ListPasskeysUseCase.kt`
- テスト: T-08 で集約（T-02 単体ではコンパイル確認のみ）
- 設計との差分: なし
- 確認事項: なし
- commit: dde0937（T-03〜T-07 と統合）

## Task T-03〜T-07: ViewModel/UiState/Adapter/Activity/ServiceLocator + drawable + strings

複合タスク。tasks.md §T-04 案 A に従い、build green を維持するために 1 commit に集約。

- 実装ファイル:
  - T-03: `CredentialListViewModel.kt`（5 引数 Factory、kindFilter StateFlow、
    sealed when ベースの applyFilter/applySearch/applyKindFilter、`onPasskeyClicked`）
    / `CredentialListUiState.kt`（mainList 型変更、kindFilter / passkeyCount 追加）
  - T-04: `CredentialListAdapter.kt`（multi-viewType、PasswordViewHolder /
    PasskeyViewHolder 内部 class、stableId ベース DiffUtil）
  - T-05: `ic_passkey_24.xml` / `ic_password_24.xml`
  - T-06: 5 件の string key（en/ja/default 3 ファイル）
  - T-07: `ServiceLocator.kt`（listPasskeysUseCase 追加）/
    `CredentialListActivity.kt`（Factory 5 引数、sealed when callback、Snackbar）
- テスト: T-08 + T-10 で集約
- 設計との差分:
  - `inputStateFlow` を `data class InputState` で 1 段経由する形にした（design §4.5.3 が
    "Quad" を file private で declare する案を提示していたが、Kotlin 標準の `data class` の
    方が KDoc / debuggability で勝るため）。
  - PasswordViewHolder で `chipSignature.visibility = View.VISIBLE` /
    `strengthBar.visibility = View.VISIBLE` / `btnOverflow.visibility = View.VISIBLE` を
    bind 時に明示的にリストア（design §4.6.4 では「既存挙動をそのまま」だったが、ViewHolder
    プールから PasskeyViewHolder で隠した View が再利用されるケースを防ぐため defensive で
    visibility を毎回明示）。
- 確認事項:
  - sort popup（`UpdatedAtDesc` / `LabelAsc` / `PackageAsc`）が押された場合の挙動は
    design.md §8.4 / Q-5 のとおり「最終 mainList の並びは常に lastUsedAt DESC, createdAt
    DESC で再ソート」。既存ユーザーが LabelAsc / PackageAsc を選択しても見た目の順序が
    変わらない（lastUsedAt が同値同士の場合の tiebreaker でのみ副次効果）UX 退行を
    PR 本文で明示する必要あり（Q-5 残課題）。
  - PassKey 行で `text_label` に `displayName ?: rpDisplayName ?: rpId` のみが入り、
    PassKey 種別ラベル chip 等は出さない（design §6.3.3 で「追加しない」と確定済）。
- commit: dde0937

## Task T-08 + T-10: Unit tests + 既存テスト追従

T-10 は T-08 と分離した個別 commit にする予定だったが、`CredentialListViewModelTest`
の既存テストすべてが型シグネチャ変更を被るため、T-08 / T-10 を 1 つの commit に集約した。

- 実装ファイル:
  - `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListViewModelTest.kt`
    （rewrite: 既存 password 単独テストの型追従 + Issue #101 新規ケース合計 23 テスト）
  - 新規 `PasskeyDisplayModelTest.kt`（7 テスト、reflection で sensitive 列除外確認 / toString
    から sensitive キーワードが消えていることまで含む）
  - 新規 `CredentialListSortingTest.kt`（12 テスト、null handling / tiebreaker / Long.MAX_VALUE
    境界 / mergeAndSort 4 パターン）
- 設計との差分:
  - `uiState_sortSwitch_reorders` 既存テストを **削除**（design §8.4 の確定: sort popup が
    mainList の順序に直接反映されないため、既存 assertion 形式は本 Issue 設計と矛盾する）。
    削除理由は commit message に明示。
  - `applyFilter` / `applySearch` / `computeEmptyKind` の type 変更を test 側で追従。
    既存 logic（password 部分の挙動）は変えていない（NFR 3.5）。
- 確認事項:
  - `FakePasskeyRepository` は test scope の private inner class として宣言。`listAll()`
    のみ実装し、他 6 メソッドは `UnsupportedOperationException` を throw（誤って呼ばれた
    場合に regression が表出するようにする）。
  - tasks.md T-08 §テストケース一覧で `applySearch_hitsPasskeyRpDisplayName` /
    `applySearch_hitsPasskeyUserDisplayName` を追加（design §15.2 の検索ケース網羅と整合）。
- commit: ceae157

## Task T-09: Adapter Robolectric Test

- 実装ファイル:
  - 新規 `app/src/test/java/io/github/hitoshiichikawa/keynest/ui/list/CredentialListAdapterInstrumentationTest.kt`
    （Robolectric sdk=34、15 テスト）
- 設計との差分:
  - design §15.6 が `EmptyHostActivity` を spin up する案だったが、`ContextThemeWrapper` 経由で
    `Theme_KeyNest` を解決する既存 KeyNest test 慣習（`CredentialListEmptyStateTest`）に合わせて
    Activity launch を回避（Robolectric の Looper / Activity lifecycle ノイズを減らせる）。
  - PassKey 行 fallback chain テスト（textLabel / textSubtitle）で `AsyncListDiffer` の
    非同期挙動を扱うため、**1 ケースあたり fresh adapter を作る** ヘルパー
    `labelTextForPasskey` / `subtitleTextForPasskey` を導入。`shadowOf(Looper.getMainLooper()).idle()`
    で diff 適用を待つ。
- 確認事項: なし
- commit: 981f3ba

## Task T-11: 最終確認（chore commit なし）

- 全 unit test pass を確認（844 tests completed、1 failed は `AppInfoProviderTest` の
  pre-existing failure: develop の `versionName=1.0.0` を test が `0.1.0` 期待で書かれている。
  本 Issue 範囲外）。
- `:app:assembleDebug` 成功。
- `:app:lintDebug` は pre-existing の `PackageSignatureResolver.kt`（API level 28 issue）で
  fail するが、本 Issue 新規ファイル / 変更ファイルからの新規 lint error / warning なし
  （`credential_list_passkey_a11y_icon` の UnusedResources warning 1 件のみ。design §10.1 で
  「将来用途分離のため発番」と確定済の future-reserved key で意図的）。

## 受入基準 (Requirements) との対応

各 requirement numeric ID が、どのテストで担保されているか:

- **R1.1 (同じ RecyclerView で 1 list 表示)**:
  `CredentialListViewModelTest.uiState_combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast`
  + `CredentialListAdapterInstrumentationTest.bindPassword_*` / `bindPasskey_*`（手動 QA は §T-11）
- **R1.2 (種別アイコン切替)**:
  `CredentialListAdapterInstrumentationTest.bindPasskey_paintsIcPasskey24_*`
  + `CredentialListAdapterInstrumentationTest.bindPassword_paintsAppIconViaIconLoader_*`
- **R1.3 (PassKey 行 3 行 fallback)**:
  `CredentialListAdapterInstrumentationTest.bindPasskey_textLabel_fallsBackThroughDisplayName_thenRpDisplayName_thenRpId`
  / `bindPasskey_textSubtitle_fallsBackThroughUserDisplayName_thenUserName_thenUnknownString`
  / `bindPasskey_textPackage_isAlwaysRpId`
- **R1.4 (lastUsedAt DESC, createdAt DESC, NULL last)**:
  `CredentialListSortingTest.*`（7 ケース）
  + `CredentialListViewModelTest.uiState_combine_mergesPasswordsAndPasskeys_byLastUsedDescNullsLast`
- **R1.5 (DiffUtil stableId 衝突防止)**:
  `CredentialListAdapterInstrumentationTest.diffAreItemsTheSame_passwordIdOne_andPasskeyCredentialIdOne_returnsFalse`
- **R1.6 (areContentsTheSame variant 別)**:
  `CredentialListAdapterInstrumentationTest.diffAreItemsTheSame_samePasskeyCredentialId_returnsTrue`
  / `diffAreItemsTheSame_samePasswordId_returnsTrue`
- **R1.7 (Empty state 共通)**: `CredentialListViewModelTest.computeEmptyKind_*` 4 ケース
- **R1.8 (vector drawable 直接設定)**:
  `CredentialListAdapterInstrumentationTest.bindPasskey_paintsIcPasskey24_*`
  + `bindPassword_paintsAppIconViaIconLoader_notSetImageResource`
- **R1.9 (chip_signature / strength_bar / overflow を GONE)**:
  `CredentialListAdapterInstrumentationTest.bindPasskey_paintsIcPasskey24_andHidesChipSignatureStrengthBarAndOverflow`
- **R2.1 (検索 case-insensitive contains 横断)**:
  `CredentialListViewModelTest.applySearch_hitsPasskeyRpId / hitsPasskeyRpDisplayName / hitsPasskeyUserName_caseInsensitive / hitsPasskeyUserDisplayName`
- **R2.2 (applySearch sealed when 実装)**:
  上記 + `CredentialListViewModelTest.applySearch_matchesPasswordLabelUsernamePackage_caseInsensitively`
- **R2.5 (case-insensitive)**:
  `CredentialListViewModelTest.applySearch_hitsPasskeyUserName_caseInsensitive`
- **R2.6 (blank query 全件返し)**:
  `CredentialListViewModelTest.uiState_clearSearch_returnsToFullList_whileFilterPreserved`
- **R2.7 (signature filter PassKey 素通し)**:
  `CredentialListViewModelTest.applyFilter_signatureMatched_keepsPasskeyRowsRegardlessOfPasswordFilter`
  / `applyFilter_signatureMissing_keepsPasskeyRowsRegardlessOfPasswordFilter`
- **R3.1 / R3.2 (string values)**:
  `values/strings.xml` / `values-en/strings.xml` / `values-ja/strings.xml` で kind_label を "PassKey"
  固定（手動レビュー、`grep "パスキー\|passkey\|Passkey" values-ja/strings.xml | grep -v PassKey` で空）
- **R3.3 (ハードコード禁止)**: adapter / activity / view-model がすべて R.string.* 参照
  （`bindPasskey_setsIconContentDescription_toPassKey` で TalkBack 用 string が変数経由
  であることを間接検証）
- **R3.4 (KDoc 表記固定)**: KDoc 内「PassKey」のみ使用（手動レビュー）
- **R3.5 (drawable ファイル名命名)**: `ic_passkey_24.xml` / `ic_password_24.xml`（小文字 + アンダースコア）
- **R4.1〜R4.7**: 上記 R1〜R3 で網羅
- **R4.8 (既存 applyFilter / applySearch password 単独テスト pass)**:
  `CredentialListViewModelTest.applySearch_matchesPasswordLabelUsernamePackage_caseInsensitively`
  / `applyFilter_partitionsBySignatureSha256_onPasswordRows`
- **R4.9 (既存 Activity 関連テスト pass)**: `CredentialListEmptyStateTest` 無変更（既存 test
  は EmptyKind 引数の signature 変更を受けないため）
- **R4.10 (PasskeyDao.listAll)**: `PasskeyDaoTest`（6 ケース）
- **R4.11 (PasskeyRepository.listAll)**: `PasskeyRepositoryTest.listAll_*`（2 ケース）
- **R5.1 / R5.2 (PassKey タップ Snackbar)**:
  `CredentialListActivity.setUpMainList` 内の sealed when（手動レビュー）/
  `CredentialListViewModelTest.onPasskeyClicked_doesNotMutateUiState`
- **R5.3 (long-click 何もしない)**:
  `CredentialListAdapter.PasskeyViewHolder.bind` 内の `setOnLongClickListener { onLongClick(...); true }`
  + Activity sealed when で `Passkey -> Unit`（手動レビュー）
- **R5.4 (overflow GONE)**:
  `CredentialListAdapterInstrumentationTest.bindPasskey_paintsIcPasskey24_andHidesChipSignatureStrengthBarAndOverflow`
- **R5.5 (PR description 説明)**: Reviewer 側の責務（impl-notes.md でも代替確認可能）
- **R6.1 (PassKey 行 contentDescription)**:
  `CredentialListAdapterInstrumentationTest.bindPasskey_setsIconContentDescription_toPassKey`
- **R6.2 (Password 行 contentDescription null)**:
  `CredentialListAdapterInstrumentationTest.bindPassword_setsIconContentDescription_toNull`
- **R6.3 (TalkBack 区別)**: R6.1 + R6.2 + 手動 QA（§T-11）
- **R6.4 (5 件 string key 両ロケール存在)**: AAPT2 が `assembleDebug` 成功で保証
  （`./gradlew :app:assembleDebug` 緑）
- **NFR 1.x (パフォーマンス)**: `CredentialListSortingTest` 全ケース + design §13 試算
- **NFR 2.1 (sensitive 列 UI 層遮断)**:
  `PasskeyDisplayModelTest.dataClassDoesNotDeclareSensitiveEntityFields_reflection`
  / `dataClassDoesNotMentionSensitiveFields_inToString`
- **NFR 2.2 (Entity → DisplayModel 変換が UseCase 内)**: `ListPasskeysUseCase` 設計（手動レビュー）
- **NFR 2.3 (SafeLogger raw 出さない)**:
  `CredentialListViewModelTest.onPasskeyClicked_doesNotMutateUiState`（mutation 不在を確認、
  実際の log message format は `CredentialListViewModel.onPasskeyClicked` を手動レビュー）
- **NFR 3.5 (既存テスト非破壊)**: 全 unit test 843 件 pass（pre-existing AppInfoProviderTest 1 件のみ
  本 Issue 範囲外）
- **NFR 4.1 (INTERNET permission なし)**: `InternetPermissionAbsenceTest` 既存 pass を維持
- **NFR 5.x (i18n 命名)**: `credential_list_passkey_*` / `credential_list_password_*` prefix 固定（手動レビュー）
- **D-1 (PassKey 表記)**: string 値 / KDoc / コメント / commit message ですべて "PassKey"（手動レビュー）
- **D-8 (DiffUtil 衝突防止)**: R1.5 と同じ
- **D-9 (DB schema 不変)**: `KeyNestDatabase.version = 5` 不変 / `Migration_4_5` のまま
  / 新規 migration 追加なし / `app/schemas/` 配下無変更
