import { Suspense, lazy } from 'react';
import type { ComponentProps } from 'react';
import Box from '@mui/material/Box';
import Skeleton from '@mui/material/Skeleton';

/**
 * Every Recharts-backed figure on the dashboard, wrapped so the page can use
 * them like ordinary components while their code arrives separately.
 *
 * Recharts and its d3 dependencies were the bulk of the dashboard's bundle, and
 * the dashboard is the first screen after sign-in — so the numbers, the
 * attention panel and the task table had to wait on chart code before any of
 * them could render. Loading the charts as their own chunk lets the rest of the
 * page paint first, and each figure shows a placeholder of its own size, so
 * nothing below it moves when the chunk lands.
 *
 * The real modules are imported for their types only, which TypeScript erases,
 * so referring to them here does not pull Recharts back into this chunk.
 */

type PortfolioCharts = typeof import('./PortfolioCharts');
type CategoryBreakdown = typeof import('../CategoryBreakdownChart');

function ChartPlaceholder() {
  return (
    <Box sx={{ width: '100%', height: '100%', display: 'flex', alignItems: 'stretch' }}>
      <Skeleton variant="rounded" animation="wave" sx={{ width: '100%', height: '100%' }} />
    </Box>
  );
}

const ProgressTrendChartLazy = lazy(() =>
  import('./PortfolioCharts').then((m) => ({ default: m.ProgressTrendChart })));
const VisionAreaProgressChartLazy = lazy(() =>
  import('./PortfolioCharts').then((m) => ({ default: m.VisionAreaProgressChart })));
const PartnerPipelineChartLazy = lazy(() =>
  import('./PortfolioCharts').then((m) => ({ default: m.PartnerPipelineChart })));
const CategoryBreakdownChartLazy = lazy(() =>
  import('../CategoryBreakdownChart').then((m) => ({ default: m.CategoryBreakdownChart })));

export function ProgressTrendChart(props: ComponentProps<PortfolioCharts['ProgressTrendChart']>) {
  return (
    <Suspense fallback={<ChartPlaceholder />}>
      <ProgressTrendChartLazy {...props} />
    </Suspense>
  );
}

export function VisionAreaProgressChart(props: ComponentProps<PortfolioCharts['VisionAreaProgressChart']>) {
  return (
    <Suspense fallback={<ChartPlaceholder />}>
      <VisionAreaProgressChartLazy {...props} />
    </Suspense>
  );
}

export function PartnerPipelineChart(props: ComponentProps<PortfolioCharts['PartnerPipelineChart']>) {
  return (
    <Suspense fallback={<ChartPlaceholder />}>
      <PartnerPipelineChartLazy {...props} />
    </Suspense>
  );
}

/** Its own card, so the placeholder stands in for the whole card's body. */
export function CategoryBreakdownChart(props: ComponentProps<CategoryBreakdown['CategoryBreakdownChart']>) {
  return (
    <Suspense fallback={<Box sx={{ minHeight: 280 }}><ChartPlaceholder /></Box>}>
      <CategoryBreakdownChartLazy {...props} />
    </Suspense>
  );
}
