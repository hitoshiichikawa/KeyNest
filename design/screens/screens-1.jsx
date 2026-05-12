// screens.jsx — All KeyNest screens as React components.
// Each is a self-contained 360×740 phone surface. Wrap in .kn-light or
// .kn-dark to swap theme. Components do not own theme — the artboard does.

// ─── Sample data ─────────────────────────────────────────
const SAMPLE_CREDS = [
  { color: '#1F6FEB', letter: 'S', label: 'Salesforce Mobile',     pkg: 'com.salesforce.chatter',        user: 'k.tanaka@company.co.jp',     strength: 'strong',  recent: '2 min ago' },
  { color: '#0EA5E9', letter: 'W', label: 'Workday',               pkg: 'com.workday.workdroidapp',      user: '20231458',                   strength: 'medium',  recent: '今日' },
  { color: '#10B981', letter: 'K', label: 'Kintone 営業ログ',       pkg: 'com.cybozu.kintone',            user: 'tanaka.k',                   strength: 'strong',  recent: '昨日' },
  { color: '#F59E0B', letter: 'B', label: 'BizReach Admin',        pkg: 'jp.co.bizreach.admin',          user: 'admin@bizreach',             strength: 'weak',    recent: '3 日前' },
  { color: '#6366F1', letter: 'M', label: 'Microsoft Teams (社内)', pkg: 'com.microsoft.teams',           user: 'k.tanaka@corp.local',        strength: 'strong',  recent: '先週' },
  { color: '#EC4899', letter: 'Z', label: 'Zendesk Support',       pkg: 'com.zendesk.android',           user: 'tanaka_k_support',           strength: 'medium',  recent: '先週' },
  { color: '#8B5CF6', letter: 'N', label: 'Notion (社内 Wiki)',     pkg: 'notion.id',                     user: 'k.tanaka',                   strength: 'strong',  recent: '2 週間前' },
];

const STRENGTH = {
  strong: { label: '強', sub: 'Strong',  fill: 3, color: 'var(--kn-success)' },
  medium: { label: '中', sub: 'Medium',  fill: 2, color: 'var(--kn-warning)' },
  weak:   { label: '弱', sub: 'Weak',    fill: 1, color: 'var(--kn-danger)'  },
};

// ─── Shared chrome inside the phone ──────────────────────
function Frame({ children, scrollable = true, padded = true }) {
  return (
    <div className="kn-phone">
      <StatusBar/>
      <div className="kn-noscroll" style={{ flex: 1, overflow: scrollable ? 'auto' : 'hidden', padding: padded ? '0 0 84px' : 0 }}>
        {children}
      </div>
      <HomeIndicator/>
    </div>
  );
}

// Strength pill
function StrengthBar({ kind, compact = false }) {
  const s = STRENGTH[kind];
  return (
    <div style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
      <div style={{ display: 'flex', gap: 2 }}>
        {[1,2,3].map(i => (
          <div key={i} style={{
            width: compact ? 8 : 14, height: 4, borderRadius: 2,
            background: i <= s.fill ? s.color : 'var(--border-strong)'
          }}/>
        ))}
      </div>
      {!compact && <span style={{ font: '700 11px/1 var(--kn-font-ui)', color: s.color, letterSpacing: '.04em' }}>{s.sub.toUpperCase()}</span>}
    </div>
  );
}

// Section header
function SectionHead({ title, count, action }) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', padding: '20px 20px 8px' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
        <span style={{ font: '700 13px/1 var(--kn-font-ui)', letterSpacing: '.06em', color: 'var(--text-2)', textTransform: 'uppercase' }}>{title}</span>
        {count != null && <span style={{ font: '700 12px/1 var(--kn-font-ui)', color: 'var(--text-3)' }}>{count}</span>}
      </div>
      {action}
    </div>
  );
}

