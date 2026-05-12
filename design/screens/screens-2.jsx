// screens-2.jsx — Picker bottom sheet, autofill dataset, unlock, settings

// ═══════════════════════════════════════════════════════════
// 4) PACKAGE PICKER — bottom sheet over the list
// ═══════════════════════════════════════════════════════════
function ScreenPicker() {
  const installed = [
    { c: '#1F6FEB', l: 'S', name: 'Salesforce Mobile',  pkg: 'com.salesforce.chatter',     used: true  },
    { c: '#0EA5E9', l: 'W', name: 'Workday',            pkg: 'com.workday.workdroidapp',   used: true  },
    { c: '#10B981', l: 'K', name: 'Kintone',            pkg: 'com.cybozu.kintone',         used: true  },
    { c: '#6366F1', l: 'M', name: 'Microsoft Teams',    pkg: 'com.microsoft.teams',        used: false },
    { c: '#8B5CF6', l: 'N', name: 'Notion',             pkg: 'notion.id',                  used: false },
    { c: '#EC4899', l: 'Z', name: 'Zendesk',            pkg: 'com.zendesk.android',        used: false },
    { c: '#F59E0B', l: 'B', name: 'BizReach',           pkg: 'jp.co.bizreach.bizreach',    used: false },
    { c: '#EF4444', l: 'R', name: 'Redmine Mobile',     pkg: 'jp.redmine.redmineapp',      used: false },
    { c: '#06B6D4', l: 'F', name: 'Figma',              pkg: 'com.figma.mirror',           used: false },
  ];
  return (
    <div className="kn-phone" style={{ background: 'rgba(11,18,32,0.55)' }}>
      <StatusBar/>
      {/* dimmed list peek */}
      <div style={{ position: 'absolute', inset: 0, opacity: .25, pointerEvents: 'none' }}>
        <ScreenEditBgPeek/>
      </div>
      {/* sheet */}
      <div style={{ position: 'absolute', left: 0, right: 0, bottom: 0, background: 'var(--bg-elev)', borderTopLeftRadius: 28, borderTopRightRadius: 28, padding: '12px 0 24px', boxShadow: '0 -20px 60px rgba(0,0,0,.25)', display: 'flex', flexDirection: 'column', maxHeight: '88%' }}>
        <div style={{ width: 36, height: 4, borderRadius: 2, background: 'var(--border-strong)', margin: '0 auto 14px' }}/>
        <div style={{ padding: '0 20px 12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{ font: '800 19px/1.2 var(--kn-font-ui)', letterSpacing: '-.02em' }}>アプリを選択</div>
            <div style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: 'var(--text-2)', marginTop: 2 }}>署名情報も同時に取得します</div>
          </div>
          <button className="kn-iconbtn">{Icons.close}</button>
        </div>
        <div style={{ padding: '0 16px 10px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '11px 14px', background: 'var(--surface-2)', borderRadius: 12, color: 'var(--text-3)' }}>
            {Icons.search}
            <span style={{ font: '500 14px/1 var(--kn-font-ui)' }}>アプリ名・パッケージ名で絞り込み</span>
          </div>
        </div>

        <div style={{ padding: '4px 12px 0', overflow: 'auto' }} className="kn-noscroll">
          {installed.some(a => a.used) && (
            <>
              <div style={{ font: '700 11px/1 var(--kn-font-ui)', color: 'var(--text-3)', letterSpacing: '.08em', padding: '12px 12px 6px', textTransform: 'uppercase' }}>業務でよく使う</div>
              {installed.filter(a => a.used).map((a, i) => <PickerRow key={i} a={a} selected={i === 0}/>)}
            </>
          )}
          <div style={{ font: '700 11px/1 var(--kn-font-ui)', color: 'var(--text-3)', letterSpacing: '.08em', padding: '16px 12px 6px', textTransform: 'uppercase' }}>すべてのアプリ</div>
          {installed.filter(a => !a.used).map((a, i) => <PickerRow key={i} a={a}/>)}
        </div>

        <div style={{ padding: '12px 20px 0', borderTop: '1px solid var(--border)' }}>
          <button style={{ width: '100%', padding: 12, background: 'transparent', color: 'var(--text-2)', border: 'none', font: '600 13px/1.3 var(--kn-font-ui)' }}>
            一覧にない → パッケージ名を手動入力
          </button>
        </div>
      </div>
      <HomeIndicator/>
    </div>
  );
}

function PickerRow({ a, selected }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 12px', borderRadius: 12, background: selected ? 'var(--surface-tint)' : 'transparent' }}>
      <IconTile color={a.c} letter={a.l} size={40} radius={11}/>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ font: '700 14px/1.2 var(--kn-font-ui)' }}>{a.name}</div>
        <div style={{ font: '500 11px/1.3 var(--kn-font-ui) var(--kn-font-mono)', fontFamily: 'var(--kn-font-mono)', color: 'var(--text-3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginTop: 2 }}>{a.pkg}</div>
      </div>
      {selected
        ? <span style={{ color: 'var(--primary)' }}>{Icons.check}</span>
        : React.cloneElement(Icons.chevron, { style: { color: 'var(--text-3)' } })
      }
    </div>
  );
}

function ScreenEditBgPeek() {
  return (
    <div className="kn-phone" style={{ background: 'var(--bg)' }}>
      <StatusBar/>
      <div className="kn-appbar" style={{ padding: '8px 8px' }}>
        <button className="kn-iconbtn">{Icons.close}</button>
        <div style={{ font: '700 16px/1 var(--kn-font-ui)' }}>新しいクレデンシャル</div>
        <button style={{ font: '700 15px/1 var(--kn-font-ui)', color: 'var(--text-3)', background: 'transparent', border: 'none', padding: '8px 12px' }}>保存</button>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════
// 5) AUTOFILL DATASET DROPDOWN — third-party app context
// ═══════════════════════════════════════════════════════════
function ScreenDataset() {
  return (
    <div className="kn-phone" style={{ background: '#FAFAFA', color: '#0B1220' }}>
      <StatusBar/>
      {/* Pretend host app (Salesforce login) */}
      <div style={{ flex: 1, background: '#FAFAFA', padding: '24px 24px 0', display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 28 }}>
          <button style={{ background: 'transparent', border: 'none', color: '#0B1220' }}>{Icons.back}</button>
          <div style={{ font: '600 13px/1 var(--kn-font-ui)', color: '#5C6B8E' }}>ログイン</div>
          <div style={{ width: 22 }}/>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 24 }}>
          <IconTile color="#1F6FEB" letter="S" size={48} radius={12}/>
          <div>
            <div style={{ font: '800 19px/1.2 var(--kn-font-ui)' }}>Salesforce</div>
            <div style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: '#5C6B8E' }}>サインインしてください</div>
          </div>
        </div>

        {/* username field — focused */}
        <div style={{ position: 'relative', marginBottom: 56 }}>
          <label style={{ font: '600 12px/1 var(--kn-font-ui)', color: '#1F6FEB' }}>ユーザー名</label>
          <div style={{ marginTop: 6, padding: '12px 14px', background: 'white', border: '2px solid #1F6FEB', borderRadius: 10, display: 'flex', alignItems: 'center' }}>
            <span style={{ font: '600 16px/1 var(--kn-font-ui)', color: '#9CA9C7' }}>|</span>
            <span style={{ width: 1, height: 18, background: '#1F6FEB', marginLeft: 2, animation: 'caret 1s steps(1) infinite' }}/>
          </div>

          {/* Autofill chip */}
          <div style={{ position: 'absolute', top: -4, right: -2, transform: 'rotate(-3deg)' }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, padding: '3px 7px', background: '#0B1220', color: 'white', borderRadius: 8, font: '700 9px/1 var(--kn-font-ui)' }}>FOCUSED</div>
          </div>

          {/* Autofill dropdown */}
          <div style={{ position: 'absolute', top: 'calc(100% + 6px)', left: 0, right: 0, background: 'white', borderRadius: 16, boxShadow: '0 16px 48px rgba(15,23,41,.18), 0 1px 3px rgba(15,23,41,.08)', overflow: 'hidden', border: '1px solid rgba(15,23,41,.06)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 14px', background: '#F1F4FA', borderBottom: '1px solid rgba(15,23,41,.06)' }}>
              <KNMark size={16} color="#1F6FEB"/>
              <span style={{ font: '700 11px/1 var(--kn-font-ui)', color: '#0B1220', letterSpacing: '.04em' }}>KEYNEST</span>
              <span style={{ flex: 1 }}/>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, padding: '3px 7px', background: '#D1FAE5', color: '#047857', borderRadius: 999, font: '700 9px/1 var(--kn-font-ui)' }}>
                {React.cloneElement(Icons.shieldFill, { width: 9, height: 9 })}
                署名一致
              </span>
            </div>
            <DatasetRow letter="S" color="#1F6FEB" label="Salesforce (営業)" user="k.tanaka@company.co.jp" locked/>
            <DatasetRow letter="S" color="#0F4AA8" label="Salesforce (個人検証)" user="kt.test@company.co.jp" locked/>
            <div style={{ padding: '10px 14px', display: 'flex', alignItems: 'center', gap: 8, color: '#1F6FEB', font: '600 13px/1 var(--kn-font-ui)' }}>
              {React.cloneElement(Icons.plus, { width: 16, height: 16 })}
              KeyNest で新規作成
            </div>
          </div>
        </div>

        <div style={{ marginBottom: 16 }}>
          <label style={{ font: '600 12px/1 var(--kn-font-ui)', color: '#5C6B8E' }}>パスワード</label>
          <div style={{ marginTop: 6, padding: '12px 14px', background: 'white', border: '1px solid #E3E8F1', borderRadius: 10 }}>
            <span style={{ font: '500 14px/1 var(--kn-font-ui)', color: '#9CA9C7' }}>必須</span>
          </div>
        </div>

        <button style={{ width: '100%', padding: 14, background: '#0EA5E9', color: 'white', border: 'none', borderRadius: 10, font: '700 14px/1 var(--kn-font-ui)' }}>サインイン</button>
      </div>
      <HomeIndicator/>
    </div>
  );
}

