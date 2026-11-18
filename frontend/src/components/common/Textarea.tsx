import type { ChangeEventHandler, FocusEventHandler } from 'react';
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
  onBlur?: FocusEventHandler<HTMLTextAreaElement | HTMLInputElement>;
  /** The most characters the box takes (V19 RM-FR-26). */
  maxLength?: number;
  /** Visible lines before it grows; 3 by default. */
  rows?: number;
};

export function Textarea({ 'aria-label': ariaLabel, maxLength, rows = 3, ...props }: TextareaProps) {
  // The label goes on the <textarea> itself, as in Input. Spread onto
  // TextField it would land on the outer wrapper and leave the box unnamed.
  const htmlInput = {
    ...(ariaLabel ? { 'aria-label': ariaLabel } : {}),
    ...(props.autoFocus ? { 'data-autofocus': true } : {}),
    ...(maxLength ? { maxLength } : {}),
  };
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      multiline
      minRows={rows}
      slotProps={Object.keys(htmlInput).length > 0 ? { htmlInput } : undefined}
      {...props}
    />
  );
}