// ═══════════════════════════════════════════════════════════
// 1) ONBOARDING — Autofill enable
// ═══════════════════════════════════════════════════════════
function ScreenOnboarding({ step = 2 }) {
  return (
    <Frame>
      {/* hero illustration */}
      <div style={{ padding: '12px 20px 0' }}>
        {/* progress dots */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 32 }}>
          {[1,2,3].map(i => (
            <div key={i} style={{
              height: 4, flex: i === step ? 2 : 1, borderRadius: 4,
              background: i <= step ? 'var(--primary)' : 'var(--border-strong)',
              transition: '.3s'
            }}/>
          ))}
        </div>

        <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 28 }}>
          <OnboardingIllustration/>
        </div>

        <div style={{ font: '800 28px/1.2 var(--kn-font-ui)', letterSpacing: '-.02em', marginBottom: 12 }}>
          Android の Autofill を<br/>KeyNest に切り替える
        </div>
        <div style={{ font: '500 15px/1.55 var(--kn-font-ui)', color: 'var(--text-2)', marginBottom: 24 }}>
          ログイン画面で保存済みのID/パスワードを<br/>候補表示するために、Autofill サービスを<br/>KeyNest に設定します。
        </div>

        {/* steps */}
        <div className="kn-card" style={{ padding: 16, marginBottom: 24 }}>
          {[
            ['1', '「設定を開く」をタップ', 'Android の設定画面に移動します'],
            ['2', '「KeyNest」を選択', 'パスワードとアカウントの項目で'],
            ['3', '戻ってきたら準備完了', 'クレデンシャルを登録できます'],
          ].map(([n,t,d], i, arr) => (
            <div key={n} style={{ display: 'flex', gap: 12, padding: '10px 0', borderBottom: i < arr.length-1 ? '1px solid var(--border)' : 'none' }}>
              <div style={{ width: 28, height: 28, flexShrink: 0, borderRadius: 999, background: 'var(--surface-tint)', color: 'var(--primary)', font: '800 13px/28px var(--kn-font-ui)', textAlign: 'center' }}>{n}</div>
              <div>
                <div style={{ font: '700 14px/1.3 var(--kn-font-ui)' }}>{t}</div>
                <div style={{ font: '500 13px/1.4 var(--kn-font-ui)', color: 'var(--text-2)', marginTop: 2 }}>{d}</div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* sticky bottom action */}
      <div style={{ position: 'absolute', bottom: 24, left: 20, right: 20 }}>
        <button className="kn-btn kn-btn--primary kn-btn--full">設定を開く</button>
        <button className="kn-btn kn-btn--ghost kn-btn--full" style={{ height: 44, marginTop: 6 }}>あとで</button>
      </div>
    </Frame>
  );
}

function OnboardingIllustration() {
  return (
    <svg width="220" height="180" viewBox="0 0 220 180" fill="none">
      <defs>
        <linearGradient id="ob-roof" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2A7BF5"/>
          <stop offset="1" stopColor="#1457C9"/>
        </linearGradient>
        <radialGradient id="ob-glow" cx=".5" cy=".5" r=".6">
          <stop offset="0" stopColor="#1F6FEB" stopOpacity=".18"/>
          <stop offset="1" stopColor="#1F6FEB" stopOpacity="0"/>
        </radialGradient>
      </defs>
      <circle cx="110" cy="90" r="90" fill="url(#ob-glow)"/>
      {/* shelter shape */}
      <path d="M60 120 Q110 50 160 120 L160 138 Q110 124 60 138 Z" fill="url(#ob-roof)"/>
      <circle cx="110" cy="98" r="9" fill="white"/>
      <rect x="106.5" y="100" width="7" height="14" rx="2.5" fill="white"/>
      {/* incoming credential chips */}
      <g transform="translate(20 30)">
        <rect width="60" height="20" rx="10" fill="white" stroke="#D0E0FC" strokeWidth="1.5"/>
        <circle cx="11" cy="10" r="4" fill="#1F6FEB"/>
        <rect x="20" y="6" width="32" height="3" rx="1.5" fill="#1F6FEB" opacity=".7"/>
        <rect x="20" y="12" width="22" height="3" rx="1.5" fill="#9CA9C7"/>
      </g>
      <g transform="translate(155 145)">
        <rect width="60" height="20" rx="10" fill="white" stroke="#D0E0FC" strokeWidth="1.5"/>
        <circle cx="11" cy="10" r="4" fill="#10B981"/>
        <rect x="20" y="6" width="32" height="3" rx="1.5" fill="#10B981" opacity=".7"/>
        <rect x="20" y="12" width="22" height="3" rx="1.5" fill="#9CA9C7"/>
      </g>
      {/* arrows */}
      <path d="M84 50 Q100 65 96 86" stroke="#1F6FEB" strokeWidth="1.5" strokeDasharray="3 3" fill="none"/>
      <path d="M154 148 Q140 138 138 122" stroke="#1F6FEB" strokeWidth="1.5" strokeDasharray="3 3" fill="none"/>
    </svg>
  );
}

