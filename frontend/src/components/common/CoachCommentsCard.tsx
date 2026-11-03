import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { listMyCoachComments, markCoachCommentRead } from '../../api/cohortApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { CoachComment } from '../../types/vision';
import { Button } from './Button';

const SHOWN = 5;

/** V9 (v0.1 FR-17): what the participant's coach wrote to them. Hidden until there is something. */
export function CoachCommentsCard({ token }: { token: string | null }) {
  const [comments, setComments] = useState<CoachComment[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) {
      return;
    }
    listMyCoachComments(token)
      .then(setComments)
      .catch(() => setComments([]));
  }, [token]);

  async function markRead(id: number) {
    if (!token) {
      return;
    }
    setError('');
    try {
      const read = await markCoachCommentRead(token, id);
      setComments((current) => current.map((comment) => (comment.id === id ? read : comment)));
    } catch (readError) {
      setError(readError instanceof Error ? tr(readError.message) : tr('Unable to save this.'));
    }
  }

  if (comments.length === 0) {
    return null;
  }
  const unread = comments.filter((comment) => !comment.readAt).length;

  return (
    <Card>
      <CardHeader
        title={tr('From your coach')}
        subheader={unread > 0 ? tr('{count} new', { count: unread }) : tr('All read')}
      />
      <CardContent>
        <Stack spacing={1.5}>
          {error && <Alert severity="error">{error}</Alert>}
          {comments.slice(0, SHOWN).map((comment) => (
            <Box key={comment.id} sx={{ borderLeft: 3, borderColor: comment.readAt ? 'divider' : 'primary.main', pl: 1.5 }}>
              <Typography variant="caption" color="text.secondary">
                {comment.coachName ?? tr('Your coach')} · {formatDate(new Date(comment.createdAt))}
                {comment.targetTitle ? ` · ${comment.targetTitle}` : ''}
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{comment.body}</Typography>
              {!comment.readAt
                ? <Button type="button" variant="secondary" onClick={() => void markRead(comment.id)}>{tr('Mark as read')}</Button>
                : <Chip size="small" label={tr('Read')} sx={{ mt: 0.5 }} />}
            </Box>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
}
