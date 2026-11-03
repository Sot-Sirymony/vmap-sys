import { describe, expect, it } from 'vitest';
import { basedOn, focusFor, quickActionsFor } from './aiFocus';

describe('Dream Coach focus (AI-FR-01, AI-FR-02, AI-FR-05)', () => {
  it('reads the dream or module on screen from the address', () => {
    expect(focusFor('/dreams/12')).toEqual({ page: '/dreams/12', dreamId: 12, moduleNumber: null });
    expect(focusFor('/modules/4')).toEqual({ page: '/modules/4', dreamId: null, moduleNumber: 4 });
    expect(focusFor('/today')).toEqual({ page: '/today', dreamId: null, moduleNumber: null });
  });

  it('offers exactly three questions that fit each page', () => {
    for (const page of ['/', '/today', '/dreams/3', '/goals', '/tasks', '/modules/2', '/journal', '/partners', '/reviews', '/settings/account']) {
      expect(quickActionsFor(page)).toHaveLength(3);
    }
    expect(quickActionsFor('/modules/2')[0]).toBe('Help me start this exercise');
    expect(quickActionsFor('/journal')).toContain('Help me face this fear');
  });

  it('turns the sources a reply used into a short reason', () => {
    expect(basedOn(['dream'])).toBe('Based on this dream');
    expect(basedOn(['plan', 'tasks', 'fears'])).toBe('Based on your plan, your tasks and your fear log');
    expect(basedOn([])).toBeNull();
    expect(basedOn(undefined)).toBeNull();
  });
});
