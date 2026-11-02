import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import { tr } from '../../i18n/translate';

export type ViewMode = 'board' | 'list';

export function ViewToggle({ value, onChange, label }: {
  value: ViewMode;
  onChange: (mode: ViewMode) => void;
  label: string;
}) {
  return (
    <ToggleButtonGroup
      className="view-toggle"
      value={value}
      exclusive
      size="small"
      onChange={(_event, next) => {
        if (next) {
          onChange(next);
        }
      }}
      aria-label={label}
    >
      <ToggleButton value="board">{tr('Board')}</ToggleButton>
      <ToggleButton value="list">{tr('List')}</ToggleButton>
    </ToggleButtonGroup>
  );
}
