/**
 * LiveBadge — small pulsing green dot + "Live" pill, with optional
 * "updated Xs ago" text. Used wherever data is real-time.
 */
export default function LiveBadge({ lastUpdatedSeconds }: { lastUpdatedSeconds?: number }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:border-emerald-500/25 dark:bg-emerald-500/10 dark:text-emerald-400">
      <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-500" />
      Live
      {lastUpdatedSeconds !== undefined && (
        <span className="font-medium text-emerald-600/70 dark:text-emerald-400/70">· {lastUpdatedSeconds}s ago</span>
      )}
    </span>
  );
}
