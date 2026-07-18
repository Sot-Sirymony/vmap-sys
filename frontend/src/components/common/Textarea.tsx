import type { ChangeEventHandler } from 'react';
import TextField from '@mui/material/TextField';

type TextareaProps = {
  value?: string;
  onChange?: ChangeEventHandler<HTMLTextAreaElement>;
  required?: boolean;
  placeholder?: string;
  disabled?: boolean;
  name?: string;
  autoFocus?: boolean;
};

export function Textarea(props: TextareaProps) {
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      multiline
      minRows={3}
      slotProps={props.autoFocus ? { htmlInput: { 'data-autofocus': true } } : undefined}
      {...props}
    />
  );
}
