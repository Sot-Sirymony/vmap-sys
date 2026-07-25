import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { searchInsights } from '../api/insightApi';
import { EmptyState } from '../components/common/EmptyState';
import { ErrorMessage } from '../components/common/ErrorMessage';
import { Loading } from '../components/common/Loading';
import { SearchBar } from '../components/common/SearchBar';
import { useAuth } from '../context/AuthContext';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import type { Insight, InsightKind } from '../types/vision';
import { PageSection } from './PageSection';

const KIND_LABELS: Record<InsightKind, string> = {
  LESSON_LEARNED: 'Lesson learned',
  ROOT_CAUSE: 'Root cause',
  CREATIVE_ALTERNATIVES: 'Creative alternatives',
};

function titleCase(value: string): string {
  return value ? value.charAt(0).toUpperCase() + value.slice(1).toLowerCase() : value;
}

// Where the insight came from, and where to click back to for editing (FR-36.3).
function sourceLabel(insight: Insight): string {
  if (insight.source === 'REVIEW') {
    return `${titleCase(insight.sourceTitle)} review`;
  }
  return `Obstacle: ${insight.sourceTitle}`;
}

function sourceLink(insight: Insight): string {
  return insight.source === 'REVIEW' ? '/reviews' : '/obstacles';
}

function formatDate(dateIso?: string): string {
  if (!dateIso) {
    return '';
  }
  return new Date(`${dateIso}T00:00:00`).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

/**
 * FR-36.1: the Insight Library. A read-only, searchable view over the lessons
 * the user has already captured in reviews and obstacles — every card links
 * back to its source record for editing. It never authors anything.
 */
export function InsightsPage() {
  const { token } = useAuth();
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search, 300);
  const [insights, setInsights] = useState<Insight[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    setLoading(true);
    searchInsights(token, debouncedSearch)
      .then((data) => {
        setInsights(data);
        setError('');
      })
      .catch((loadError) => setError(loadError instanceof Error ? loadError.message : 'Unable to load insights.'))
      .finally(() => setLoading(false));
  }, [token, debouncedSearch]);

  return (
    <PageSection
      title="Insights"
      subtitle="Search the lessons you've already captured — reused instead of relearned."
    >
      <Card className="filter-bar flex-row">
        <SearchBar value={search} onChange={setSearch} entityLabel="insights" />
      </Card>

      {loading && <Loading variant="cards" rows={4} />}
      {error && <ErrorMessage message={error} />}

      {!loading && !error && insights.length === 0 && (
        <EmptyState>
          {debouncedSearch
            ? 'No insights match your search.'
            : 'No lessons captured yet. Your review lessons and obstacle root causes will collect here.'}
        </EmptyState>
      )}

      {!loading && !error && insights.length > 0 && (
        <Stack spacing={1.5}>
          {insights.map((insight, index) => (
            <Card key={`${insight.source}-${insight.sourceId}-${insight.kind}-${index}`}>
              <CardContent>
                <Stack direction="row" sx={{ alignItems: 'center', gap: 1, flexWrap: 'wrap', mb: 1 }}>
                  <Chip size="small" label={KIND_LABELS[insight.kind]} sx={{ fontWeight: 700 }} />
                  <Typography
                    component={Link}
                    to={sourceLink(insight)}
                    variant="body2"
                    sx={{ color: 'primary.main', fontWeight: 600, textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                  >
                    {sourceLabel(insight)}
                  </Typography>
                  {insight.date && (
                    <Typography variant="caption" color="text.secondary">· {formatDate(insight.date)}</Typography>
                  )}
                </Stack>
                <Typography variant="body2" sx={{ whiteSpace: 'pre-line' }}>{insight.content}</Typography>
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}
    </PageSection>
  );
}
