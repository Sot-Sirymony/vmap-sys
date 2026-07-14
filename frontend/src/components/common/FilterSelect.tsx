import FormControl from '@mui/material/FormControl';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';

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

// One dropdown in a filter bar. An empty value always means "no filter", which
// is what lets every page reset a filter the same way.
export function FilterSelect({ label, value, onChange, options, allLabel = 'All' }: FilterSelectProps) {
  return (
    <label>
      {label}
      <FormControl fullWidth size="small">
        <Select displayEmpty value={value} onChange={(event) => onChange(event.target.value)}>
          <MenuItem value="">{allLabel}</MenuItem>
          {options.map((option) => (
            <MenuItem value={option.value} key={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
    </label>
  );
}
