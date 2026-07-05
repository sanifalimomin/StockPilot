

export function Logo({ className = 'h-9 w-9' }: { className?: string }) {
  return (
    <svg viewBox="0 0 48 48" className={className} role="img" aria-label="StockPilot logo">
      <defs>
        <linearGradient id="sp-gradient" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#4F46E5" />
          <stop offset="100%" stopColor="#0EA5E9" />
        </linearGradient>
      </defs>
      <rect width="48" height="48" rx="11" fill="url(#sp-gradient)" />
      {}
      <polygon points="24,10.5 35.5,17 24,23.5 12.5,17" fill="#fff" opacity="0.95" />
      <polygon points="12.5,17 24,23.5 24,37 12.5,30.5" fill="#fff" opacity="0.5" />
      <polygon points="35.5,17 24,23.5 24,37 35.5,30.5" fill="#fff" opacity="0.72" />
      {}
      <path
        d="M17 42.5 l4.5 -3.5 3 2 5.5 -4.5"
        fill="none"
        stroke="#fff"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <polygon points="31.7,35.1 30.3,38.2 28.4,35.9" fill="#fff" />
    </svg>
  );
}