// ═══════════════════════════════════════════════════════════
// 2) CREDENTIAL LIST — empty + populated
// ═══════════════════════════════════════════════════════════
function ScreenListEmpty() {
  return (
    <Frame>
      <ListAppBar count={0}/>
      <SearchBar/>
      <div style={{ padding: '40px 28px', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', marginTop: 24 }}>
        <div style={{ position: 'relative', marginBottom: 24 }}>
          <div style={{ position: 'absolute', inset: -18, borderRadius: '50%', background: 'var(--surface-tint)', filter: 'blur(8px)' }}/>
          <KNMark size={92}/>
        </div>
        <div style={{ font: '800 22px/1.25 var(--kn-font-ui)', letterSpacing: '-.02em', marginBottom: 10 }}>
          最初の鍵を巣に入れよう
        </div>
        <div style={{ font: '500 14px/1.5 var(--kn-font-ui)', color: 'var(--text-2)', marginBottom: 28 }}>
          業務アプリのID/パスワードを登録すると、<br/>
          ログイン画面で候補として表示されます。<br/>
          すべてこの端末の中だけに保存。
        </div>

        <button className="kn-btn kn-btn--primary" style={{ minWidth: 220 }}>
          {Icons.plus}
          クレデンシャルを登録
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 28, font: '600 12px/1 var(--kn-font-ui)', color: 'var(--text-3)' }}>
          {Icons.shield}
          AES-GCM · 端末ローカルのみ
        </div>
      </div>
    </Frame>
  );
}

