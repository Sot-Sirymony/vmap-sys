import { tr } from '../../i18n/translate';
import type { AiFocus } from '../../types/vision';

/** AI-FR-01: what is on screen, read from the page address. */
export function focusFor(pathname: string): AiFocus {
  const dream = /^\/dreams\/(\d+)/.exec(pathname);
  const module = /^\/modules\/(\d+)/.exec(pathname);
  return {
    page: pathname,
    dreamId: dream ? Number(dream[1]) : null,
    moduleNumber: module ? Number(module[1]) : null,
  };
}

/** AI-FR-02: three ready-made questions for each kind of page, so nobody has to start from a blank box. */
export function quickActionsFor(pathname: string): string[] {
  if (pathname.startsWith('/modules/')) {
    return [tr('Help me start this exercise'), tr('Improve my answer'), tr('What comes next?')];
  }
  if (pathname.startsWith('/today') || pathname === '/') {
    return [tr('What should I do first today?'), tr('I only have 30 minutes. What fits?'), tr('Help me get unstuck')];
  }
  if (pathname.startsWith('/dreams') || pathname.startsWith('/vision')) {
    return [tr('Help me make this dream clearer'), tr('Suggest goals for this dream'), tr('What could block this dream?')];
  }
  if (pathname.startsWith('/goals')) {
    return [tr('Is my goal specific enough?'), tr('What steps does this goal need?'), tr('Which goal should come first?')];
  }
  if (pathname.startsWith('/steps') || pathname.startsWith('/tasks') || pathname.startsWith('/plan')) {
    return [tr('Check my plan for gaps'), tr('Break a big step into smaller ones'), tr('What is most overdue?')];
  }
  if (pathname.startsWith('/journal') || pathname.startsWith('/obstacles')) {
    return [tr('Help me face this fear'), tr('How should I answer this criticism?'), tr('Do you see a pattern in my mood?')];
  }
  if (pathname.startsWith('/partners') || pathname.startsWith('/communication')) {
    return [tr('Who could help with my plan?'), tr('Draft a short ask to a mentor'), tr('How do I follow up politely?')];
  }
  if (pathname.startsWith('/reviews')) {
    return [tr('Help me review my week'), tr('What went well and what slipped?'), tr('What should my top 3 be next week?')];
  }
  return [tr('Help me start'), tr('What should I do next?'), tr('Explain this page')];
}

const SOURCE_LABELS: Record<string, string> = {
  plan: 'your plan',
  dream: 'this dream',
  tasks: 'your tasks',
  attitude: 'your attitude check-ins',
  fears: 'your fear log',
  obstacles: 'your obstacles',
  module: 'your module',
};

/** AI-FR-05: the short reason under a reply. Null when it used none of the participant's records. */
export function basedOn(sources: string[] | undefined): string | null {
  const labels = (sources ?? []).map((source) => SOURCE_LABELS[source]).filter(Boolean).map((label) => tr(label));
  if (labels.length === 0) {
    return null;
  }
  const list = labels.length === 1 ? labels[0] : `${labels.slice(0, -1).join(', ')} ${tr('and')} ${labels[labels.length - 1]}`;
  return tr('Based on {sources}', { sources: list });
}
