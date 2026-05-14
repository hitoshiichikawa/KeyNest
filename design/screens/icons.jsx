// icons.jsx — App icon variations. Each is a 1024x1024 SVG.
// All built around the "key + nest/shelter" idea, exploring different
// treatments. Drop the option(s) you like into a real Android adaptive
// icon (foreground 432x432 at center of 1024x1024 safe zone).

function IconShell({ size = 192, radius = 0.224, children, bg }) {
  // Android adaptive icons mask to a ~22% radius squircle. We render at the
  // declared radius so what you see matches the live mask.
  return (
    <div style={{ width: size, height: size, borderRadius: size * radius, overflow: 'hidden', background: bg, position: 'relative', boxShadow: '0 12px 32px rgba(15,23,41,0.18), 0 2px 6px rgba(15,23,41,0.08)' }}>
      <svg viewBox="0 0 200 200" width={size} height={size} style={{ display: 'block' }}>
        {children}
      </svg>
    </div>
  );
}

// All silhouettes now fill ~85% of the 200x200 canvas so they read
// strongly at 48dp on a home screen. Adaptive-icon safe zone (66%) is
// respected by keeping the strongest forms inside x=24..176 / y=20..180.

// A — Selected direction: cream "shelter" + bold blue roof + keyhole
function IconA({ size }) {
  return (
    <IconShell size={size} bg="#F4EBDC">
      <defs>
        <linearGradient id="iconA-roof" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#2A7BF5"/>
          <stop offset="1" stopColor="#0F4AA8"/>
        </linearGradient>
      </defs>
      {/* tan nest base */}
      <path d="M18 158 Q100 132 182 158 L182 184 Q100 168 18 184 Z" fill="#D9C3A0"/>
      {/* shelter / roof */}
      <path d="M22 156 Q100 22 178 156 Z" fill="url(#iconA-roof)"/>
      {/* big keyhole, centered */}
      <circle cx="100" cy="98" r="18" fill="#F4EBDC"/>
      <path d="M91 110 L88 148 L112 148 L109 110 Z" fill="#F4EBDC"/>
      {/* twig accents */}
      <path d="M30 174 L72 168 M128 168 L170 174" stroke="#9B7A4A" strokeWidth="4" strokeLinecap="round"/>
    </IconShell>
  );
}

// B — Filled blue, oversized "K" key
function IconB({ size }) {
  return (
    <IconShell size={size} bg="#1F6FEB">
      <defs>
        <linearGradient id="iconB-bg" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2E80F8"/>
          <stop offset="1" stopColor="#0F4AA8"/>
        </linearGradient>
      </defs>
      <rect width="200" height="200" fill="url(#iconB-bg)"/>
      {/* paper-nest shelter under the K */}
      <path d="M24 168 Q100 122 176 168 Z" fill="#F4EBDC" opacity=".95"/>
      {/* big K-shaped key */}
      <g stroke="#F4EBDC" strokeLinecap="round" fill="none">
        <circle cx="60" cy="100" r="22" strokeWidth="14"/>
        <path d="M82 100 L168 100" strokeWidth="20"/>
        <path d="M138 100 L138 132" strokeWidth="14"/>
        <path d="M158 100 L158 122" strokeWidth="14"/>
      </g>
      <circle cx="60" cy="100" r="6" fill="#1F6FEB"/>
    </IconShell>
  );
}

// C — Dark mode-first: navy + cream peak
function IconC({ size }) {
  return (
    <IconShell size={size} bg="#0B1220">
      <defs>
        <radialGradient id="iconC-glow" cx=".5" cy=".7" r=".55">
          <stop offset="0" stopColor="#1F6FEB" stopOpacity=".55"/>
          <stop offset="1" stopColor="#1F6FEB" stopOpacity="0"/>
        </radialGradient>
      </defs>
      <rect width="200" height="200" fill="url(#iconC-glow)"/>
      {/* tan band */}
      <path d="M16 168 Q100 144 184 168 L184 188 Q100 174 16 188 Z" fill="#D9C3A0" opacity=".55"/>
      {/* shelter */}
      <path d="M20 164 Q100 18 180 164 L180 184 Q100 170 20 184 Z" fill="#F4EBDC"/>
      {/* keyhole */}
      <circle cx="100" cy="106" r="16" fill="#0B1220"/>
      <path d="M92 116 L88 154 L112 154 L108 116 Z" fill="#0B1220"/>
    </IconShell>
  );
}

