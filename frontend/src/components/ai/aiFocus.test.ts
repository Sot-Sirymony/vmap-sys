import { describe, expect, it } from 'vitest';
import { assistantPageFor, basedOn, focusFor, quickActionsFor } from './aiFocus';

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
    expect(quickActionsFor('/journal')).toContain('Help me face this fear');
  });

  it('gives the five page assistant pages their own questions (V15, AH-FR-12)', () => {
    expect(quickActionsFor('/modules')).toEqual(['Which module should I do next?', 'What is this program about?', 'How far have I come?']);
    expect(quickActionsFor('/modules/2')).toEqual(['Explain this module', 'Help me start', 'Check my answer']);
    expect(quickActionsFor('/assessment')[0]).toBe('What does this statement mean?');
    expect(quickActionsFor('/work-style')).not.toContain('Help me start');
    // The Obstacles page no longer shares the Journal's fear questions, nor Communication the Partners ones.
    expect(quickActionsFor('/obstacles')).not.toContain('Help me face this fear');
    expect(quickActionsFor('/communication')).not.toContain('Who could help with my plan?');
    expect(assistantPageFor('/modules/7')).toBe('MODULE');
    expect(assistantPageFor('/partners')).toBeNull();
  });

  it('turns the sources a reply used into a short reason', () => {
    expect(basedOn(['dream'])).toBe('Based on this dream');
    expect(basedOn(['plan', 'tasks', 'fears'])).toBe('Based on your plan, your tasks and your fear log');
    expect(basedOn(['page', 'obstacle'])).toBe('Based on this page and this obstacle');
    expect(basedOn([])).toBeNull();
    expect(basedOn(undefined)).toBeNull();
  });
});
