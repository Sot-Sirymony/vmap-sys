import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { listParticipantStreamlineReviews } from '../../api/streamlineApi';
import { tr } from '../../i18n/translate';
import type { StreamlineListItem } from '../../types/streamline';
import { STAGE_NAMES } from './streamlineLabels';

/** V27 D-27-10: a participant's Streamline reviews, read-only, for their coach. */
export function CoachStreamlineCard({ token, userId }: { token: string; userId: number }) {
  const [reviews, setReviews] = useState<StreamlineListItem[] | null>(null);
  const names = STAGE_NAMES();

  useEffect(() => {
    listParticipantStreamlineReviews(token, userId).then(setReviews).catch(() => setReviews([]));
  }, [token, userId]);

  if (!reviews || reviews.length === 0) return null;
  return (
    <Card>
      <CardHeader title={tr('Streamline reviews')} subheader={tr('How they trimmed their plan, read-only')} />
      <CardContent>
        <ul style={{ margin: 0, paddingLeft: 18 }}>
          {reviews.map((review) => (
            <li key={review.id}>
              <Link to={`/coach/participants/${userId}/streamline/${review.id}`}>{review.anchorTitle}</Link>{' '}
              <Typography component="span" variant="body2" sx={{ color: 'var(--text-secondary)' }}>
                {review.status === 'OPEN'
                  ? tr('Stage {number}: {name}', { number: review.currentStage, name: names[review.currentStage - 1] })
                  : review.status === 'COMPLETED' ? tr('Completed') : tr('Abandoned')}
                {' · '}
                {tr('Steps {before} → {after} · {cut} cut · {back} brought back', { before: review.stepsBefore, after: review.stepsAfter, cut: review.deleted, back: review.addedBack })}
              </Typography>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
