import type { ButtonHTMLAttributes, ReactNode } from 'react';
import MuiButton from '@mui/material/Button';

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'text';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  /** Small icon shown to the left of the label (the "left icon" button). */
  startIcon?: ReactNode;
  children: ReactNode;
};

// Button design system (docs/Optimize Appearance/button.md):
//   primary   → FILLED  (accent fill, white text)
//   secondary → OUTLINE (white fill, thin grey border, dark text)
//   danger    → FILLED in the error colour
//   text      → TEXT    (no fill, accent text, tint on hover)
// The theme (MuiButton in theme.ts) draws the hover, pressed and disabled states.
const VARIANT_MAP: Record<ButtonVariant, { variant: 'contained' | 'outlined' | 'text'; color: 'primary' | 'secondary' | 'error' }> = {
  primary: { variant: 'contained', color: 'primary' },
  secondary: { variant: 'outlined', color: 'secondary' },
  danger: { variant: 'contained', color: 'error' },
  text: { variant: 'text', color: 'primary' },
};

export function Button({ variant = 'primary', children, type = 'button', color: _nativeColor, ...props }: ButtonProps) {
  const { variant: muiVariant, color } = VARIANT_MAP[variant];
  return (
    <MuiButton variant={muiVariant} color={color} type={type} size="small" {...props}>
      {children}
    </MuiButton>
  );
}
