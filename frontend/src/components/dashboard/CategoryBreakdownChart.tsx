import { useNavigate } from 'react-router';
import { Bar, BarChart, CartesianGrid, Pie, PieChart, Rectangle, ResponsiveContainer, Sector, Tooltip, XAxis } from 'recharts';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { ChartTooltipContent } from './ChartTooltipContent';
import { CATEGORICAL_PIE_SLOTS, categoricalPieColor, chartBlueRamp, chartPrimary, pieLabelColor } from '../../theme';
import { useThemeSettings } from '../../context/ThemeModeContext';

// A single-hue depth ramp off Fluent's Communication Blue — pressed to light
// tint — for charts with no specific semantic color mapping. Category is
// encoded as shade rather than hue, so the chart reads as one calibrated
// system instead of a rainbow of unrelated colors. Warm hues (red/amber/
// green) are reserved for status/priority semantics elsewhere. Ordered
// darkest-first (not light-to-dark) since most charts here only ever show
// 2-4 categories — the pale end of the ramp is a fallback for the rare
// 5th/6th slot, not what a typical 3-category chart should lead with.
const DEFAULT_DONUT_COLORS = [...chartBlueRamp];

/**
 * The key the rolled-up tail carries. Charts that fold their tail get one slice
 * standing for several categories, so `linkForKey` should send it to the
 * unfiltered list.
 */
export const OTHER_CATEGORY_KEY = 'OTHER_CATEGORIES';

type CategoryBreakdownChartProps = {
  title: string;
  description: string;
  data: Record<string, number>;
  formatLabel?: (key: string) => string;
  variant?: 'bar' | 'donut' | 'pie';
  colorForKey?: (key: string) => string;
  /**
   * Makes a slice or bar a way in: clicking it opens the list filtered to that
   * category. Only pass it when a filter reproduces the segment exactly.
   */
  linkForKey?: (key: string) => string;
  /**
   * What to call the rolled-up tail once the categories outrun the palette —
   * "Other areas", "Other types". Only meaningful for the pie variant, which is
   * the one that assigns its own fills from the categorical palette.
   */
  otherLabel?: string;
};

type Slice = { category: string; count: number; key: string; fill: string };

/**
 * FR-41 — the solid pie: percentage labels sitting on the slices, a dashed rule,
 * then a dot legend underneath.
 *
 * The percentage is drawn *on* the slice, so the label colour has to work
 * against every fill. That is why the palette is mode-specific and why the label
 * colour comes from the theme rather than being hardcoded white: white on a
 * light-mode slice, near-black on a dark-mode one.
 *
 * The legend carries the category names and the tooltip carries the counts, so
 * nothing here is encoded by colour alone — which is what lets the palette use
 * four distinct hues at all (BR-14).
 */