function DatasetRow({ letter, color, label, user, locked }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', borderBottom: '1px solid rgba(15,23,41,.06)' }}>
      <IconTile color={color} letter={letter} size={32} radius={9}/>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ font: '700 13px/1.2 var(--kn-font-ui)', color: '#0B1220' }}>{label}</div>
        <div style={{ font: '500 11px/1.3 var(--kn-font-ui)', color: '#5C6B8E', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user}</div>
      </div>
      {locked && <span style={{ color: '#5C6B8E', display: 'inline-flex' }}>{React.cloneElement(Icons.lock, { width: 16, height: 16 })}</span>}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════
// 6) BIOMETRIC UNLOCK — overlay on the host app
// ═══════════════════════════════════════════════════════════
function ScreenUnlock({ state = 'prompt' }) {
  return (
    <div className="kn-phone" style={{ background: 'rgba(11,18,32,0.55)' }}>
      <StatusBar/>
      {/* dimmed host */}
      <div style={{ position: 'absolute', inset: 0, opacity: .25, pointerEvents: 'none' }}>
        <ScreenDataset/>
      </div>

      {/* center prompt */}
      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
        <div style={{ width: '100%', background: 'var(--bg-elev)', borderRadius: 24, padding: 24, boxShadow: '0 40px 80px rgba(0,0,0,.45)' }}>
          {/* Brand cap */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 18 }}>
            <KNMark size={20}/>
            <span style={{ font: '700 12px/1 var(--kn-font-ui)', letterSpacing: '.06em', color: 'var(--text-2)' }}>KEYNEST · UNLOCK</span>
          </div>

          {/* target chip */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 12, background: 'var(--surface-2)', borderRadius: 14, marginBottom: 24 }}>
            <IconTile color="#1F6FEB" letter="S" size={40} radius={11}/>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ font: '700 14px/1.2 var(--kn-font-ui)' }}>Salesforce (営業)</div>
              <div style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: 'var(--text-2)' }}>k.tanaka@company.co.jp</div>
            </div>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 3, padding: '3px 7px', background: 'var(--kn-success-soft)', color: '#047857', borderRadius: 999, font: '700 9px/1 var(--kn-font-ui)' }}>
              {React.cloneElement(Icons.shieldFill, { width: 9, height: 9 })}
              署名一致
            </span>
          </div>

          {/* fingerprint */}
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '12px 0 16px' }}>
            <div style={{ position: 'relative', width: 96, height: 96, marginBottom: 14 }}>
              <div style={{ position: 'absolute', inset: 0, borderRadius: '50%', background: 'var(--surface-tint)' }}/>
              <div style={{ position: 'absolute', inset: 12, borderRadius: '50%', background: 'transparent', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--primary)' }}>
                {React.cloneElement(Icons.fingerprint, { width: 64, height: 64 })}
              </div>
              {/* concentric pulse */}
              <div style={{ position: 'absolute', inset: -6, borderRadius: '50%', border: '2px solid var(--primary)', opacity: .25 }}/>
            </div>
            <div style={{ font: '800 19px/1.2 var(--kn-font-ui)', letterSpacing: '-.01em', marginBottom: 6 }}>
              指紋を読み取り中…
            </div>
            <div style={{ font: '500 13px/1.4 var(--kn-font-ui)', color: 'var(--text-2)', textAlign: 'center' }}>
              認証後にパスワードが復号され、<br/>ログイン画面に入力されます。
            </div>
          </div>

          <button style={{ width: '100%', padding: 12, background: 'transparent', color: 'var(--primary)', border: '1px solid var(--border-strong)', borderRadius: 12, font: '700 13px/1 var(--kn-font-ui)' }}>
            PIN / パスコードで認証
          </button>
          <button style={{ width: '100%', marginTop: 6, padding: 10, background: 'transparent', color: 'var(--text-3)', border: 'none', font: '600 13px/1 var(--kn-font-ui)' }}>
            キャンセル
          </button>
        </div>
      </div>
      <HomeIndicator/>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════
// 7) SETTINGS / ABOUT
// ═══════════════════════════════════════════════════════════
function ScreenSettings() {
  return (
    <Frame>
      <div className="kn-appbar" style={{ padding: '8px 8px' }}>
        <button className="kn-iconbtn">{Icons.back}</button>
        <div style={{ font: '700 16px/1 var(--kn-font-ui)' }}>設定</div>
        <div style={{ width: 40 }}/>
      </div>

      <div style={{ padding: '4px 20px 24px' }}>
        {/* Autofill status hero */}
        <div style={{ padding: 18, borderRadius: 20, background: 'linear-gradient(140deg, var(--primary) 0%, var(--kn-blue-700) 100%)', color: 'white', marginBottom: 20, position: 'relative', overflow: 'hidden' }}>
          <div style={{ position: 'absolute', top: -30, right: -20, opacity: .15 }}>
            <KNMark size={140} color="white"/>
          </div>
          <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '4px 9px', background: 'rgba(255,255,255,.2)', borderRadius: 999, font: '700 10px/1 var(--kn-font-ui)', marginBottom: 12 }}>
            {React.cloneElement(Icons.check, { width: 12, height: 12 })}
            有効
          </div>
          <div style={{ font: '800 20px/1.2 var(--kn-font-ui)', letterSpacing: '-.02em' }}>
            Autofill サービス
          </div>
          <div style={{ font: '500 12px/1.45 var(--kn-font-ui)', opacity: .85, marginTop: 4 }}>
            KeyNest が Android のパスワードAutofillとして動作しています。
          </div>
          <button style={{ marginTop: 14, padding: '8px 14px', background: 'rgba(255,255,255,.18)', color: 'white', border: '1px solid rgba(255,255,255,.3)', borderRadius: 10, font: '700 12px/1 var(--kn-font-ui)' }}>
            Android 設定で確認
          </button>
        </div>

        <SettingGroup title="セキュリティ">
          <SettingRow icon={Icons.fingerprint} label="ロック解除方法" sub="生体認証 + PIN フォールバック"/>
          <SettingRow icon={Icons.clock} label="アンロック保持時間" sub="毎回認証する" right="毎回"/>
          <SettingRow icon={Icons.shield} label="署名照合の厳密性" sub="不一致は候補から除外" toggle defaultOn/>
        </SettingGroup>

        <SettingGroup title="Vault">
          <SettingRow icon={Icons.key} label="クレデンシャル数" right="7 件"/>
          <SettingRow icon={Icons.copy} label="エクスポート" sub="暗号化バックアップ"/>
          <SettingRow icon={Icons.warning} label="すべて削除" sub="復元できません" danger/>
        </SettingGroup>

        <SettingGroup title="About">
          <SettingRow icon={<KNMark size={20}/>} label="KeyNest" sub="バージョン 0.4.0 (build 128) · MIT"/>
          <SettingRow icon={Icons.shield} label="プライバシー" sub="ネットワーク送信なし · Keystore 保護"/>
        </SettingGroup>

        <div style={{ marginTop: 24, textAlign: 'center', font: '500 11px/1.5 var(--kn-font-ui)', color: 'var(--text-3)' }}>
          すべてのデータはこの端末の<br/>Android Keystore で保護された<br/>AES-GCM で暗号化されます。
        </div>
      </div>
    </Frame>
  );
}

