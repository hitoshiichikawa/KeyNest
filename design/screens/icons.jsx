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

// A — Selected direction: cream "shelter" silhouette, blue roof + key dot
function IconA({ size }) {
  return (
    <IconShell size={size} bg="#F4EBDC">
      <defs>
        <linearGradient id="iconA-roof" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#2A7BF5"/>
          <stop offset="1" stopColor="#1457C9"/>
        </linearGradient>
      </defs>
      {/* nest base (tan) */}
      <path d="M40 130 Q100 60 160 130 L160 152 Q100 132 40 152 Z" fill="#D9C3A0"/>
      {/* roof / shelter */}
      <path d="M52 128 Q100 72 148 128 Z" fill="url(#iconA-roof)"/>
      {/* keyhole */}
      <circle cx="100" cy="108" r="8.5" fill="#F4EBDC"/>
      <rect x="96.5" y="111" width="7" height="14" rx="2.5" fill="#F4EBDC"/>
      {/* twig accent */}
      <path d="M60 142 L86 138 M118 138 L142 142" stroke="#9B7A4A" strokeWidth="2.2" strokeLinecap="round"/>
    </IconShell>
  );
}

// B — Filled blue, monogram-key
function IconB({ size }) {
  return (
    <IconShell size={size} bg="#1F6FEB">
      {/* paper nest light */}
      <path d="M50 132 Q100 80 150 132 Z" fill="#F4EBDC" opacity=".95"/>
      {/* "K" notch as a key bit */}
      <path d="M100 105 L100 130 M100 117 L113 105 M100 117 L113 130"
        stroke="#1F6FEB" strokeWidth="6.5" strokeLinecap="round" fill="none"/>
      {/* key bow */}
      <circle cx="85" cy="117" r="6.5" fill="none" stroke="#1F6FEB" strokeWidth="4"/>
    </IconShell>
  );
}

// C — Dark mode-first: deep navy with cream peak + glow
function IconC({ size }) {
  return (
    <IconShell size={size} bg="#0B1220">
      <defs>
        <radialGradient id="iconC-glow" cx=".5" cy=".75" r=".6">
          <stop offset="0" stopColor="#1F6FEB" stopOpacity=".55"/>
          <stop offset="1" stopColor="#1F6FEB" stopOpacity="0"/>
        </radialGradient>
      </defs>
      <rect x="0" y="0" width="200" height="200" fill="url(#iconC-glow)"/>
      <path d="M50 130 Q100 70 150 130 L150 144 Q100 128 50 144 Z" fill="#F4EBDC"/>
      <circle cx="100" cy="113" r="7.5" fill="#0B1220"/>
      <rect x="96.5" y="116" width="7" height="13" rx="2.4" fill="#0B1220"/>
    </IconShell>
  );
}

// D — Outlined "draft" style — soft cream, blue line work
function IconD({ size }) {
  return (
    <IconShell size={size} bg="#F4EBDC">
      <g fill="none" stroke="#1457C9" strokeWidth="5" strokeLinejoin="round" strokeLinecap="round">
        <path d="M45 138 Q100 70 155 138 L155 154 Q100 134 45 154 Z"/>
        <circle cx="100" cy="112" r="9"/>
        <path d="M100 121 L100 134"/>
      </g>
    </IconShell>
  );
}

// E — Two-tone shelter with gradient key
function IconE({ size }) {
  return (
    <IconShell size={size} bg="#EFE4D0">
      <defs>
        <linearGradient id="iconE-key" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#5E4FE0"/>
          <stop offset="1" stopColor="#1F6FEB"/>
        </linearGradient>
      </defs>
      <path d="M50 134 Q100 72 150 134 Z" fill="#1F6FEB"/>
      <path d="M58 134 Q100 88 142 134 Z" fill="#D9C3A0"/>
      <circle cx="100" cy="115" r="9" fill="url(#iconE-key)"/>
      <rect x="96.5" y="118" width="7" height="16" rx="2.5" fill="url(#iconE-key)"/>
    </IconShell>
  );
}

// F — Modern wordmark / abstract — minimal "K + roof"
function IconF({ size }) {
  return (
    <IconShell size={size} bg="#F4EBDC">
      <path d="M70 60 L70 145" stroke="#0F4AA8" strokeWidth="14" strokeLinecap="round"/>
      <path d="M70 102 L130 60" stroke="#1F6FEB" strokeWidth="14" strokeLinecap="round"/>
      <path d="M70 102 L132 145" stroke="#1F6FEB" strokeWidth="14" strokeLinecap="round"/>
      {/* nest swoop */}
      <path d="M44 156 Q100 140 156 156" stroke="#9B7A4A" strokeWidth="6" fill="none" strokeLinecap="round"/>
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
