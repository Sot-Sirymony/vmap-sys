import { describe, expect, it } from 'vitest';
import { SECURITY_CATALOGUE, SECURITY_GROUPS } from './securityCatalogue';
import { changedItems, defaultState, mfaWarning, needsDeprecatedConfirm, setMany, setOne } from './securityRules';

const ALL = SECURITY_CATALOGUE.map((item) => item.id);
const AUTHN = SECURITY_CATALOGUE.filter((item) => item.family === 'AUTHN').map((item) => item.id);
const onlyAllowed = (...ids: string[]) => Object.fromEntries(ALL.map((id) => [id, ids.includes(id)]));

describe('security catalogue (BRD V24.0.0.0 section 5)', () => {
  it('holds 57 items in 7 groups: 33 Authentication and 24 Authorization, with unique IDs', () => {
    expect(SECURITY_CATALOGUE).toHaveLength(57);
    expect(SECURITY_GROUPS).toHaveLength(7);
    expect(AUTHN).toHaveLength(33);
    expect(new Set(ALL).size).toBe(57);
  });

  it('starts with the suggested defaults: 7 allowed', () => {
    expect(Object.values(defaultState()).filter(Boolean)).toHaveLength(7);
  });
});

describe('security rules (BRD V24.0.0.0 section 7)', () => {
  it('BR-02: refuses to block the last allowed Authentication item', () => {
    const state = onlyAllowed('AN-M01', 'AZ-A03');
    const result = setOne(state, 'AN-M01', false);
    expect(result.state).toBe(state);
    expect(result.notice?.text).toMatch(/at least one Authentication/i);
    expect(setOne(state, 'AZ-A03', false).state['AZ-A03']).toBe(false);
  });

  it('BR-02: Block all keeps Password-based allowed', () => {
    const result = setMany(defaultState(), ALL, false);
    expect(Object.entries(result.state).filter(([, allowed]) => allowed).map(([id]) => id)).toEqual(['AN-M01']);
    expect(result.notice?.values?.name).toBe('Password-based');
  });

  it('BR-02: blocking a group keeps its first allowed item when nothing else would be left', () => {
    const result = setMany(onlyAllowed('AN-F01', 'AN-F02'), ['AN-F01', 'AN-F02', 'AN-F03', 'AN-F04', 'AN-F05'], false);
    expect(result.state['AN-F01']).toBe(true);
    expect(result.state['AN-F02']).toBe(false);
  });

  it('BR-03: bulk Allow skips Deprecated items; a single Deprecated item needs confirmation', () => {
    const result = setMany(defaultState(), ALL, true);
    expect(result.state['AZ-D05']).toBe(false);
    expect(result.state['AZ-D06']).toBe(false);
    expect(result.state['AN-M13']).toBe(true); // Legacy is not Deprecated
    expect(result.notice?.values?.count).toBe(2);
    expect(needsDeprecatedConfirm('AZ-D05', true)).toBe(true);
    expect(needsDeprecatedConfirm('AZ-D05', false)).toBe(false);
    expect(needsDeprecatedConfirm('AN-M13', true)).toBe(false);
  });

  it('BR-04: warns when MFA is allowed with fewer than two factors', () => {
    expect(mfaWarning(defaultState())).toBe(false);
    expect(mfaWarning(onlyAllowed('AN-M01', 'AN-M02', 'AN-F01'))).toBe(true);
    expect(mfaWarning(onlyAllowed('AN-M01', 'AN-F01'))).toBe(false);
  });

  it('FR-13: lists what will be allowed and blocked', () => {
    const saved = defaultState();
    const draft = { ...saved, 'AN-S03': true, 'AZ-P01': false };
    const { allowed, blocked } = changedItems(saved, draft);
    expect(allowed.map((item) => item.id)).toEqual(['AN-S03']);
    expect(blocked.map((item) => item.id)).toEqual(['AZ-P01']);
  });
});
