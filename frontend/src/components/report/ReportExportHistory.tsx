import { useEffect, useState } from 'react';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import { listReportExports } from '../../api/savedReportApi';
import { formatDate } from '../../i18n/formatDate';
import { tr } from '../../i18n/translate';
import type { ReportExport } from '../../types/vision';

const FORMAT_LABELS: Record<ReportExport['format'], string> = { xlsx: 'Excel', pptx: 'PowerPoint', pdf: 'PDF' };

/** V19 RM-FR-25: a saved report's downloads, newest first. reloadKey changes after each download. */
export function ReportExportHistory({ token, reportId, reloadKey }: Readonly<{ token: string; reportId: number; reloadKey: string }>) {
  const [exports, setExports] = useState<ReportExport[]>([]);

  useEffect(() => {
    let active = true;
    listReportExports(token, reportId).then((list) => active && setExports(list)).catch(() => undefined);
    return () => {
      active = false;
    };
  }, [token, reportId, reloadKey]);

  return (
    <Card component="section" aria-label={tr('Download history')}>
      <CardHeader title={tr('Download history')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }} />
      <CardContent sx={{ pt: 0 }}>
        {exports.length === 0 ? (
          <Typography variant="body2">{tr('Not downloaded yet.')}</Typography>
        ) : (
          <ol style={{ margin: 0, paddingLeft: 20 }}>
            {exports.map((entry) => (
              <li key={entry.id}>
                <Typography variant="body2" component="span">
                  {`${tr(FORMAT_LABELS[entry.format])} · ${entry.language === 'km' ? 'ខ្មែរ' : 'English'} · `
                    + `${formatDate(new Date(entry.createdAt), { year: true })} ${new Date(entry.createdAt).toTimeString().slice(0, 5)}`}
                </Typography>
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}
