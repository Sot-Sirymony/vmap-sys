import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/apiClient';
import * as stepUpApi from '../../api/stepUpApi';
import { isStepUpCancelled, useStepUp } from './useStepUp';

vi.mock('../../api/stepUpApi', () => ({ requestStepUpCode: vi.fn(), verifyStepUpCode: vi.fn() }));

const required = () => new ApiError("Verify it's you to continue.", 403, 'STEP_UP_REQUIRED');
const sent = { challengeId: 41, sentTo: 's*****@g****.com', expiresAt: new Date(Date.now() + 600_000).toISOString(),
  resendAfter: new Date(Date.now() - 1000).toISOString(), delivery: 'EMAIL' as const };

/** A page with one protected action that records what happened. */
function Page({ call }: Readonly<{ call: (code?: number) => Promise<string> }>) {
  const stepUp = useStepUp('token');
  const [result, setResult] = useState('');
  return (
    <>
      <button type="button" onClick={() => {
        stepUp.protect('EXPORT_DATA', call)
          .then((value) => setResult(`done ${value}`))
          .catch((error: unknown) => setResult(isStepUpCancelled(error) ? 'cancelled' : `failed ${(error as Error).message}`));
      }}>Act</button>
      <p>{result}</p>
      {stepUp.dialog}
    </>
  );
}

