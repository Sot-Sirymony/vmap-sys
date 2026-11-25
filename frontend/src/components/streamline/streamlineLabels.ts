import { tr } from '../../i18n/translate';
import type { AutomationChoice, RepeatCadence, RequirementSource, SpeedLever, StageState, StreamlineVerdict } from '../../types/streamline';

// V27 (BRD 27): the words the Streamline review shows. Original wording (D-27-11:
// the method is credited in the BRD and the user guide, not named here).

export const STAGE_NAMES = (): string[] => [tr('Question'), tr('Delete'), tr('Simplify'), tr('Speed up'), tr('Automate')];

export const STAGE_INTROS = (): string[] => [
  tr('For every step and task: who asked for it, and is it really needed?'),
  tr('Cut whatever you can. Anything you delete can be brought back.'),
  tr('Make what is left simpler: merge, shrink the scope, remove hand-offs.'),
  tr('Find where work sits and waits, and choose how to finish sooner.'),
  tr('Last of all: let the app repeat routine tasks, or hand them to someone else.'),
];

/** BRD 27 §6.1: two prompts per stage. */
export const STAGE_PROMPTS = (): string[][] => [
  [tr("Who asked for this? If the answer is 'I'm not sure', start here."), tr('Would anyone notice if this step disappeared?')],
  [tr('Cut first, decide later. You can bring anything back.'), tr("If it's 'nice to have', it can go.")],
  [tr('Can two of these become one?'), tr('Does this need a hand-off, or can one person finish it?')],
  [tr('Where does work sit and wait?'), tr('What would make this finish a week sooner?')],
  [tr('You do this every week. Should the app create it for you?'), tr("Is this worth your time, or someone else's?")],
];

export const SOURCE_LABELS = (): Record<RequirementSource, string> => ({
  SELF: tr('Myself'),
  PARTNER: tr('A partner'),
  MENTOR: tr('A mentor'),
  COACH: tr('My coach'),
  ORGANISATION: tr('An organisation'),
  NOT_SURE: tr('Not sure'),
});

export const VERDICT_LABELS = (): Record<StreamlineVerdict, string> => ({
  NEEDED: tr('Needed'),
  NOT_SURE: tr('Not sure'),
  NOT_NEEDED: tr('Not needed'),
});

export const LEVER_LABELS = (): Record<SpeedLever, string> => ({
  REMOVE_WAIT: tr('Remove a wait'),
  UNBLOCK_FIRST: tr('Unblock first'),
  PARALLEL: tr('Do in parallel'),
  BATCH: tr('Batch'),
  SHORTEN_LOOP: tr('Shorten the loop'),
  CLOSER_DATE: tr('Set a closer date'),
});

export const LEVER_HINTS = (): Record<SpeedLever, string> => ({
  REMOVE_WAIT: tr('Ask the partner for a date, or follow up sooner.'),
  UNBLOCK_FIRST: tr('Make the blocker the next action.'),
  PARALLEL: tr('Start tasks that do not depend on each other at the same time.'),
  BATCH: tr('Group similar small tasks into one sitting.'),
  SHORTEN_LOOP: tr('Get feedback on a draft before the full piece.'),
  CLOSER_DATE: tr('Bring the due date forward.'),
});

export const AUTOMATION_LABELS = (): Record<AutomationChoice, string> => ({
  REPEAT: tr('Repeat'),
  REMIND: tr('Remind me'),
  DELEGATE: tr('Delegate or automate outside the app'),
  MANUAL: tr('Leave manual'),
});

export const CADENCE_LABELS = (): Record<RepeatCadence, string> => ({
  DAILY: tr('Every day'),
  WEEKLY: tr('Every week'),
  MONTHLY: tr('Every month'),
});

export const STATE_LABELS = (): Record<StageState, string> => ({
  PENDING: tr('Not done'),
  DONE: tr('Done'),
  NOT_NEEDED: tr('Not needed'),
});

export const CANDIDATE_REASONS = (): Record<string, string> => ({
  MANY_STEPS: tr('Many open steps'),
  OVERDUE: tr('Many overdue tasks'),
  MANY_TASKS: tr('Many open tasks'),
  REPEATED_WORK: tr('The same task done again and again'),
  CYCLE_TIME_DOUBLED: tr('Taking twice as long as before'),
});

/** V32 D-32-9: the weekdays a reminder can fall on, 1 = Monday. */
export const WEEKDAY_NAMES = (): string[] =>
  [tr('Monday'), tr('Tuesday'), tr('Wednesday'), tr('Thursday'), tr('Friday'), tr('Saturday'), tr('Sunday')];

/** V32 D-32-9: "Reminds you every Monday", "… every day", "… on day 5 of each month". */
export function remindSummary(cadence: RepeatCadence | null | undefined, day: number | null | undefined): string {
  if (cadence === 'DAILY') return tr('Reminds you every day');
  if (cadence === 'WEEKLY') return tr('Reminds you every {day}', { day: WEEKDAY_NAMES()[(day ?? 1) - 1] });
  if (cadence === 'MONTHLY') return tr('Reminds you on day {day} of each month', { day: day ?? 1 });
  return '';
}

/** Days to one decimal place, or a dash. */
export function days(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : tr('{days} days', { days: Number(value).toFixed(1) });
}

export function hours(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : tr('{hours} h', { hours: Number(value).toFixed(1).replace(/\.0$/, '') });
}
