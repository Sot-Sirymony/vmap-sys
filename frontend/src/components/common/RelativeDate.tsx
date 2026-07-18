const DAY_MS = 24 * 60 * 60 * 1000;

function daysFromToday(iso: string): number {
  const today = new Date();
  const target = new Date(`${iso}T00:00:00`);
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((target.getTime() - startOfToday.getTime()) / DAY_MS);
}

function shortDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

/**
 * FR-27.1: dates in rows and cards read as time-to-act — "in 3 days",
 * "5 days overdue" — instead of raw ISO. The word "overdue" doubles as the
 * non-color channel BR-17 requires; the exact date stays in the tooltip.
 * Completed records show the plain date (no urgency left to signal).
 */
export function RelativeDate({ date, completed = false }: { date?: string; completed?: boolean }) {
  if (!date) {
    return <span>-</span>;
  }
  if (completed) {
    return <span title={date}>{shortDate(date)}</span>;
  }
  const diff = daysFromToday(date);
  if (diff < 0) {
    const days = -diff;
    return (
      <span className="date-overdue" title={date}>
        {days === 1 ? '1 day overdue' : `${days} days overdue`}
      </span>
    );
  }
  if (diff === 0) {
    return <span className="date-soon" title={date}>Today</span>;
  }
  if (diff === 1) {
    return <span className="date-soon" title={date}>Tomorrow</span>;
  }
  if (diff <= 14) {
    return <span title={date}>in {diff} days</span>;
  }
  return <span title={date}>{shortDate(date)}</span>;
}
