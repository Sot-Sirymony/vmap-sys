import { describe, expect, it } from 'vitest';
import { safeHttpUrl } from './safeUrl';

describe('safeHttpUrl (SEC-01-10)', () => {
  it('keeps http and https addresses', () => {
    expect(safeHttpUrl('https://example.org/guide')).toBe('https://example.org/guide');
    expect(safeHttpUrl('  http://example.org  ')).toBe('http://example.org/');
    expect(safeHttpUrl('HTTPS://Example.org/A')).toBe('https://example.org/A');
  });

  it('refuses scripts, data and other schemes', () => {
    expect(safeHttpUrl('javascript:alert(1)')).toBeUndefined();
    expect(safeHttpUrl(' JavaScript:alert(1)')).toBeUndefined();
    expect(safeHttpUrl('java\tscript:alert(1)')).toBeUndefined();
    expect(safeHttpUrl('data:text/html,<script>alert(1)</script>')).toBeUndefined();
    expect(safeHttpUrl('vbscript:msgbox(1)')).toBeUndefined();
    expect(safeHttpUrl('file:///etc/passwd')).toBeUndefined();
  });

  it('refuses empty and relative values', () => {
    expect(safeHttpUrl(undefined)).toBeUndefined();
    expect(safeHttpUrl(null)).toBeUndefined();
    expect(safeHttpUrl('')).toBeUndefined();
    expect(safeHttpUrl('/dreams/1')).toBeUndefined();
    expect(safeHttpUrl('example.org')).toBeUndefined();
  });
});
