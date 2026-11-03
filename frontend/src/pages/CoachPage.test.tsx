import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listCoachParticipants } from '../api/cohortApi';
import type { CoachParticipant } from '../types/vision';
import { CoachPage } from './CoachPage';

let role = 'COACH';
vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'token', user: { role } }) }));
vi.mock('../api/cohortApi', () => ({ listCoachParticipants: vi.fn() }));
vi.mock('../api/aiApi', () => ({ listAiSafetyAlerts: vi.fn().mockResolvedValue([]), acknowledgeAiSafetyAlert: vi.fn() }));

function participant(fullName: string, daysInactive: number): CoachParticipant {
  return {
    userId: daysInactive + 1,
    fullName,
    email: `${fullName}@example.com`,
    cohortId: 1,
    cohortName: 'Pilot',
    lastActiveAt: '2026-11-01T00:00:00Z',
    daysInactive,
    atRisk: daysInactive >= 7,
    metrics: { metrics: [{ id: 'BO-01', label: 'Dreams written and ranked', value: '1', target: 'At least 1', met: true }] },
  };
}

const page = () => render(<MemoryRouter><CoachPage /></MemoryRouter>);

describe('CoachPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    role = 'COACH';
  });

  it('turns away someone who is not a coach without loading anything', () => {
    role = 'USER';
    page();
    expect(screen.getByText('Only a coach can open the coach workspace.')).toBeInTheDocument();
    expect(listCoachParticipants).not.toHaveBeenCalled();
  });

  it('lists the quietest first, flags who is at risk, and filters to them', async () => {
    vi.mocked(listCoachParticipants).mockResolvedValue([participant('Active', 0), participant('Quiet', 9)]);
    page();

    const links = await screen.findAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual(['Quiet', 'Active']);
    expect(screen.getByText('1 participant(s) have done nothing for 7 days or more.')).toBeInTheDocument();
    expect(screen.getByText(/Last active 9 day\(s\) ago/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('switch', { name: 'At risk only' }));
    expect(screen.getAllByRole('link').map((link) => link.textContent)).toEqual(['Quiet']);
  });
});
