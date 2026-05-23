package io.github.hitoshiichikawa.keynest.domain.model

/**
 * KeyNest の Credential Manager 登録状態を表す 3 値。
 *
 * Issue #103 Req 3.2 / 3.3 / 3.4 / NFR 3.1 (PassKey 表記固定).
 *
 * - [Enabled]: API 34+ (Android 14+) かつ KeyNest が OS の Credential Manager
 *   に PassKey プロバイダとして有効化済み。
 * - [Disabled]: API 34+ だが KeyNest が未有効化、または判定 API 例外時の
 *   fallback (Req 3.5)。
 * - [Unsupported]: `Build.VERSION.SDK_INT < 34` (Android 14 未満)。本機能の
 *   対象 OS でないことを示す中立状態 (Req 1.6 / 1.7)。
 *
 * 単一責務: 3 値の正規化のみを担う UI 非依存 enum。UI 文言切替や
 * OS API 直叩きは含まない (各 case に追加プロパティを持たせる必要が
 * なく、`when` 網羅性も `sealed class` と同等のため enum を採用)。
 */
enum class PasskeyProviderStatus { Enabled, Disabled, Unsupported }
