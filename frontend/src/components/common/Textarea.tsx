import type { ChangeEventHandler } from 'react';
import TextField from '@mui/material/TextField';

type TextareaProps = {
  value?: string;
  onChange?: ChangeEventHandler<HTMLTextAreaElement>;
  required?: boolean;
  placeholder?: string;
  disabled?: boolean;
  name?: string;
  /** Lets a <label htmlFor> name the box when the label also holds a button (V15 Help with this). */
  id?: string;
  autoFocus?: boolean;
  /** Names the text box for screen readers when no visible label wraps it. */
  'aria-label'?: string;
};

export function Textarea({ 'aria-label': ariaLabel, ...props }: TextareaProps) {
  // The label goes on the <textarea> itself, as in Input. Spread onto
  // TextField it would land on the outer wrapper and leave the box unnamed.
  const htmlInput = {
    ...(ariaLabel ? { 'aria-label': ariaLabel } : {}),
    ...(props.autoFocus ? { 'data-autofocus': true } : {}),
  };
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      multiline
      minRows={3}
      slotProps={Object.keys(htmlInput).length > 0 ? { htmlInput } : undefined}
      {...props}
    />
  );
}
