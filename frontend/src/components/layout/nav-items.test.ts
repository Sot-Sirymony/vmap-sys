import { describe, expect, it } from 'vitest';
import { navGroupsFor } from './nav-items';

const paths = (role?: string) => navGroupsFor(role).flatMap((group) => group.items.map((item) => item.to));

describe('navGroupsFor', () => {
  it('shows coaching to coaches and admins, and cohorts to admins only', () => {
    expect(paths('USER')).not.toContain('/coach');
    expect(paths(undefined)).not.toContain('/coach');
    expect(paths('COACH')).toContain('/coach');
    expect(paths('COACH')).not.toContain('/settings/cohorts');
    expect(paths('ADMIN')).toEqual(expect.arrayContaining(['/coach', '/settings/cohorts']));
  });
});
