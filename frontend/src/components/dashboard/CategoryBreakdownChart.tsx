import { useNavigate } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Pie, PieChart, Rectangle, ResponsiveContainer, Sector, Tooltip, XAxis } from 'recharts';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { ChartTooltipContent } from './ChartTooltipContent';

// A single-hue depth ramp off Fluent's Communication Blue — pressed to light
// tint — for charts with no specific semantic color mapping. Category is
// encoded as shade rather than hue, so the chart reads as one calibrated
// system instead of a rainbow of unrelated colors. Warm hues (red/amber/
// green) are reserved for status/priority semantics elsewhere. Ordered
// darkest-first (not light-to-dark) since most charts here only ever show
// 2-4 categories — the pale end of the ramp is a fallback for the rare
// 5th/6th slot, not what a typical 3-category chart should lead with.
const DEFAULT_DONUT_COLORS = ['#005a9e', '#0078d4', '#2b88d8', '#71afe5', '#c7e0f4', '#deecf9'];

type CategoryBreakdownChartProps = {
  title: string;
  description: string;
  data: Record<string, number>;
  formatLabel?: (key: string) => string;
  variant?: 'bar' | 'donut';
  colorForKey?: (key: string) => string;
  /**
   * Makes a slice or bar a way in: clicking it opens the list filtered to that
   * category. Only pass it when a filter reproduces the segment exactly.
   */
  linkForKey?: (key: string) => string;
};

export function CategoryBreakdownChart({
  title,
  description,
  data,
  formatLabel = (key) => key,
  variant = 'bar',
  colorForKey,
  linkForKey,
}: CategoryBreakdownChartProps) {
  const navigate = useNavigate();
  const chartData = Object.entries(data).map(([key, count], index) => ({
    category: formatLabel(key),
    count,
    key,
    fill: colorForKey ? colorForKey(key) : DEFAULT_DONUT_COLORS[index % DEFAULT_DONUT_COLORS.length],
  }));

  // Recharts hands the clicked datum back untyped, so narrow it here rather than
  // fight the library's overloads.
  const openCategory = linkForKey
    ? (entry: unknown) => {
        const key = (entry as { key?: string } | null | undefined)?.key;
        if (key) {
          navigate(linkForKey(key));
        }
      }
    : undefined;

  return (
    <Card>
      <CardHeader title={title} subheader={description} />
      <CardContent>
        {chartData.length === 0 ? (
          <Box sx={{ display: 'flex', height: 220, alignItems: 'center', justifyContent: 'center' }}>
            <Box component="span" sx={{ fontSize: '0.875rem', color: 'text.secondary' }}>No data yet.</Box>
          </Box>
        ) : variant === 'donut' ? (
          <>
            <Box sx={{ width: '100%', height: 220 }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Tooltip content={<ChartTooltipContent />} />
                  <Pie
                    data={chartData}
                    dataKey="count"
                    nameKey="category"
                    innerRadius={55}
                    outerRadius={85}
                    paddingAngle={2}
                    onClick={openCategory}
                    shape={(props) => {
                      const { payload, ...sectorProps } = props;
                      return <Sector {...sectorProps} fill={payload.fill} />;
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
            </Box>
            <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', justifyContent: 'center', pt: 1.5 }}>
              {chartData.map((entry) => (
                <Stack key={entry.key} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
                  <Box sx={{ width: 8, height: 8, borderRadius: '2px', bgcolor: entry.fill }} />
                  <Typography variant="caption" color="text.secondary">{entry.category}</Typography>
                </Stack>
              ))}
            </Stack>
          </>
        ) : (
          <Box sx={{ width: '100%', height: 220 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid vertical={false} />
                <XAxis dataKey="category" tickLine={false} axisLine={false} tickMargin={8} />
                <Tooltip content={<ChartTooltipContent />} />
                <Bar
                  dataKey="count"
                  fill="#0078d4"
                  radius={4}
                  onClick={openCategory}
                  shape={
                    colorForKey
                      ? (props) => {
                          const { payload, ...rectProps } = props;
                          return <Rectangle {...rectProps} fill={payload.fill} />;
                        }
                      : undefined
                  }
                />
              </BarChart>
            </ResponsiveContainer>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
