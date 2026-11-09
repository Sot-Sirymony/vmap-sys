import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Typography from '@mui/material/Typography';
import { tr } from '../../../i18n/translate';
import type { AssistantPage } from '../../../types/vision';

export const SWITCHABLE_PAGES: { page: AssistantPage; label: string }[] = [
  { page: 'MODULES', label: 'Modules list' },
  { page: 'MODULE', label: 'Module pages' },
  { page: 'ASSESSMENT', label: 'Assessment' },
  { page: 'OBSTACLES', label: 'Obstacles' },
  { page: 'COMMUNICATION', label: 'Communication' },
  { page: 'WORK_STYLE', label: 'Work Style' },
];

/**
 * V15 (AH-FR-57): one checkbox per page; ticked means the page assistant and
 * helpers are on there. Pages that are off still show the program's own help.
 */
export function PageSwitchesField({ pagesOff, onChange, disabled = false, legend }: Readonly<{
  pagesOff: AssistantPage[];
  onChange: (pagesOff: AssistantPage[]) => void;
  disabled?: boolean;
  legend: string;
}>) {
  return (
    <Box component="fieldset" sx={{ border: 0, m: 0, p: 0 }}>
      <Typography component="legend" variant="body2" sx={{ fontWeight: 600 }}>{legend}</Typography>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', columnGap: 2 }}>
        {SWITCHABLE_PAGES.map((item) => (
          <FormControlLabel
            key={item.page}
            control={(
              <Checkbox size="small" checked={!pagesOff.includes(item.page)} disabled={disabled}
                onChange={(event) => onChange(event.target.checked
                  ? pagesOff.filter((page) => page !== item.page)
                  : [...pagesOff, item.page])} />
            )}
            label={tr(item.label)}
          />
        ))}
      </Box>
    </Box>
  );
}