describe('useStepUp and StepUpDialog (V20 SV-FR-20 to SV-FR-26)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(stepUpApi.requestStepUpCode).mockResolvedValue(sent);
  });
  afterEach(() => vi.useRealTimers());

  it('runs the action straight away when no code is needed', async () => {
    const call = vi.fn().mockResolvedValue('file');
    render(<Page call={call} />);
    await userEvent.click(screen.getByRole('button', { name: 'Act' }));
    expect(await screen.findByText('done file')).toBeInTheDocument();
    expect(call).toHaveBeenCalledWith(undefined);
    expect(stepUpApi.requestStepUpCode).not.toHaveBeenCalled();
  });

  it('asks for the emailed code, submits six digits on its own, and runs the action again once with it', async () => {
    const call = vi.fn().mockRejectedValueOnce(required()).mockResolvedValueOnce('file');
    vi.mocked(stepUpApi.verifyStepUpCode).mockRejectedValueOnce(new ApiError('That code is not right. 4 tries left.', 400, 'STEP_UP_WRONG_CODE', 4))
      .mockResolvedValueOnce({ verified: true, challengeId: 41, windowUntil: null });
    render(<Page call={call} />);
    await userEvent.click(screen.getByRole('button', { name: 'Act' }));

    const dialog = await screen.findByRole('dialog', { name: "Verify it's you" });
    expect(dialog).toHaveTextContent('We sent a 6-digit code to s*****@g****.com. It works for 10 minutes.');
    expect(stepUpApi.requestStepUpCode).toHaveBeenCalledWith('token', 'EXPORT_DATA', 'en', undefined);
    const box = screen.getByRole('textbox', { name: 'Code' });
    expect(box).toHaveAttribute('autocomplete', 'one-time-code');
    expect(box).toHaveAttribute('inputmode', 'numeric');
    expect(box).toHaveFocus();

    await userEvent.type(box, '111111');
    expect(await screen.findByRole('alert')).toHaveTextContent('That code is not right. 4 tries left.');
    expect(box).toHaveValue('');
    await userEvent.type(box, '042 917');
    expect(stepUpApi.verifyStepUpCode).toHaveBeenLastCalledWith('token', 41, '042917');
    expect(await screen.findByText('done file')).toBeInTheDocument();
    expect(call).toHaveBeenNthCalledWith(2, 41);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('stops a code that ended and offers a new one after the wait', async () => {
    vi.mocked(stepUpApi.requestStepUpCode).mockResolvedValue({ ...sent, resendAfter: new Date(Date.now() + 2500).toISOString() });
    vi.mocked(stepUpApi.verifyStepUpCode).mockRejectedValue(new ApiError('This code no longer works. Ask for a new code.', 410, 'STEP_UP_CODE_ENDED'));
    render(<Page call={vi.fn().mockRejectedValue(required())} />);
    await userEvent.click(screen.getByRole('button', { name: 'Act' }));
    const resend = await screen.findByRole('button', { name: /Send a new code in \d s/ });
    expect(resend).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Code' }), '123456');
    expect(await screen.findByText('This code no longer works. Ask for a new code.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Code' })).toBeDisabled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Send a new code' })).toBeEnabled(), { timeout: 4000 });
    await userEvent.click(screen.getByRole('button', { name: 'Send a new code' }));
    await waitFor(() => expect(stepUpApi.requestStepUpCode).toHaveBeenCalledTimes(2));
    expect(screen.getByRole('textbox', { name: 'Code' })).toBeEnabled();
  });

  it('goes on with the password checks when the server has no mail, and says why', async () => {
    vi.mocked(stepUpApi.requestStepUpCode).mockResolvedValue({ challengeId: null, sentTo: 's***@g***.com', delivery: 'NONE' });
    const call = vi.fn().mockRejectedValueOnce(required()).mockResolvedValueOnce('file');
    render(<Page call={call} />);
    await userEvent.click(screen.getByRole('button', { name: 'Act' }));
    expect(await screen.findByText(/Email is not set up on this server/)).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Code' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByText('done file')).toBeInTheDocument();
    expect(call).toHaveBeenNthCalledWith(2, undefined);
  });

  it('chains two codes for a new reminder address: the account first, then the new address (SV-FR-04)', async () => {
    const accountNeeded = new ApiError("Verify it's you to continue.", 403, 'STEP_UP_REQUIRED', undefined, 'CHANGE_REMINDER_EMAIL');
    const addressNeeded = new ApiError("Verify it's you to continue.", 403, 'STEP_UP_REQUIRED', undefined, 'CONFIRM_REMINDER_EMAIL');
    const save = vi.fn().mockRejectedValueOnce(accountNeeded).mockRejectedValueOnce(addressNeeded).mockResolvedValueOnce('saved');
    vi.mocked(stepUpApi.requestStepUpCode).mockResolvedValueOnce(sent).mockResolvedValueOnce({ ...sent, challengeId: 42, sentTo: 'n***@w***.org' });
    vi.mocked(stepUpApi.verifyStepUpCode).mockResolvedValueOnce({ verified: true, challengeId: 41, windowUntil: 'soon' })
      .mockResolvedValueOnce({ verified: true, challengeId: 42, windowUntil: null });

    function Reminder() {
      const stepUp = useStepUp('token');
      const [result, setResult] = useState('');
      return (
        <>
          <button type="button" onClick={() => {
            void stepUp.protect('CHANGE_REMINDER_EMAIL', (account) => stepUp.protect('CONFIRM_REMINDER_EMAIL',
              (confirm) => save(account, confirm) as Promise<string>, 'new@work.org')).then(setResult);
          }}>Save</button>
          <p>{result}</p>
          {stepUp.dialog}
        </>
      );
    }
    render(<Reminder />);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Code' }), '111111');
    expect(await screen.findByText(/n\*\*\*@w\*\*\*\.org/)).toBeInTheDocument();
    expect(stepUpApi.requestStepUpCode).toHaveBeenNthCalledWith(1, 'token', 'CHANGE_REMINDER_EMAIL', 'en', undefined);
    expect(stepUpApi.requestStepUpCode).toHaveBeenNthCalledWith(2, 'token', 'CONFIRM_REMINDER_EMAIL', 'en', 'new@work.org');
    await userEvent.type(screen.getByRole('textbox', { name: 'Code' }), '222222');
    expect(await screen.findByText('saved')).toBeInTheDocument();
    expect(save).toHaveBeenLastCalledWith(41, 42);
  });

  it('opens whichever code the server names when the page cannot know it in advance (SV-FR-06, SV-FR-08)', async () => {
    const roleNeeded = new ApiError("Verify it's you to continue.", 403, 'STEP_UP_REQUIRED', undefined, 'ADMIN_ROLE_CHANGE');
    const save = vi.fn().mockRejectedValueOnce(roleNeeded).mockResolvedValueOnce('saved');
    vi.mocked(stepUpApi.verifyStepUpCode).mockResolvedValueOnce({ verified: true, challengeId: 41, windowUntil: null });
    function Admin() {
      const stepUp = useStepUp('token');
      const [result, setResult] = useState('');
      return (
        <>
          <button type="button" onClick={() => void stepUp.protect(null, (code) => save(code) as Promise<string>).then(setResult)}>Save</button>
          <p>{result}</p>
          {stepUp.dialog}
        </>
      );
    }
    render(<Admin />);
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await userEvent.type(await screen.findByRole('textbox', { name: 'Code' }), '333333');
    expect(await screen.findByText('saved')).toBeInTheDocument();
    expect(stepUpApi.requestStepUpCode).toHaveBeenCalledWith('token', 'ADMIN_ROLE_CHANGE', 'en', undefined);
    expect(save).toHaveBeenLastCalledWith(41);
  });

  it('cancels quietly, says the development code is in the log, and passes other errors on', async () => {
    vi.mocked(stepUpApi.requestStepUpCode).mockResolvedValue({ ...sent, delivery: 'LOG' });
    const { unmount } = render(<Page call={vi.fn().mockRejectedValue(required())} />);
    await userEvent.click(screen.getByRole('button', { name: 'Act' }));
    expect(await screen.findByText('Development server: the code is in the server log.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(await screen.findByText('cancelled')).toBeInTheDocument();
    unmount();

    vi.mocked(stepUpApi.requestStepUpCode).mockRejectedValue(new Error("We couldn't send the code. Try again in a few minutes."));
    const second = render(<Page call={vi.fn().mockRejectedValue(required())} />);
    await userEvent.click(screen.getByRole('button', { name: 'Act' }));
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't send the code. Try again in a few minutes.");
    second.unmount();

    render(<Page call={vi.fn().mockRejectedValue(new ApiError('Report not found.', 404))} />);
    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'Act' }));
    });
    expect(await screen.findByText('failed Report not found.')).toBeInTheDocument();
  });
});