function SettingGroup({ title, children }) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ font: '700 11px/1 var(--kn-font-ui)', color: 'var(--text-3)', letterSpacing: '.08em', padding: '0 4px 8px', textTransform: 'uppercase' }}>{title}</div>
      <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 16, overflow: 'hidden' }}>
        {children}
      </div>
    </div>
  );
}
function SettingRow({ icon, label, sub, right, toggle, defaultOn, danger }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', borderBottom: '1px solid var(--border)', color: danger ? 'var(--kn-danger)' : 'var(--text)' }}>
      <div style={{ width: 32, height: 32, borderRadius: 10, background: 'var(--surface-2)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: danger ? 'var(--kn-danger)' : 'var(--text-2)' }}>
        {React.cloneElement(icon, { width: 18, height: 18 })}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ font: '700 14px/1.2 var(--kn-font-ui)' }}>{label}</div>
        {sub && <div style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: 'var(--text-2)', marginTop: 2 }}>{sub}</div>}
      </div>
      {right && <span style={{ font: '600 13px/1 var(--kn-font-ui)', color: 'var(--text-2)' }}>{right}</span>}
      {toggle && (
        <div style={{ width: 40, height: 24, borderRadius: 999, background: defaultOn ? 'var(--primary)' : 'var(--border-strong)', padding: 2, display: 'flex', justifyContent: defaultOn ? 'flex-end' : 'flex-start' }}>
          <div style={{ width: 20, height: 20, borderRadius: '50%', background: 'white' }}/>
        </div>
      )}
      {!toggle && !right && React.cloneElement(Icons.chevron, { style: { color: 'var(--text-3)' } })}
    </div>
  );
}

Object.assign(window, { ScreenPicker, ScreenDataset, ScreenUnlock, ScreenSettings });
