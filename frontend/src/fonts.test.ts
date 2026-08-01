import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fontFamilies, fontStack } from './theme';

const imported: string[] = [];

// Stand in for the real @fontsource packages: importing them in jsdom would try
// to parse CSS and fetch woff2 files. What matters here is *which* imports fire
// and how many times, not what the CSS contains.
vi.mock('@fontsource-variable/public-sans', () => { imported.push('publicSans'); return {}; });
vi.mock('@fontsource-variable/inter', () => { imported.push('inter'); return {}; });
vi.mock('@fontsource-variable/dm-sans', () => { imported.push('dmSans'); return {}; });
vi.mock('@fontsource-variable/nunito-sans', () => { imported.push('nunitoSans'); return {}; });

const { loadFont } = await import('./fonts');

describe('font loading (FR-42.2)', () => {
  beforeEach(() => {
    imported.length = 0;
  });

  /**
   * The point of the whole self-hosted, lazy arrangement: a user on the default
   * downloads no font at all. If this ever regresses, everyone starts paying for
   * typefaces most of them never pick.
   */
  it('fetches nothing for the system default', async () => {
    await loadFont('system');
    expect(imported).toHaveLength(0);
  });

  it('fetches a font only once, however many times it is selected', async () => {
    await loadFont('inter');
    await loadFont('inter');
    await loadFont('inter');

    expect(imported.filter((id) => id === 'inter')).toHaveLength(1);
  });

  it('fetches only the font that was chosen', async () => {
    await loadFont('dmSans');

    expect(imported).toEqual(['dmSans']);
  });

  /**
   * A font that fails to arrive must not surface as an error. Every stack ends
   * in the system fallback, so the page simply renders in the default face —
   * an appearance preference is not worth breaking a screen over.
   */
  it('resolves rather than rejecting when a font fails to load', async () => {
    vi.doMock('@fontsource-variable/nunito-sans', () => {
      throw new Error('network down');
    });

    await expect(loadFont('nunitoSans')).resolves.toBeUndefined();
  });
});

describe('font stacks (FR-42)', () => {
  it('offers the system default plus the four named faces', () => {
    expect(fontFamilies.map((font) => font.id)).toEqual([
      'system',
      'publicSans',
      'inter',
      'dmSans',
      'nunitoSans',
    ]);
  });

  /**
   * Every stack has to end in the system chain. That is what makes a failed or
   * blocked web font degrade to the app's normal face instead of to whatever
   * serif the browser defaults to.
   */
  it.each(fontFamilies.map((font) => font.id))('%s falls back to the system stack', (id) => {
    const stack = fontStack(id);
    expect(stack).toContain('sans-serif');
    expect(stack).toContain('-apple-system');
  });

  it('names the variable face first for each web font', () => {
    expect(fontStack('inter')).toMatch(/^"Inter Variable"/);
    expect(fontStack('publicSans')).toMatch(/^"Public Sans Variable"/);
    expect(fontStack('dmSans')).toMatch(/^"DM Sans Variable"/);
    expect(fontStack('nunitoSans')).toMatch(/^"Nunito Sans Variable"/);
  });

  it('falls back to the system stack for an unknown id', () => {
    expect(fontStack('comicSans' as never)).toBe(fontStack('system'));
  });
});