function ScreenListPopulated() {
  return (
    <Frame>
      <ListAppBar count={SAMPLE_CREDS.length}/>
      <SearchBar/>
      <FilterChips/>

      {/* RECENT row */}
      <SectionHead title="最近使った" count={3} action={<span style={{ font: '600 12px/1 var(--kn-font-ui)', color: 'var(--primary)' }}>すべて</span>}/>
      <div className="kn-noscroll" style={{ display: 'flex', gap: 10, padding: '0 20px 4px', overflowX: 'auto' }}>
        {SAMPLE_CREDS.slice(0, 3).map((c, i) => (
          <div key={i} style={{ flex: '0 0 132px', padding: 14, borderRadius: 18, background: 'var(--surface)', border: '1px solid var(--border)', display: 'flex', flexDirection: 'column', gap: 10 }}>
            <IconTile color={c.color} letter={c.letter} size={36} radius={10}/>
            <div>
              <div style={{ font: '700 13px/1.2 var(--kn-font-ui)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.label}</div>
              <div style={{ font: '500 11px/1.3 var(--kn-font-ui)', color: 'var(--text-3)', marginTop: 2, display: 'flex', alignItems: 'center', gap: 4 }}>
                {Icons.clock}{c.recent}
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* All credentials */}
      <SectionHead title="すべて" count={SAMPLE_CREDS.length} action={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, font: '600 12px/1 var(--kn-font-ui)', color: 'var(--text-2)' }}>
          {Icons.filter} 並び替え
        </span>
      }/>
      <div style={{ padding: '0 16px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {SAMPLE_CREDS.map((c, i) => <CredCard key={i} cred={c}/>)}
      </div>
    </Frame>
  );
}

function ListAppBar({ count }) {
  return (
    <div className="kn-appbar">
      <div>
        <div style={{ font: '600 12px/1 var(--kn-font-ui)', color: 'var(--text-2)', marginBottom: 4, letterSpacing: '.04em' }}>
          KeyNest
        </div>
        <div className="kn-appbar-title">
          Vault {count > 0 && <span style={{ font: '600 14px/1 var(--kn-font-ui)', color: 'var(--text-3)', letterSpacing: 0 }}>· {count}</span>}
        </div>
      </div>
      <div style={{ display: 'flex', gap: 4 }}>
        <button className="kn-iconbtn">{Icons.search}</button>
        <button className="kn-iconbtn">{Icons.settings}</button>
      </div>
    </div>
  );
}

function SearchBar() {
  return (
    <div style={{ padding: '8px 16px 4px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '12px 14px', background: 'var(--surface-2)', borderRadius: 14, color: 'var(--text-3)' }}>
        {Icons.search}
        <span style={{ font: '500 14px/1 var(--kn-font-ui)' }}>アプリ名・ユーザー名で検索</span>
      </div>
    </div>
  );
}

function FilterChips() {
  const chips = [
    { label: 'すべて', active: true },
    { label: '署名OK', icon: Icons.shield },
    { label: '署名なし', icon: Icons.warning, warn: true },
    { label: '強度: 弱', warn: true },
  ];
  return (
    <div className="kn-noscroll" style={{ display: 'flex', gap: 8, padding: '8px 16px 4px', overflowX: 'auto' }}>
      {chips.map((c, i) => (
        <div key={i} style={{
          flex: '0 0 auto', display: 'inline-flex', alignItems: 'center', gap: 4,
          padding: '8px 14px', borderRadius: 999,
          background: c.active ? 'var(--primary)' : 'var(--surface)',
          color: c.active ? 'var(--on-primary)' : (c.warn ? 'var(--kn-warning)' : 'var(--text)'),
          border: c.active ? 'none' : '1px solid var(--border)',
          font: '600 12px/1 var(--kn-font-ui)',
        }}>
          {c.icon && React.cloneElement(c.icon, { width: 14, height: 14 })}
          {c.label}
        </div>
      ))}
    </div>
  );
}

function CredCard({ cred }) {
  return (
    <div style={{ padding: 14, background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 18, display: 'flex', alignItems: 'center', gap: 12 }}>
      <IconTile color={cred.color} letter={cred.letter} size={44} radius={12}/>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 3 }}>
          <span style={{ font: '700 15px/1.2 var(--kn-font-ui)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{cred.label}</span>
          <span title="署名OK" style={{ color: 'var(--kn-success)', display: 'inline-flex' }}>{React.cloneElement(Icons.shieldFill, { width: 13, height: 13 })}</span>
        </div>
        <div style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: 'var(--text-2)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {cred.user}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 6 }}>
          <StrengthBar kind={cred.strength} compact/>
          <span style={{ font: '500 11px/1 var(--kn-font-ui)', color: 'var(--text-3)' }}>· {cred.pkg}</span>
        </div>
      </div>
      <button className="kn-iconbtn" style={{ color: 'var(--text-3)' }}>{Icons.more}</button>
    </div>
  );
}

// ═══════════════════════════════════════════════════════════
// 3) CREDENTIAL EDIT — create / edit
// ═══════════════════════════════════════════════════════════
function ScreenEdit({ mode = 'new' }) {
  const isEdit = mode === 'edit';
  return (
    <Frame>
      <div className="kn-appbar" style={{ padding: '8px 8px' }}>
        <button className="kn-iconbtn">{Icons.close}</button>
        <div style={{ font: '700 16px/1 var(--kn-font-ui)' }}>{isEdit ? 'クレデンシャル編集' : '新しいクレデンシャル'}</div>
        <button style={{ font: '700 15px/1 var(--kn-font-ui)', color: 'var(--primary)', background: 'transparent', border: 'none', padding: '8px 12px' }}>保存</button>
      </div>

      <div style={{ padding: '4px 20px 20px' }}>
        {/* Target app card */}
        <div className="kn-card" style={{ padding: 14, display: 'flex', alignItems: 'center', gap: 14, marginTop: 8, marginBottom: 24 }}>
          <IconTile color="#1F6FEB" letter="S" size={48} radius={14}/>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ font: '700 15px/1.2 var(--kn-font-ui)' }}>Salesforce Mobile</div>
            <div style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: 'var(--text-2)', fontFamily: 'var(--kn-font-mono)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              com.salesforce.chatter
            </div>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginTop: 6, padding: '3px 8px', background: 'var(--kn-success-soft)', color: 'var(--kn-success)', borderRadius: 999, font: '700 10px/1 var(--kn-font-ui)' }}>
              {React.cloneElement(Icons.shieldFill, { width: 11, height: 11 })}
              署名取得済み · SHA-256
            </div>
          </div>
          <button style={{ font: '600 12px/1 var(--kn-font-ui)', color: 'var(--primary)', background: 'transparent', border: 'none' }}>変更</button>
        </div>

        <Field label="表示名" value="Salesforce (営業)" help="Autofill 候補のラベル"/>
        <Field label="ユーザー名 / メール" value="k.tanaka@company.co.jp" icon={Icons.copy}/>
        <PasswordField/>

        {/* Advanced */}
        <div style={{ marginTop: 16, padding: 14, background: 'var(--surface-2)', borderRadius: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
            <span style={{ font: '700 13px/1 var(--kn-font-ui)', color: 'var(--text-2)' }}>詳細設定</span>
            {React.cloneElement(Icons.chevron, { width: 16, height: 16, style: { color: 'var(--text-3)', transform: 'rotate(90deg)' } })}
          </div>
          <Row k="パッケージ名" v="com.salesforce.chatter" mono/>
          <Row k="署名 SHA-256" v="a1:2f:9e:…:cb:01" mono/>
          <Row k="登録日" v="2025-11-12 14:22"/>
        </div>

        {isEdit && (
          <button style={{ marginTop: 20, width: '100%', padding: 14, background: 'transparent', color: 'var(--kn-danger)', border: '1px solid var(--border)', borderRadius: 14, font: '700 14px/1 var(--kn-font-ui)' }}>
            このクレデンシャルを削除
          </button>
        )}
      </div>
    </Frame>
  );
}

function Field({ label, value, help, icon }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <label style={{ font: '700 12px/1 var(--kn-font-ui)', color: 'var(--text-2)', letterSpacing: '.04em', textTransform: 'uppercase' }}>{label}</label>
      <div style={{ marginTop: 6, padding: '12px 14px', background: 'var(--surface)', border: '1px solid var(--border-strong)', borderRadius: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
        <input defaultValue={value} style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', font: '600 15px/1.2 var(--kn-font-ui)', color: 'var(--text)', minWidth: 0 }}/>
        {icon && <span style={{ color: 'var(--text-3)' }}>{icon}</span>}
      </div>
      {help && <div style={{ font: '500 11px/1.4 var(--kn-font-ui)', color: 'var(--text-3)', marginTop: 4 }}>{help}</div>}
    </div>
  );
}

function PasswordField() {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
        <label style={{ font: '700 12px/1 var(--kn-font-ui)', color: 'var(--text-2)', letterSpacing: '.04em', textTransform: 'uppercase' }}>パスワード</label>
        <StrengthBar kind="strong"/>
      </div>
      <div style={{ marginTop: 6, padding: '12px 14px', background: 'var(--surface)', border: '2px solid var(--primary)', borderRadius: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
        <input type="password" defaultValue="••••••••••••••" style={{ flex: 1, background: 'transparent', border: 'none', outline: 'none', font: '700 17px/1 var(--kn-font-mono)', letterSpacing: '.1em', color: 'var(--text)', minWidth: 0 }}/>
        <button className="kn-iconbtn" style={{ width: 32, height: 32, color: 'var(--text-2)' }}>{Icons.eye}</button>
      </div>
    </div>
  );
}

function Row({ k, v, mono }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--border)' }}>
      <span style={{ font: '500 12px/1.3 var(--kn-font-ui)', color: 'var(--text-2)' }}>{k}</span>
      <span style={{ font: `600 12px/1.3 ${mono ? 'var(--kn-font-mono)' : 'var(--kn-font-ui)'}`, color: 'var(--text)' }}>{v}</span>
    </div>
  );
}

Object.assign(window, { ScreenOnboarding, ScreenListEmpty, ScreenListPopulated, ScreenEdit, Frame, SAMPLE_CREDS, StrengthBar, IconTile });
