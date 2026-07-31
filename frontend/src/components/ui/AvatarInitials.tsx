const AVATAR_COLORS = [
  'bg-brand-600',
  'bg-sky-600',
  'bg-violet-600',
  'bg-emerald-600',
  'bg-amber-600',
  'bg-rose-600',
] as const;

/** Deterministic color per name — same name always maps to the same color. */
function colorForName(name: string): string {
  const hash = name
    .split('')
    .reduce((acc, ch) => (acc * 31 + ch.charCodeAt(0)) >>> 0, 7);
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function initialsFor(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

const SIZES = {
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-14 w-14 text-lg',
} as const;

export default function AvatarInitials({
  name,
  size = 'md',
  imgUrl,
}: {
  name: string;
  size?: keyof typeof SIZES;
  imgUrl?: string | null;
}) {
  if (imgUrl) {
    return (
      <img
        src={imgUrl}
        alt={name}
        className={`${SIZES[size]} shrink-0 rounded-full object-cover ring-2 ring-white shadow-card`}
      />
    );
  }
  return (
    <div
      className={`${SIZES[size]} ${colorForName(name)} flex shrink-0 items-center justify-center rounded-full font-bold text-white ring-2 ring-white shadow-card`}
    >
      {initialsFor(name)}
    </div>
  );
}