// D — Outlined / linework — soft cream + thick blue
function IconD({ size }) {
  return (
    <IconShell size={size} bg="#F4EBDC">
      <g fill="none" stroke="#0F4AA8" strokeWidth="10" strokeLinejoin="round" strokeLinecap="round">
        <path d="M20 160 Q100 22 180 160 L180 184 Q100 162 20 184 Z"/>
        <circle cx="100" cy="100" r="14"/>
        <path d="M100 116 L100 152" strokeWidth="14"/>
      </g>
    </IconShell>
  );
}

// E — Two-tone layered shelter with bold gradient key
function IconE({ size }) {
  return (
    <IconShell size={size} bg="#EFE4D0">
      <defs>
        <linearGradient id="iconE-key" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#6366F1"/>
          <stop offset="1" stopColor="#1F6FEB"/>
        </linearGradient>
      </defs>
      {/* outer blue roof */}
      <path d="M16 160 Q100 18 184 160 Z" fill="#1F6FEB"/>
      {/* inner cream roof */}
      <path d="M40 160 Q100 60 160 160 Z" fill="#EFE4D0"/>
      {/* big key */}
      <circle cx="100" cy="108" r="20" fill="url(#iconE-key)"/>
      <circle cx="100" cy="108" r="7" fill="#EFE4D0"/>
      <path d="M92 124 L88 168 L112 168 L108 124 Z" fill="url(#iconE-key)"/>
      <rect x="108" y="152" width="14" height="8" rx="2" fill="url(#iconE-key)"/>
    </IconShell>
  );
}

// F — Geometric wordmark — bold K + nest swoop
function IconF({ size }) {
  return (
    <IconShell size={size} bg="#F4EBDC">
      {/* nest underline */}
      <path d="M22 172 Q100 144 178 172" stroke="#9B7A4A" strokeWidth="10" fill="none" strokeLinecap="round"/>
      {/* K */}
      <path d="M50 28 L50 158" stroke="#0F4AA8" strokeWidth="26" strokeLinecap="round"/>
      <path d="M50 96 L142 30" stroke="#1F6FEB" strokeWidth="26" strokeLinecap="round"/>
      <path d="M50 96 L150 158" stroke="#1F6FEB" strokeWidth="26" strokeLinecap="round"/>
      {/* key bow at top of the stem */}
      <circle cx="50" cy="42" r="14" fill="#F4EBDC"/>
    </IconShell>
  );
}

function IconCard({ title, sub, children }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, padding: 18, background: 'white', borderRadius: 20, boxShadow: '0 1px 2px rgba(0,0,0,.04), 0 6px 16px rgba(0,0,0,.06)' }}>
      <div style={{ display: 'flex', gap: 14, alignItems: 'flex-end' }}>
        {children}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ width: 56, height: 56, borderRadius: 14 }}>{React.cloneElement(children, { size: 56 })}</div>
          <div style={{ width: 36, height: 36, borderRadius: 10 }}>{React.cloneElement(children, { size: 36 })}</div>
        </div>
      </div>
      <div style={{ textAlign: 'center', marginTop: 4 }}>
        <div style={{ font: '700 14px/1.2 Manrope, sans-serif', color: '#0B1220' }}>{title}</div>
        <div style={{ font: '500 12px/1.4 Manrope, sans-serif', color: '#5C6B8E', marginTop: 2 }}>{sub}</div>
      </div>
    </div>
  );
}

function IconSet() {
  const opts = [
    { Component: IconA, title: 'A — Shelter (recommended)', sub: 'Cream + blue, traditional' },
    { Component: IconB, title: 'B — Filled / Monogram', sub: 'Blue, key-as-K' },
    { Component: IconC, title: 'C — Dark mode-first', sub: 'Navy + cream peak' },
    { Component: IconD, title: 'D — Outlined / Linework', sub: 'Soft, minimal' },
    { Component: IconE, title: 'E — Two-tone', sub: 'Layered shelter, gradient key' },
    { Component: IconF, title: 'F — Wordmark / Abstract', sub: 'K + roof, geometric' },
  ];
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 24, padding: 32, background: '#F0EEE9', font: '500 14px/1.4 Manrope, sans-serif' }}>
      {opts.map(({ Component, title, sub }, i) => (
        <IconCard key={i} title={title} sub={sub}>
          <Component size={140}/>
        </IconCard>
      ))}
    </div>
  );
}

Object.assign(window, { IconA, IconB, IconC, IconD, IconE, IconF, IconSet });
