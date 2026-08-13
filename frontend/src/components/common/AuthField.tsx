import { useState, type ReactNode } from 'react';
import { Eye, EyeOff } from 'lucide-react';

type AuthFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  type?: 'text' | 'email' | 'password';
  name?: string;
  autoComplete?: string;
  placeholder?: string;
  required?: boolean;
  minLength?: number;
  /** Leading icon inside the field (Stitch puts mail/lock glyphs there). */
  icon?: ReactNode;
  /** Adds the eye button that flips the password between hidden and shown. */
  revealable?: boolean;
  /** Rendered at the right end of the label row (e.g. "Forgot password?"). */
  labelEnd?: ReactNode;
};

/**
 * A labelled input in the Stitch auth style: caps label, optional leading
 * icon, optional reveal toggle. Native input rather than the MUI TextField
 * the in-app forms use, because the comp's field anatomy (inset icon, inline
 * toggle, pill-adjacent radii) is this exact drawing.
 */
export function AuthField({
  id,
  label,
  value,
  onChange,
  type = 'text',
  name,
  autoComplete,
  placeholder,
  required,
  minLength,
  icon,
  revealable,
  labelEnd,
}: AuthFieldProps) {
  const [revealed, setRevealed] = useState(false);
  const inputType = revealable ? (revealed ? 'text' : 'password') : type;
  const classes = [
    'auth-input',
    icon ? 'auth-input--icon' : '',
    revealable ? 'auth-input--toggle' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className="auth-field">
      {labelEnd ? (
        <div className="auth-label-row">
          <label className="auth-label" htmlFor={id}>
            {label}
          </label>
          {labelEnd}
        </div>
      ) : (
        <label className="auth-label" htmlFor={id}>
          {label}
        </label>
      )}
      <div className="auth-input-wrap">
        {icon && (
          <span className="auth-input-icon" aria-hidden="true">
            {icon}
          </span>
        )}
        <input
          id={id}
          className={classes}
          type={inputType}
          name={name}
          autoComplete={autoComplete}
          placeholder={placeholder}
          required={required}
          minLength={minLength}
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
        {revealable && (
          <button
            type="button"
            className="auth-toggle"
            aria-label={revealed ? 'Hide password' : 'Show password'}
            onClick={() => setRevealed((current) => !current)}
          >
            {revealed ? <EyeOff size={18} /> : <Eye size={18} />}
          </button>
        )}
      </div>
    </div>
  );
}
