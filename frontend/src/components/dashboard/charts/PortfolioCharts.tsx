import { Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from 'recharts';
import { ChartTooltipContent } from '../ChartTooltipContent';
import { chartPrimary } from '../../../theme';
import type { PartnerStatus } from '../../../types/vision';

/**
 * The dashboard's three Recharts figures, moved out of DashboardPage so that
 * Recharts (and the d3 packages behind it) live in a chunk the browser only
 * fetches once the dashboard is on screen, instead of inside the page bundle
 * itself. Everything around them — the cards, the empty states, the partner
 * legend — stays in the page, so only the drawing moved.
 *
 * Each component renders the plot alone and fills its container, which means
 * the page keeps deciding how tall each figure is and the placeholder shown
 * while this chunk loads can reserve exactly that height.
 */

export type TrendPoint = { label: string; progress: number };
export type AreaProgressPoint = { name: string; progress: number };

const percentTick = (value: number) => `${value}%`;

export function ProgressTrendChart({ data }: { data: TrendPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ left: 8, right: 16 }}>
        <CartesianGrid vertical={false} />
        <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} />
        <YAxis type="number" domain={[0, 100]} tickFormatter={percentTick} tickLine={false} axisLine={false} width={40} />
        <RechartsTooltip content={<ChartTooltipContent />} />
        <Area
          dataKey="progress"
          name="Average progress %"
          type="monotone"
          fill={chartPrimary}
          fillOpacity={0.15}
          stroke={chartPrimary}
          strokeWidth={2}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}

export function VisionAreaProgressChart({ data }: { data: AreaProgressPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} layout="vertical" margin={{ left: 8, right: 16 }}>
        <CartesianGrid horizontal={false} />
        <XAxis type="number" domain={[0, 100]} tickFormatter={percentTick} tickLine={false} axisLine={false} />
        <YAxis type="category" dataKey="name" tickLine={false} axisLine={false} width={120} />
        <RechartsTooltip content={<ChartTooltipContent />} />
        <Bar dataKey="progress" name="Progress %" radius={4} fill={chartPrimary} />
      </BarChart>
    </ResponsiveContainer>
  );
}

export function PartnerPipelineChart({
  data,
  statuses,
  labelFor,
  fillFor,
  onSelectStatus,
}: {
  data: Record<string, unknown>[];
  statuses: readonly PartnerStatus[];
  labelFor: (status: PartnerStatus) => string;
  fillFor: (status: PartnerStatus) => string;
  onSelectStatus: (status: PartnerStatus) => void;
}) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} layout="vertical" margin={{ left: 0, right: 0, top: 0, bottom: 0 }}>
        <XAxis type="number" hide />
        <YAxis type="category" dataKey="name" hide />
        <RechartsTooltip content={<ChartTooltipContent />} />
        {statuses.map((status) => (
          <Bar
            key={status}
            dataKey={status}
            stackId="pipeline"
            name={labelFor(status)}
            fill={fillFor(status)}
            cursor="pointer"
            onClick={() => onSelectStatus(status)}
          />
        ))}
      </BarChart>
    </ResponsiveContainer>
  );
}
