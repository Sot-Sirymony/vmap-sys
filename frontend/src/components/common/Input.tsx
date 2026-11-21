import type { InputHTMLAttributes, Ref } from 'react';
import TextField from '@mui/material/TextField';

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'color'> & {
  /** Ref to the underlying input element (e.g. for programmatic focus). */
  inputRef?: Ref<HTMLInputElement>;
};

export function Input({ min, max, minLength, maxLength, step, pattern, inputMode, 'aria-label': ariaLabel, ...props }: InputProps) {
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      // data-autofocus lets the Modal re-focus the intended first field after
      // MUI's focus trap has claimed focus for the dialog (FR-22.3);
      // aria-label must land on the native input, not TextField's root div.
      slotProps={{
        htmlInput: {
          min, max, minLength, maxLength, step, pattern, inputMode,
          ...(ariaLabel ? { 'aria-label': ariaLabel } : {}),
          ...(props.autoFocus ? { 'data-autofocus': true } : {}),
        },
      }}
      {...props}
    />
  );
}
