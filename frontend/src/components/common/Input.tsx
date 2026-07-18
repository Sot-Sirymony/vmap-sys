import type { InputHTMLAttributes } from 'react';
import TextField from '@mui/material/TextField';

type InputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'color'>;

export function Input({ min, max, minLength, maxLength, step, pattern, ...props }: InputProps) {
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      // data-autofocus lets the Modal re-focus the intended first field after
      // MUI's focus trap has claimed focus for the dialog (FR-22.3).
      slotProps={{ htmlInput: { min, max, minLength, maxLength, step, pattern, ...(props.autoFocus ? { 'data-autofocus': true } : {}) } }}
      {...props}
    />
  );
}
