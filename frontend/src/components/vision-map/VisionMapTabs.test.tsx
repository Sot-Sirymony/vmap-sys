import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { VisionMapTabs } from './VisionMapTabs';

function tabsAt(path: string) {
  render(<MemoryRouter initialEntries={[path]}><VisionMapTabs /></MemoryRouter>);
  return within(screen.getByRole('navigation', { name: 'Vision Map' }));
}

describe('VisionMapTabs (V37 D-37-3)', () => {
  it('links each tab to its own page', () => {
    const tabs = tabsAt('/vision-map');
    expect(tabs.getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Map', '/vision-map'],
      ['Timeline', '/vision-map/timeline'],
      ['Management Report', '/vision-map/report'],
      ['Saved reports', '/vision-map/reports'],
    ]);
    expect(tabs.getByRole('link', { name: 'Map' })).toHaveAttribute('aria-current', 'page');
  });

  it.each([
    ['/vision-map/timeline', 'Timeline'],
    ['/vision-map/report', 'Management Report'],
    ['/vision-map/reports', 'Saved reports'],
    ['/vision-map/reports/12', 'Saved reports'],
    ['/dreams/4', 'Map'],
  ])('marks the right tab on %s', (path, name) => {
    const tabs = tabsAt(path);
    expect(tabs.getByRole('link', { name })).toHaveAttribute('aria-current', 'page');
    expect(tabs.getAllByRole('link').filter((link) => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
  });
});
