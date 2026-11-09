import { tr } from '../../i18n/translate';
import type { AiFocus, AiStatus, AssistantPage } from '../../types/vision';
import { helpFor } from './page-assistant/pageHelp';

/** V15 (AH-FR-01, AH-FR-11): the pages that have the page assistant instead of the Dream Coach. */
export function assistantPageFor(pathname: string): AssistantPage | null {
  if (pathname === '/modules') {
    return 'MODULES';
  }
  if (/^\/modules\/\d+/.test(pathname)) {
    return 'MODULE';
  }
  if (pathname.startsWith('/assessment')) {
    return 'ASSESSMENT';
  }
  if (pathname.startsWith('/obstacles')) {
    return 'OBSTACLES';
  }
  if (pathname.startsWith('/communication')) {
    return 'COMMUNICATION';
  }
  if (pathname.startsWith('/work-style')) {
    return 'WORK_STYLE';
  }
  return null;
}

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
  // V15 (AH-FR-12): the five assistant pages use their own quick actions, never the generic or shared ones.
  const assistantPage = assistantPageFor(pathname);
  if (assistantPage) {
    return helpFor(assistantPage, 'page').quickActions.map((action) => tr(action));
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
  if (pathname.startsWith('/journal')) {
    return [tr('Help me face this fear'), tr('How should I answer this criticism?'), tr('Do you see a pattern in my mood?')];
  }
  if (pathname.startsWith('/partners')) {
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
  section: 'this section of your plan',
  // V15 (AH-FR-08): what the page assistant used.
  page: 'this page',
  modules: 'your modules',
  assessment: 'your assessment scores',
  obstacle: 'this obstacle',
  message: 'this message',
  earlierMessages: 'your earlier messages to this partner',
  linked: 'the linked plan items',
  workStyle: 'your work style',
  form: 'what you have typed',
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

/** V15 (AH-FR-57): AI help is on for this page: available for the person, and not switched off for the page or their cohort. */
export function aiOnFor(status: AiStatus | null | undefined, page: AssistantPage): boolean {
  return Boolean(status?.available) && !(status?.pagesOff ?? []).includes(page);
}