function PieBreakdown({
  data,
  total,
  onSliceClick,
}: {
  data: Slice[];
  total: number;
  onSliceClick?: (entry: unknown) => void;
}) {
  const { settings, resolvedMode } = useThemeSettings();
  const labelColor = pieLabelColor[resolvedMode];

  // Slices under this share of the pie have no room for a legible label; the
  // legend and tooltip still name them, so nothing is lost by omitting it.
  const MIN_LABELLED_SHARE = 0.05;

  return (
    <>
      <Box sx={{ width: '100%', height: 240 }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Tooltip content={<ChartTooltipContent />} />
            <Pie
              data={data}
              dataKey="count"
              nameKey="category"
              outerRadius={95}
              // Solid pie, no gap and no stroke — the flat look of the reference.
              paddingAngle={0}
              stroke="none"
              isAnimationActive={!settings.reduceMotion}
              onClick={onSliceClick}
              label={({ cx, cy, midAngle, innerRadius, outerRadius, value, index }) => {
                const share = total > 0 ? (value as number) / total : 0;
                if (share < MIN_LABELLED_SHARE) {
                  return null;
                }
                // Place the text at the centroid of the slice.
                const radius = (innerRadius as number) + ((outerRadius as number) - (innerRadius as number)) * 0.55;
                const radians = -(midAngle as number) * (Math.PI / 180);
                return (
                  <text
                    key={data[index as number]?.key}
                    x={(cx as number) + radius * Math.cos(radians)}
                    y={(cy as number) + radius * Math.sin(radians)}
                    fill={labelColor}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fontSize={13}
                    fontWeight={600}
                  >
                    {`${(share * 100).toFixed(1)}%`}
                  </text>
                );
              }}
              labelLine={false}
              shape={(props) => {
                const { payload, ...sectorProps } = props;
                return <Sector {...sectorProps} fill={payload.fill} />;
              }}
            />
          </PieChart>
        </ResponsiveContainer>
      </Box>
      {/* The dashed rule from the reference, in the theme's own border token so
          it follows mode, high contrast, and background tone. */}
      <Box sx={{ borderTop: '1px dashed', borderColor: 'divider', mt: 1, mb: 1.5 }} />
      <Stack
        direction="row"
        spacing={2.5}
        sx={{ flexWrap: 'wrap', justifyContent: 'center', rowGap: 1 }}
      >
        {data.map((entry) => (
          <Stack key={entry.key} direction="row" spacing={0.75} sx={{ alignItems: 'center' }}>
            <Box sx={{ width: 10, height: 10, borderRadius: '50%', bgcolor: entry.fill, flexShrink: 0 }} />
            <Typography variant="body2" color="text.secondary">{entry.category}</Typography>
          </Stack>
        ))}
      </Stack>
    </>
  );
}

export function CategoryBreakdownChart({
  title,
  description,
  data,
  formatLabel = (key) => key,
  variant = 'bar',
  colorForKey,
  linkForKey,
  otherLabel = 'Other',
}: CategoryBreakdownChartProps) {
  const navigate = useNavigate();
  const { settings, resolvedMode } = useThemeSettings();

  const entries = Object.entries(data);

  // The pie assigns its own fills, because it is the variant bound by the
  // categorical palette's fixed number of slots. Once the categories outrun the
  // slots, the smallest are rolled into a single "Other" slice — reaching for
  // one more colour would mean repeating one, and two slices of a pie sharing a
  // fill is a claim that they are the same category.
  //
  // Size decides *which* categories keep their own slice, but the slices are
  // then laid out and coloured in the order the data arrived, not by rank. A
  // colour has to follow the category, not its position in the ranking:
  // this dashboard filters by vision area and by period, and if colour tracked
  // rank then narrowing the period would repaint every slice as the counts
  // reshuffled.
  const pieData = (() => {
    if (variant !== 'pie') {
      return [];
    }
    const overflows = entries.length > CATEGORICAL_PIE_SLOTS;
    const named = overflows
      ? new Set(
        [...entries]
          .sort(([, first], [, second]) => second - first)
          .slice(0, CATEGORICAL_PIE_SLOTS - 1)
          .map(([key]) => key),
      )
      : new Set(entries.map(([key]) => key));

    const slices = entries
      .filter(([key]) => named.has(key))
      .map(([key, count]) => ({ key, category: formatLabel(key), count }));

    if (overflows) {
      const tail = entries
        .filter(([key]) => !named.has(key))
        .reduce((sum, [, count]) => sum + count, 0);
      slices.push({ key: OTHER_CATEGORY_KEY, category: otherLabel, count: tail });
    }

    return slices.map((slice, index) => ({
      ...slice,
      fill: categoricalPieColor(index, resolvedMode, settings.highContrast),
    }));
  })();

  const chartData = variant === 'pie'
    ? pieData
    : entries.map(([key, count], index) => ({
      category: formatLabel(key),
      count,
      key,
      fill: colorForKey ? colorForKey(key) : DEFAULT_DONUT_COLORS[index % DEFAULT_DONUT_COLORS.length],
    }));

  // Percentages are computed here, not taken from the data, so a pie always sums
  // to 100% of what it is actually showing.
  const total = chartData.reduce((sum, entry) => sum + entry.count, 0);

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
            <Box component="span" sx={{ fontSize: 'var(--font-body-sm)', color: 'text.secondary' }}>No data yet.</Box>
          </Box>
        ) : variant === 'pie' ? (
          <PieBreakdown data={chartData} total={total} onSliceClick={openCategory} />
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
                  fill={chartPrimary}
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
