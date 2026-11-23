import { FACTOR_GROUP, MFA_ID, PASSWORD_BASED_ID, SECURITY_CATALOGUE, type SecurityItem } from './securityCatalogue';

/**
 * V24.0.0.0 (BRD section 7): the business rules of the Security access
 * prototype, as pure functions so they are easy to test. State maps an item ID
 * to whether it is allowed.
 */
export type SecurityState = Record<string, boolean>;

export type ChangeResult = {
  state: SecurityState;
  /** Set when a rule stopped or changed part of the request (BR-02, BR-03); English text for tr(). */
  notice?: { text: string; values?: Record<string, string | number> };
};

const byId = new Map(SECURITY_CATALOGUE.map((item) => [item.id, item]));

export function defaultState(catalogue: SecurityItem[] = SECURITY_CATALOGUE): SecurityState {
  return Object.fromEntries(catalogue.map((item) => [item.id, item.defaultAllowed]));
}

function allowedAuthn(state: SecurityState): string[] {
  return SECURITY_CATALOGUE.filter((item) => item.family === 'AUTHN' && state[item.id]).map((item) => item.id);
}

/** FR-04 with BR-02: blocking the last allowed Authentication item is refused. */
export function setOne(state: SecurityState, id: string, allowed: boolean): ChangeResult {
  const item = byId.get(id);
  if (!allowed && item?.family === 'AUTHN' && state[id] && allowedAuthn(state).length === 1) {
    return { state, notice: { text: 'At least one Authentication method must stay allowed.' } };
  }
  return { state: { ...state, [id]: allowed } };
}

/**
 * FR-05, FR-06 with BR-02 and BR-03: allowing in bulk skips Deprecated items;
 * blocking in bulk keeps one Authentication item allowed (Password-based when
 * it is among the items, otherwise the first allowed one).
 */
export function setMany(state: SecurityState, ids: string[], allowed: boolean): ChangeResult {
  const next = { ...state };
  if (allowed) {
    const skipped = ids.filter((id) => byId.get(id)?.tag === 'Deprecated' && !state[id]);
    ids.filter((id) => !skipped.includes(id)).forEach((id) => { next[id] = true; });
    return skipped.length > 0
      ? { state: next, notice: { text: '{count} deprecated item(s) were left blocked. Allow them one at a time if you really need them.', values: { count: skipped.length } } }
      : { state: next };
  }
  ids.forEach((id) => { next[id] = false; });
  if (allowedAuthn(next).length > 0) return { state: next };
  const keep = ids.includes(PASSWORD_BASED_ID) ? PASSWORD_BASED_ID : ids.find((id) => byId.get(id)?.family === 'AUTHN' && state[id]);
  if (!keep) return { state: next };
  next[keep] = true;
  return { state: next, notice: { text: '{name} stays allowed: at least one Authentication method must stay allowed.', values: { name: byId.get(keep)?.name ?? keep } } };
}

/** BR-03: allowing a Deprecated item needs a confirmation. */
export function needsDeprecatedConfirm(id: string, allowed: boolean): boolean {
  return allowed && byId.get(id)?.tag === 'Deprecated';
}

/** BR-04: MFA allowed with fewer than two "By factor" items allowed. */
export function mfaWarning(state: SecurityState): boolean {
  const factors = SECURITY_CATALOGUE.filter((item) => item.group === FACTOR_GROUP && state[item.id]).length;
  return Boolean(state[MFA_ID]) && factors < 2;
}

/** FR-11, FR-13: the items whose draft differs from what was saved. */
export function changedItems(saved: SecurityState, draft: SecurityState): { allowed: SecurityItem[]; blocked: SecurityItem[] } {
  const changed = SECURITY_CATALOGUE.filter((item) => saved[item.id] !== draft[item.id]);
  return {
    allowed: changed.filter((item) => draft[item.id]),
    blocked: changed.filter((item) => !draft[item.id]),
  };
}

export function countAllowed(state: SecurityState, items: SecurityItem[]): number {
  return items.filter((item) => state[item.id]).length;
}
