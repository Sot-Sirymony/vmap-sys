import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import TextField from '@mui/material/TextField';
import { Search, X } from 'lucide-react';
import { tr } from '../../i18n/translate';

type SearchBarProps = {
  value: string;
  onChange: (value: string) => void;
  /** Names what is being searched, e.g. "dreams". Used for the label and placeholder. */
  entityLabel: string;
};

/** Free-text search box for a list page. */
export function SearchBar({ value, onChange, entityLabel }: SearchBarProps) {
  return (
    <TextField
      className="search-bar"
      variant="outlined"
      size="small"
      type="search"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder={tr('Search {items}...', { items: tr(entityLabel) })}
      slotProps={{
        // On the search box itself: on TextField it would land on the outer
        // wrapper and leave the box unnamed for screen readers.
        htmlInput: { 'aria-label': tr('Search {items}', { items: tr(entityLabel) }) },
        input: {
          startAdornment: (
            <InputAdornment position="start">
              <Search size={16} />
            </InputAdornment>
          ),
          endAdornment: value ? (
            <InputAdornment position="end">
              <IconButton size="small" aria-label={tr('Clear search')} onClick={() => onChange('')}>
                <X size={16} />
              </IconButton>
            </InputAdornment>
          ) : null,
        },
      }}
    />
  );
}
