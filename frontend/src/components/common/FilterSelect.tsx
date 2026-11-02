import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { tr } from '../../i18n/translate';

export type FilterOption = { value: string; label: string };

// Turns an enumLabels map into filter options, so a dropdown can never drift
// out of sync with the enum it filters on.
export function optionsFromLabels<T extends string>(labels: Record<T, string>): FilterOption[] {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }));
}

// Turns a list of records into filter options keyed by id.
export function optionsFromEntities<T extends { id: number }>(
  entities: T[],
  labelFor: (entity: T) => string,
): FilterOption[] {
  return entities.map((entity) => ({ value: String(entity.id), label: labelFor(entity) }));
}

type FilterSelectProps = {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: FilterOption[];
  allLabel?: string;
};

// One dropdown in a filter bar, always type-to-search. An empty value means "no
// filter", which is what lets every page reset a filter the same way — and here
// it's produced by clearing the box (the X or Escape). Every filter behaves the
// same whether it has 3 options or 300.
export function FilterSelect({ label, value, onChange, options, allLabel = tr('All') }: FilterSelectProps) {
  const selected = options.find((option) => option.value === value) ?? null;

  return (
    <label>
      {label}
      <Autocomplete
        size="small"
        options={options}
        value={selected}
        onChange={(_event, option) => onChange(option?.value ?? '')}
        getOptionLabel={(option) => option.label}
        isOptionEqualToValue={(option, current) => option.value === current.value}
        renderInput={(params) => (
          <TextField
            {...params}
            placeholder={tr(allLabel)}
            slotProps={{ ...params.slotProps, htmlInput: { ...params.slotProps.htmlInput, 'aria-label': label } }}
          />
        )}
        clearOnEscape
      />
    </label>
  );
}
