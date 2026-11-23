import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from 'recharts';
import { ChartTooltipContent } from '../ChartTooltipContent';
import { AreaGradient, useChartTheme } from './chartTheme';
import { tr } from '../../../i18n/translate';

/**
 * The dashboard's Recharts figure (the progress trend), kept out of
 * DashboardPage so that Recharts (and the d3 packages behind it) live in a
 * chunk the browser only fetches once the dashboard is on screen. The card,
 * empty state and table view stay in the page; only the drawing lives here.
 *
 * Each component renders the plot alone and fills its container, which means
 * the page keeps deciding how tall each figure is and the placeholder shown
 * while this chunk loads can reserve exactly that height.
 */

export type TrendPoint = { label: string; progress: number };

const percentTick = (value: number) => `${value}%`;

/**
 * BRD 25 §7.2 — the 12-week trend as an area line: dashed horizontal gridlines,
 * no axis lines, a soft brand gradient, and only the current (last) point drawn,
 * labelled with its value. Colours come from the chart theme (D-25-6).
 */
export function ProgressTrendChart({ data }: { data: TrendPoint[] }) {
  const theme = useChartTheme();
  const last = data.length - 1;
  const renderDot = ({ cx, cy, index }: { cx?: number; cy?: number; index?: number }) => {
    if (index !== last || cx === undefined || cy === undefined) {
      return <g key={`dot-${index}`} />;
    }
    return (
      <g key="dot-current">
        <circle cx={cx} cy={cy} r={4.5} fill={theme.brand} stroke={theme.surface} strokeWidth={2} />
        <text x={cx + 8} y={cy + 4} fill="currentColor" fontSize={13} fontWeight={600} style={{ fontVariantNumeric: 'tabular-nums' }}>
          {`${Math.round(data[last].progress)}%`}
        </text>
      </g>
    );
  };
  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 12, left: 0, right: 44, bottom: 0 }}>
        <defs>
          <AreaGradient id="v25-trend-fill" color={theme.brand} />
        </defs>
        <CartesianGrid {...theme.grid} />
        <XAxis dataKey="label" {...theme.axis} interval="preserveStartEnd" minTickGap={24} />
        <YAxis type="number" domain={[0, 100]} ticks={[0, 25, 50, 75, 100]} tickFormatter={percentTick} {...theme.axis} width={40} />
        <RechartsTooltip content={<ChartTooltipContent />} cursor={theme.tooltipCursor} />
        <Area
          dataKey="progress"
          name={tr('Average progress %')}
          type="monotone"
          fill="url(#v25-trend-fill)"
          stroke={theme.brand}
          strokeWidth={2}
          dot={renderDot}
          activeDot={theme.line.activeDot}
          {...theme.animation}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
