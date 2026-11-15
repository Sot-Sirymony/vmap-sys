import type { ChainScoreKey } from '../types/vision';

/**
 * V15 (AH-FR-23): what each assessment statement means, with two short
 * examples about made-up people, one of a low and one of a high score. Program
 * content, written once in English and Khmer; needs no AI and costs nothing.
 */
export const STATEMENT_GUIDE: Record<ChainScoreKey, { meaning: string; low: string; high: string }> = {
  settlingScore: {
    meaning: 'Whether you aim higher than what feels safe and easy, or stop once things are good enough.',
    low: 'Low: Dara has wanted to start her own clinic for years, but her job is comfortable, so she never looks into it.',
    high: 'High: Vanna is happy at work, yet sets one goal each year that stretches her, such as leading a new project.',
  },
  fearScore: {
    meaning: 'Whether you still act when something might fail, or wait until you are sure.',
    low: 'Low: Sokha has a business idea but will not test it until she is certain it will work.',
    high: 'High: Rith sells ten samples at the market to see what happens, knowing it may not go well.',
  },
  criticismScore: {
    meaning: 'Whether what others might say stops you from acting, or you listen and go ahead anyway.',
    low: 'Low: Mony drops a plan to go back to school after a relative laughs at the idea.',
    high: 'High: Pheap hears the doubts, keeps the useful points, and enrols anyway.',
  },
  visionScore: {
    meaning: 'Whether you can say exactly what you want and by when, or only have a general wish.',
    low: 'Low: Chenda wants "a better life" but cannot say what that would look like.',
    high: 'High: Bopha can say she wants to open a tailoring shop in her town by June next year.',
  },
  // V17 BL-FR-30. Not knowing how and lack of resources are step gaps now (BL-FR-32).
  prioritiesScore: {
    meaning: 'Whether your days go to the few things that move your dream, or to whatever is most urgent or noisy.',
    low: 'Low: Kosal means to work on his farm plan, but every evening goes to messages and small favours.',
    high: 'High: Sophal does one task for her plan each morning before she answers anyone else.',
  },
  persistenceScore: {
    meaning: 'Whether you keep going when progress is slow or a step fails, or drop the plan and start something else.',
    low: 'Low: Nary stops her English lessons after two weeks because she does not feel any better yet.',
    high: 'High: Thida misses a week of saving, then starts again the next week instead of giving up.',
  },
};

/** The page and the habit for each barrier's practice: the same app actions the AI practice must use (AH-FR-26). */
export const PRACTICE_ACTIONS: Record<ChainScoreKey, { path: string; page: string }> = {
  settlingScore: { path: '/dreams', page: 'Dreams' },
  fearScore: { path: '/journal', page: 'Journal' },
  criticismScore: { path: '/obstacles', page: 'Obstacles' },
  visionScore: { path: '/dreams', page: 'Dreams' },
  prioritiesScore: { path: '/today', page: 'Today' },
  persistenceScore: { path: '/today', page: 'Today' },
};

/** V15 (AH-FR-27): the program's own practice for each barrier, used without AI. */
export const PRACTICE_PROGRAM: Record<ChainScoreKey, { practice: string; habit: string }> = {
  settlingScore: { practice: 'This week, write a bolder version of one dream on the Dreams page.', habit: 'Stretch one dream' },
  fearScore: { practice: 'This week, log one fear in your Journal and plan the smallest safe test of it.', habit: 'Test one fear' },
  criticismScore: { practice: 'This week, take one criticism you heard and write down the useful point in it, on the Obstacles page.', habit: 'Find the useful point' },
  visionScore: { practice: 'This week, rewrite your top dream\'s success definition so you would know when it is reached.', habit: 'Picture the finish' },
  // V17 BL-FR-35: priorities use the letter rank on the Today page; persistence uses one small daily habit.
  prioritiesScore: { practice: 'Each day this week, give one task the letter rank A on the Today page and do it first.', habit: 'Do my A task first' },
  persistenceScore: { practice: 'This week, add one small daily habit on the Today page that moves your plan forward, and keep it going.', habit: 'Show up every day' },
};
