import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';

// Signed out: this file is about how slices are built and coloured, not data.
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ token: null, appearance: null }),
}));

const { ThemeModeProvider } = await import('../../context/ThemeModeContext');
const { CategoryBreakdownChart } = await import('./CategoryBreakdownChart');
const { CATEGORICAL_PIE_SLOTS } = await import('../../theme');

/**
 * Recharts needs a laid-out container, which jsdom will not give it, so the arcs
 * themselves never render here. The legend does — it is plain MUI outside the
 * ResponsiveContainer — and it carries exactly what this suite is about: one
 * entry per slice, each with the swatch colour that slice is drawn in.
 */
function renderPie(data: Record<string, number>, otherLabel?: string) {
  render(
    <MemoryRouter>
      <ThemeModeProvider>
        <CategoryBreakdownChart
          title="Dreams by vision area"
          description="Where active dreams are concentrated"
          data={data}
          variant="pie"
          otherLabel={otherLabel}
        />
      </ThemeModeProvider>
    </MemoryRouter>,
  );
}

/** The legend dot beside a category name, which is painted with the slice fill. */
function swatchColorFor(category: string): string {
  const label = screen.getByText(category);
  const row = label.parentElement as HTMLElement;
  const dot = row.firstElementChild as HTMLElement;
  return getComputedStyle(dot).backgroundColor;
}

const AREAS = ['Career', 'Health', 'Family', 'Finance', 'Education', 'Business'];

describe('CategoryBreakdownChart — pie slices (FR-41)', () => {
  it('shows every category when they fit the palette', () => {
    renderPie({ Career: 5, Health: 4, Family: 3, Finance: 2 });

    for (const area of ['Career', 'Health', 'Family', 'Finance']) {
      expect(screen.getByText(area)).toBeInTheDocument();
    }
    expect(screen.queryByText('Other areas')).not.toBeInTheDocument();
  });

  /**
   * The regression. The palette held four colours and used to wrap, so a fifth
   * vision area was painted in the first one's colour — and since a pie's last
   * slice touches its first, those two identical fills sat next to each other
   * with no stroke between them and read as a single wedge.
   */
  it('never paints two slices the same colour, however many categories arrive', () => {
    renderPie(Object.fromEntries(AREAS.map((area, index) => [area, AREAS.length - index])), 'Other areas');

    const rendered = [...AREAS, 'Other areas'].filter((name) => screen.queryByText(name) !== null);
    const colors = rendered.map(swatchColorFor);

    expect(new Set(colors).size).toBe(colors.length);
  });

  it('rolls the smallest categories into one Other slice past the palette', () => {
    renderPie(Object.fromEntries(AREAS.map((area, index) => [area, AREAS.length - index])), 'Other areas');

    // The biggest keep their own identity; the tail collapses into one slice, so
    // the pie never asks the palette for a colour it does not have.
    expect(screen.getByText('Career')).toBeInTheDocument();
    expect(screen.getByText('Other areas')).toBeInTheDocument();
    expect(screen.queryByText('Business')).not.toBeInTheDocument();
    expect(screen.getAllByText(/./, { selector: '.MuiTypography-body2' })).toHaveLength(CATEGORICAL_PIE_SLOTS);
  });

  it('ranks by size, so the Other slice is made of the smallest categories', () => {
    renderPie({ Tiny: 1, Huge: 50, Small: 2, Big: 30, Medium: 10 }, 'Other areas');

    expect(screen.getByText('Huge')).toBeInTheDocument();
    expect(screen.getByText('Big')).toBeInTheDocument();
    expect(screen.getByText('Medium')).toBeInTheDocument();
    expect(screen.queryByText('Tiny')).not.toBeInTheDocument();
    expect(screen.queryByText('Small')).not.toBeInTheDocument();
  });

  /**
   * Colour identifies the category, not its rank. The dashboard filters by
   * vision area and by period, so counts move constantly; if colour tracked the
   * ranking, changing the period would repaint every slice and the reader would
   * have to re-learn the legend each time.
   */
  it('keeps a category on its own colour when the counts reshuffle', () => {
    const { unmount } = render(
      <MemoryRouter>
        <ThemeModeProvider>
          <CategoryBreakdownChart title="t" description="d" data={{ Career: 9, Health: 2, Family: 1 }} variant="pie" />
        </ThemeModeProvider>
      </MemoryRouter>,
    );
    const before = ['Career', 'Health', 'Family'].map(swatchColorFor);
    unmount();

    // Same categories, order of magnitude reversed.
    render(
      <MemoryRouter>
        <ThemeModeProvider>
          <CategoryBreakdownChart title="t" description="d" data={{ Career: 1, Health: 2, Family: 9 }} variant="pie" />
        </ThemeModeProvider>
      </MemoryRouter>,
    );
    const after = ['Career', 'Health', 'Family'].map(swatchColorFor);

    expect(after).toEqual(before);
  });
});
