import Checkbox from '@mui/material/Checkbox';
import { tr } from '../../i18n/translate';

type ShowArchivedToggleProps = {
  checked: boolean;
  onToggle: () => void;
};

/** The "Show archived" checkbox shared by every list page. */
export function ShowArchivedToggle({ checked, onToggle }: ShowArchivedToggleProps) {
  return (
    <label className="checkbox-field">
      <Checkbox checked={checked} onChange={onToggle} />
      {tr('Show archived')}
    </label>
  );
}
