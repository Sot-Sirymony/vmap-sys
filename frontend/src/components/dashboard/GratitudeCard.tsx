import { FormEvent, useState } from 'react';
import { X } from 'lucide-react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import FormControl from '@mui/material/FormControl';
import IconButton from '@mui/material/IconButton';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { archiveGratitudeEntry, createGratitudeEntry } from '../../api/gratitudeEntryApi';
import type { DashboardGratitude, GratitudeCategory } from '../../types/vision';
import { gratitudeCategoryLabels } from '../../utils/enumLabels';
import { Button } from '../common/Button';
import { EmptyState } from '../common/EmptyState';
import { Input } from '../common/Input';

/**
 * FR-59.2: this week's count plus the 2-3 most recent entries, with a
 * quick-add action right on the card — the counterweight to a dashboard
 * that otherwise only tracks what's unfinished, blocked, or overdue.
 * Diagnostic only (BR-48): nothing here gates anything else.
 */
export function GratitudeCard({ token, gratitude, onChanged }: {
  token: string | null;
  gratitude?: DashboardGratitude;
  onChanged: () => void;
}) {
  const [category, setCategory] = useState<GratitudeCategory>('OTHER');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const countThisWeek = gratitude?.countThisWeek ?? 0;
  const recent = gratitude?.recent ?? [];

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || !description.trim()) {
      return;
    }
    setSaving(true);
    setError('');
    try {
      await createGratitudeEntry(token, { category, description: description.trim() });
      setDescription('');
      setCategory('OTHER');
      onChanged();
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : 'Unable to log this.');
    } finally {
      setSaving(false);
    }
  }

  async function handleArchive(id: number) {
    if (!token) {
      return;
    }
    try {
      await archiveGratitudeEntry(token, id);
      onChanged();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : 'Unable to remove this.');
    }
  }

  return (
    <Card>
      <CardHeader
        title="Gratitude"
        subheader={`${countThisWeek} logged this week — what's already going well, not just what's unfinished`}
      />
      <CardContent>
        <Stack spacing={2}>
          <Box component="form" onSubmit={(event) => void handleSubmit(event)} sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <Select
                SelectDisplayProps={{ 'aria-label': 'Category' }}
                value={category}
                onChange={(event) => setCategory(event.target.value as GratitudeCategory)}
              >
                {(Object.keys(gratitudeCategoryLabels) as GratitudeCategory[]).map((value) => (
                  <MenuItem value={value} key={value}>{gratitudeCategoryLabels[value]}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <Box sx={{ flex: 1, minWidth: 180 }}>
              <Input
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="What are you grateful for?"
                aria-label="What are you grateful for?"
              />
            </Box>
            <Button type="submit" disabled={saving || !description.trim()}>
              {saving ? 'Logging…' : 'Log it'}
            </Button>
          </Box>
          {error && <Typography variant="body2" color="error">{error}</Typography>}
          {recent.length === 0 ? (
            <EmptyState>Nothing logged yet — the first entry starts the list.</EmptyState>
          ) : (
            <Stack spacing={1}>
              {recent.map((entry) => (
                <Stack key={entry.id} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                  <Typography variant="body2" sx={{ flex: 1 }}>
                    <strong>{gratitudeCategoryLabels[entry.category]}</strong> — {entry.description}
                  </Typography>
                  <IconButton size="small" aria-label="Remove" onClick={() => void handleArchive(entry.id)}>
                    <X size={14} />
                  </IconButton>
                </Stack>
              ))}
            </Stack>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
