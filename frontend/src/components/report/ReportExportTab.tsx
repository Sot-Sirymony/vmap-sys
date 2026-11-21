import { useState } from 'react';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Typography from '@mui/material/Typography';
import { FileSpreadsheet, Presentation } from 'lucide-react';
import { downloadManagementReport, type DeckOptions, type ReportFilter, type ReportFormat } from '../../api/reportApi';
import { useI18n } from '../../i18n/I18nContext';
import { saveBlob } from '../../utils/saveBlob';
import { Button } from '../common/Button';
import { Input } from '../common/Input';
import Checkbox from '@mui/material/Checkbox';
import { tr } from '../../i18n/translate';
import type { ReportSection } from '../../types/vision';
import { ALL_SECTIONS, SECTION_LABELS } from '../../utils/managementReport';
import { Select } from '../common/Select';

/**
 * V18 MR-FR-15, MR-FR-23: the Export tab. Each file follows the scope chosen
 * above. V19 RM-FR-39, RM-FR-44: the participant picks the file language
 * (default: the app's, or a final report's own) and the sections to include;
 * a saved report remembers the sections through onSectionsChange.
 */
export function ReportExportTab({
  token, filter, disabledReason, download: downloadFile, note, sections: initialSections, onSectionsChange, defaultLanguage,
}: Readonly<{
  token: string;
  filter: ReportFilter;
  /** MR-FR-05: why the buttons are off, or empty when they are on. */
  disabledReason: string;
  /** V19 RM-FR-38: how a saved report downloads its files; the live report's scope otherwise. */
  download?: (format: ReportFormat, language: string, deck: DeckOptions) => Promise<{ blob: Blob; fileName: string }>;
  /** V19 RM-FR-43: a line above the buttons, e.g. that a draft's files are marked DRAFT. */
  note?: string;
  /** RM-FR-39: the sections chosen before (all when left out). */
  sections?: ReportSection[];
  onSectionsChange?: (sections: ReportSection[]) => void;
  /** RM-FR-44: the file language to start with, e.g. a final report's own. */
  defaultLanguage?: string;
}>) {
  const { language: appLanguage } = useI18n();
  const [language, setLanguage] = useState(defaultLanguage ?? appLanguage);
  const [sections, setSections] = useState<ReportSection[]>(initialSections?.length ? initialSections : ALL_SECTIONS);
  const [busy, setBusy] = useState<ReportFormat | null>(null);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  // MR-FR-31: deck options.
  const [deckTitle, setDeckTitle] = useState('');
  const [includeName, setIncludeName] = useState(true);

  async function download(format: ReportFormat) {
    setBusy(format);
    setError('');
    setSaved('');
    try {
      // All sections is the default, so it is left out of the request.
      const chosen = sections.length === ALL_SECTIONS.length ? {} : { sections };
      const deck = format === 'pptx' ? { title: deckTitle, includeName, ...chosen } : chosen;
      const { blob, fileName } = downloadFile
        ? await downloadFile(format, language, deck)
        : await downloadManagementReport(token, filter, format, language, deck);
      saveBlob(blob, fileName);
      setSaved(tr('Downloaded {file}.', { file: fileName }));
    } catch (downloadError) {
      setError(downloadError instanceof Error ? downloadError.message : tr('Unable to download the report.'));
    } finally {
      setBusy(null);
    }
  }

  function toggle(section: ReportSection, on: boolean) {
    const next = ALL_SECTIONS.filter((value) => (value === section ? on : sections.includes(value)));
    setSections(next);
    onSectionsChange?.(next);
  }

  const off = Boolean(disabledReason) || sections.length === 0;
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
      {note && <Alert severity="info" sx={{ gridColumn: '1 / -1' }}>{note}</Alert>}
      <Card component="section" aria-label={tr('What the files include')} sx={{ gridColumn: '1 / -1' }}>
        <CardHeader title={tr('What the files include')} titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }}
          subheader={tr('The title, executive summary and as-of line are always included. What changed needs a comparison.')} />
        <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
          <label>
            {tr('File language')}
            <Select value={language} onChange={(event) => setLanguage(event.target.value)}>
              <option value="en">English</option>
              <option value="km">ខ្មែរ (Khmer)</option>
            </Select>
          </label>
          <Box component="fieldset" sx={{ border: 0, m: 0, p: 0, display: 'grid', gap: 0.5, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: 'repeat(4, 1fr)' } }}>
            <Typography component="legend" variant="body2" sx={{ mb: 0.5 }}>{tr('Sections')}</Typography>
            {ALL_SECTIONS.map((section) => (
              <label className="checkbox-field" key={section}>
                <Checkbox checked={sections.includes(section)} onChange={(event) => toggle(section, event.target.checked)} />
                {tr(SECTION_LABELS[section])}
              </label>
            ))}
          </Box>
          {sections.length === 0 && <Alert severity="warning">{tr('Choose at least one section.')}</Alert>}
        </CardContent>
      </Card>
      <Card component="section" aria-label={tr('Excel report')}>
        <CardHeader avatar={<FileSpreadsheet aria-hidden="true" />} title={tr('Excel report')}
          titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }} />
        <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
          <Typography variant="body2">
            {tr('One row per task with its goal and step, blockers and management issues, progress, priority, due date and next action. Also a summary sheet and a list of every blocker.')}
          </Typography>
          <Box>
            <Button onClick={() => void download('xlsx')} disabled={off || busy !== null}>
              {busy === 'xlsx' ? tr('Preparing…') : tr('Export to Excel')}
            </Button>
          </Box>
        </CardContent>
      </Card>
      <Card component="section" aria-label={tr('PowerPoint deck')}>
        <CardHeader avatar={<Presentation aria-hidden="true" />} title={tr('PowerPoint deck')}
          titleTypographyProps={{ variant: 'subtitle1', component: 'h3', fontWeight: 600 }} />
        <CardContent sx={{ pt: 0, display: 'grid', gap: 1.5 }}>
          <Typography variant="body2">
            {tr('A short deck for a management review: progress by goal, priorities, blockers, overdue items, next actions and the support needed.')}
          </Typography>
          <label>
            {tr('Deck title (optional)')}
            <Input value={deckTitle} maxLength={80} placeholder={tr('Progress report')} onChange={(event) => setDeckTitle(event.target.value)} />
          </label>
          <label className="checkbox-field">
            <Checkbox checked={includeName} onChange={(event) => setIncludeName(event.target.checked)} />
            {tr('Put my name on the title slide')}
          </label>
          <Box>
            <Button onClick={() => void download('pptx')} disabled={off || busy !== null}>
              {busy === 'pptx' ? tr('Preparing…') : tr('Export to PowerPoint')}
            </Button>
          </Box>
        </CardContent>
      </Card>
      {disabledReason && <Alert severity="info" sx={{ gridColumn: '1 / -1' }}>{disabledReason}</Alert>}
      {error && <Alert severity="error" sx={{ gridColumn: '1 / -1' }}>{error}</Alert>}
      {saved && <Alert severity="success" sx={{ gridColumn: '1 / -1' }} role="status">{saved}</Alert>}
    </Box>
  );
}
