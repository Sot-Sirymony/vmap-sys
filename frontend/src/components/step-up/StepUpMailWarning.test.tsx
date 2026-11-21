import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as stepUpApi from '../../api/stepUpApi';
import { StepUpMailWarning } from './StepUpMailWarning';

vi.mock('../../api/stepUpApi', () => ({ getStepUpStatus: vi.fn() }));

describe('StepUpMailWarning (V20 SV-FR-34)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('warns admins while codes are on but no mail can carry them', async () => {
    vi.mocked(stepUpApi.getStepUpStatus).mockResolvedValue({ enabled: true, delivery: 'NONE', active: false });
    render(<StepUpMailWarning token="token" />);
    expect(await screen.findByText(/Verification codes cannot be sent/)).toBeInTheDocument();
  });

  it('says nothing when mail works, codes are off, the status cannot be read, or nobody is signed in', async () => {
    vi.mocked(stepUpApi.getStepUpStatus).mockResolvedValueOnce({ enabled: true, delivery: 'EMAIL', active: true })
      .mockResolvedValueOnce({ enabled: false, delivery: 'NONE', active: false })
      .mockRejectedValueOnce(new Error('Forbidden'));
    for (let index = 0; index < 3; index++) {
      const { unmount } = render(<StepUpMailWarning token="token" />);
      await waitFor(() => expect(stepUpApi.getStepUpStatus).toHaveBeenCalledTimes(index + 1));
      expect(screen.queryByText(/Verification codes cannot be sent/)).not.toBeInTheDocument();
      unmount();
    }
    render(<StepUpMailWarning token={null} />);
    expect(stepUpApi.getStepUpStatus).toHaveBeenCalledTimes(3);
  });
});
