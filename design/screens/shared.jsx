// shared.jsx — Phone frame + shared UI atoms for KeyNest mocks
// All screens import these. Light/dark via .kn-light / .kn-dark wrapper.

const PHONE_W = 360;
const PHONE_H = 740;

function StatusBar() {
  return (
    <div className="kn-status">
      <span>9:41</span>
      <div className="kn-status-r">
        <svg width="16" height="11" viewBox="0 0 16 11" fill="none">
          <path d="M1 7.5C3.5 5 6.5 3.5 8 3.5s4.5 1.5 7 4l-1 1c-2-2-4.5-3.5-6-3.5S4 6.5 2 8.5l-1-1z" fill="currentColor" opacity=".95"/>
          <circle cx="8" cy="9" r="1.4" fill="currentColor"/>
        </svg>
        <svg width="16" height="11" viewBox="0 0 16 11" fill="none">
          <rect x="1" y="6" width="2" height="4" rx=".5" fill="currentColor"/>
          <rect x="5" y="4" width="2" height="6" rx=".5" fill="currentColor"/>
          <rect x="9" y="2" width="2" height="8" rx=".5" fill="currentColor"/>
          <rect x="13" y="0" width="2" height="10" rx=".5" fill="currentColor"/>
        </svg>
        <svg width="22" height="11" viewBox="0 0 22 11" fill="none">
          <rect x="0.5" y="0.5" width="18" height="10" rx="2.5" stroke="currentColor" opacity=".6"/>
          <rect x="2" y="2" width="14" height="7" rx="1" fill="currentColor"/>
          <rect x="19" y="3.5" width="1.5" height="4" rx=".5" fill="currentColor" opacity=".6"/>
        </svg>
      </div>
    </div>
  );
}

function HomeIndicator() {
  return <div className="kn-home" />;
}

// Tinted brand icon tile — derives a soft gradient from a hex color
function IconTile({ color = '#1F6FEB', letter = 'A', size = 44, radius = 12, style }) {
  const grad = `linear-gradient(135deg, ${color} 0%, ${shade(color, -18)} 100%)`;
  return (
    <div className="kn-icontile" style={{ width: size, height: size, borderRadius: radius, background: grad, fontSize: size * 0.38, ...style }}>
      {letter}
    </div>
  );
}

function shade(hex, percent) {
  const num = parseInt(hex.replace('#', ''), 16);
  let r = (num >> 16) + Math.round(2.55 * percent);
  let g = ((num >> 8) & 0xff) + Math.round(2.55 * percent);
  let b = (num & 0xff) + Math.round(2.55 * percent);
  r = Math.max(0, Math.min(255, r));
  g = Math.max(0, Math.min(255, g));
  b = Math.max(0, Math.min(255, b));
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

// Inline icons (24x24, currentColor)
const Icons = {
  search: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="2"/><path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>,
  plus: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round"/></svg>,
  more: <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>,
  close: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M6 6l12 12M18 6l-12 12" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg>,
  back: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M15 6l-6 6 6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  shield: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"/></svg>,
  shieldFill: <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z"/></svg>,
  check: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M5 12l5 5 9-10" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"/></svg>,
  eye: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" stroke="currentColor" strokeWidth="1.8"/><circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth="1.8"/></svg>,
  eyeOff: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M4 4l16 16M9 5.5A10 10 0 0 1 22 12c-.5 1-1.4 2.3-2.6 3.5M15 15a3 3 0 0 1-4.2-4.2M6.5 7.5C3.7 9.4 2 12 2 12s3.5 7 10 7c1.5 0 2.9-.3 4-.8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  copy: <svg width="22" height="22" viewBox="0 0 24 24" fill="none"><rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="1.8"/><path d="M5 15V6a2 2 0 0 1 2-2h8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"/></svg>,
  fingerprint: <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"><path d="M5 8.5a8 8 0 0 1 14 0"/><path d="M3 12a9 9 0 0 1 4.5-7.8"/><path d="M21 12c0 1.5-.2 3-.5 4.4"/><path d="M8 11a4 4 0 0 1 8 0c0 3-.5 5.5-2 7.5"/><path d="M12 11v3c0 2-.5 4-1.5 5.5"/><path d="M7 19c1-1 1.5-2.5 1.5-4"/><path d="M15 22c.6-1.4 1-3 1-4.5"/></svg>,
  face: <svg width="44" height="44" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round"><path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2"/><circle cx="9.5" cy="11" r=".6" fill="currentColor"/><circle cx="14.5" cy="11" r=".6" fill="currentColor"/><path d="M12 10v4M9.5 15.5c.6.6 1.5 1 2.5 1s1.9-.4 2.5-1"/></svg>,
  settings: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.8l-.1-.1A2 2 0 0 1 7 4.9l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/></svg>,
  key: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><circle cx="8" cy="15" r="4"/><path d="m10.8 12.2 9.2-9.2"/><path d="m16 7 3 3"/><path d="m15.5 10.5 2-2"/></svg>,
  clock: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2" strokeLinecap="round"/></svg>,
  warning: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round"><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01" strokeLinecap="round"/></svg>,
  chevron: <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="m9 6 6 6-6 6"/></svg>,
  filter: <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round"><path d="M3 5h18M6 12h12M10 19h4"/></svg>,
  lock: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>,
  unlock: <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 7.5-2"/></svg>,
};

// The KeyNest "tiny mark" — used on app bar, splash, etc.
function KNMark({ size = 28, color = 'var(--primary)' }) {
  return (
    <svg width={size} height={size} viewBox="0 0 40 40" fill="none">
      {/* nest "swoop" / shielded peak */}
      <path d="M20 5 L33 14 C33 27 27 33 20 35 C13 33 7 27 7 14 Z" fill={color}/>
      {/* key bow */}
      <circle cx="20" cy="18" r="3.4" fill="white"/>
      <rect x="18.7" y="18" width="2.6" height="9" rx="1.1" fill="white"/>
      <rect x="21.3" y="22.5" width="3" height="1.6" rx=".6" fill="white"/>
    </svg>
  );
}

Object.assign(window, { StatusBar, HomeIndicator, IconTile, Icons, KNMark, PHONE_W, PHONE_H, shade });
