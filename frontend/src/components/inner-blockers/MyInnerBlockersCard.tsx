import { useEffect, useState } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';
import { Link as RouterLink } from 'react-router';
import { GapsRuleLine } from './GapsRuleLine';
import { listDreams } from '../../api/dreamApi';
import { tr } from '../../i18n/translate';
import { InnerBlockerList } from './InnerBlockerList';

/**
 * V34 (§2.2, D-34-4): the My inner blockers section of the Assessment page,
 * where the participant sees, edits, resolves and archives every blocker.
 */
export function MyInnerBlockersCard({ token }: { token: string }) {
  const [dreams, setDreams] = useState<{ id: number; title: string }[]>([]);

  useEffect(() => {
    let cancelled = false;
    listDreams(token)
      .then((list) => !cancelled && setDreams(list.map((dream) => ({ id: dream.id, title: dream.title }))))
      .catch(() => !cancelled && setDreams([]));
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <Card component="section" aria-labelledby="my-inner-blockers">
      <CardHeader
        title={<span id="my-inner-blockers">{tr('My inner blockers')}</span>}
        subheader={tr('Name what holds you back in your own words, how it shows up, and one short practice for it.')}
      />
      <CardContent sx={{ display: 'grid', gap: 1.5 }}>
        {/* V36 (D-36-7): where each thing belongs, with the way to the other page. */}
        <GapsRuleLine link={<Link component={RouterLink} to="/strengths">{tr('Open Strengths and Gaps')}</Link>} />
        <InnerBlockerList token={token} dreams={dreams} />
        <Typography variant="caption" color="text.secondary">
          {tr('This is a practice, not a diagnosis. If something feels heavy, talk to your coach or someone you trust.')}
        </Typography>
      </CardContent>
    </Card>
  );
}
