import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createDream } from '../../api/dreamApi';
import { createGoal } from '../../api/goalApi';
import { createVisionArea } from '../../api/visionAreaApi';
import { aDream, aGoal, aVisionArea } from '../../test/builders';
import { currentLocation, renderPage } from '../../test/render';
import { DreamWizard } from './DreamWizard';
import { VisionAreaWizard } from './VisionAreaWizard';

vi.mock('../../api/dreamApi');
vi.mock('../../api/goalApi');
vi.mock('../../api/visionAreaApi');


describe('DreamWizard (CLAUDE.md page 3: the coaching questions)', () => {
  const areas = [aVisionArea({ id: 1, name: 'Career' }), aVisionArea({ id: 2, name: 'Health' })];
  const handlers = () => ({ onClose: vi.fn(), onSkip: vi.fn(), onCreated: vi.fn() });

  beforeEach(() => vi.clearAllMocks());

  it('asks what, why and when, then creates the dream with the answers', async () => {
    vi.mocked(createDream).mockResolvedValue(aDream({ id: 9, title: 'Run a marathon' }));
    const props = handlers();
    renderPage(<DreamWizard token="token" visionAreas={areas} {...props} />);

    expect(screen.getByText('Step 1 of 3')).toBeInTheDocument();
    const next = screen.getByRole('button', { name: 'Continue' });
    expect(next).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/What exactly do you want to achieve/), 'Run a marathon');
    await userEvent.click(screen.getByRole('combobox', { name: 'Which area of life or work does this belong to?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Health' }));
    await userEvent.click(screen.getByRole('combobox', { name: 'How far out does it reach?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Short Term' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    expect(screen.getByText('Step 2 of 3')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/Why does this matter to you/), '  Feel strong at 40  ');
    await userEvent.type(screen.getByLabelText(/What will success look like/), 'Finish in under 5 hours');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    expect(screen.getByText('Step 3 of 3')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText(/When do you want to achieve it/), '2027-10-10');
    await userEvent.click(screen.getByRole('combobox', { name: 'How important is it right now?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Critical' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create dream' }));

    expect(createDream).toHaveBeenCalledWith('token', expect.objectContaining({
      visionAreaId: 2, title: 'Run a marathon', whyImportant: 'Feel strong at 40', successDefinition: 'Finish in under 5 hours',
      dreamType: 'SHORT_TERM', priority: 'CRITICAL', targetDate: '2027-10-10', status: 'ACTIVE',
    }));
    expect(await screen.findByText('Run a marathon')).toBeInTheDocument();
    expect(screen.getByText(/is on the map/)).toBeInTheDocument();
  });

  it('goes back without losing answers, and can be skipped', async () => {
    const props = handlers();
    renderPage(<DreamWizard token="token" visionAreas={areas} initialVisionAreaId="1" {...props} />);
    await userEvent.type(screen.getByLabelText(/What exactly do you want to achieve/), 'Write a book');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByLabelText(/What exactly do you want to achieve/)).toHaveValue('Write a book');

    await userEvent.click(screen.getByRole('button', { name: 'Skip the guide' }));
    expect(props.onSkip).toHaveBeenCalled();
  });

  it('shows why the dream could not be created, and stays on the last step', async () => {
    vi.mocked(createDream).mockRejectedValueOnce(new Error('Target date must be in the future.'));
    renderPage(<DreamWizard token="token" visionAreas={areas} {...handlers()} />);
    await userEvent.type(screen.getByLabelText(/What exactly do you want to achieve/), 'Write a book');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create dream' }));
    expect(await screen.findByText('Target date must be in the future.')).toBeInTheDocument();
    expect(screen.getByText('Step 3 of 3')).toBeInTheDocument();
  });

  it('adds goals under the new dream, then opens its Vision Map', async () => {
    vi.mocked(createDream).mockResolvedValue(aDream({ id: 9, title: 'Write a book' }));
    vi.mocked(createGoal).mockResolvedValueOnce(aGoal()).mockRejectedValueOnce(new Error('That goal already exists.'));
    const props = handlers();
    renderPage(<DreamWizard token="token" visionAreas={areas} {...props} />);
    await userEvent.type(screen.getByLabelText(/What exactly do you want to achieve/), 'Write a book');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create dream' }));

    const goal = await screen.findByPlaceholderText('e.g. Deliver three talks at local meetups');
    expect(screen.getByRole('button', { name: 'Add goal' })).toBeDisabled();
    await userEvent.type(goal, 'Finish the first draft');
    await userEvent.click(screen.getByRole('button', { name: 'Add goal' }));
    expect(createGoal).toHaveBeenCalledWith('token', expect.objectContaining({ dreamId: 9, title: 'Finish the first draft', status: 'NOT_STARTED' }));
    expect(await screen.findByText('Finish the first draft')).toBeInTheDocument();
    expect(goal).toHaveValue('');

    await userEvent.type(goal, 'Finish the first draft');
    await userEvent.click(screen.getByRole('button', { name: 'Add goal' }));
    expect(await screen.findByText('That goal already exists.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Open the Vision Map' }));
    expect(props.onCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
    expect(props.onClose).toHaveBeenCalled();
    expect(currentLocation()).toBe('/dreams/9');
  });

  it('finishes with Done, handing back the created dream', async () => {
    vi.mocked(createDream).mockResolvedValue(aDream({ id: 9 }));
    const props = handlers();
    renderPage(<DreamWizard token="token" visionAreas={areas} {...props} />);
    await userEvent.type(screen.getByLabelText(/What exactly do you want to achieve/), 'Write a book');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create dream' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Done' }));
    expect(props.onCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 9 }));
  });
});

describe('VisionAreaWizard', () => {
  const handlers = () => ({ onClose: vi.fn(), onSkip: vi.fn(), onCreated: vi.fn() });

  beforeEach(() => vi.clearAllMocks());

  it('offers common areas, marks the ones already added, and creates the chosen one', async () => {
    vi.mocked(createVisionArea).mockResolvedValue(aVisionArea({ id: 4, name: 'Health' }));
    renderPage(<VisionAreaWizard token="token" existingNames={['career']} {...handlers()} />);

    expect(screen.getByText('Career ✓')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDisabled();
    await userEvent.click(screen.getByText('Health'));
    expect(screen.getByLabelText(/What area of life or work is this/)).toHaveValue('Health');
    await userEvent.click(screen.getByRole('combobox', { name: 'How important is it right now?' }));
    await userEvent.click(await screen.findByRole('option', { name: 'Medium' }));
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    await userEvent.type(screen.getByLabelText(/What does this area look like when it's going well/), 'Energy for my family');
    await userEvent.click(screen.getByRole('button', { name: 'Create vision area' }));
    expect(createVisionArea).toHaveBeenCalledWith('token', expect.objectContaining({ name: 'Health', priority: 'MEDIUM', visionStatement: 'Energy for my family' }));
    expect(await screen.findByPlaceholderText('e.g. Become a confident public speaker')).toBeInTheDocument();
  });

  it('adds dreams under the new area, then opens the Dreams page', async () => {
    vi.mocked(createVisionArea).mockResolvedValue(aVisionArea({ id: 4, name: 'Health' }));
    vi.mocked(createDream).mockResolvedValueOnce(aDream()).mockRejectedValueOnce(new Error('Title is too long.'));
    const props = handlers();
    renderPage(<VisionAreaWizard token="token" {...props} />);
    await userEvent.type(screen.getByLabelText(/What area of life or work is this/), 'Health');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create vision area' }));

    const dream = await screen.findByPlaceholderText('e.g. Become a confident public speaker');
    await userEvent.type(dream, 'Run a marathon');
    await userEvent.click(screen.getByRole('button', { name: 'Add dream' }));
    expect(createDream).toHaveBeenCalledWith('token', expect.objectContaining({ visionAreaId: 4, title: 'Run a marathon' }));
    expect(await screen.findByText('Run a marathon')).toBeInTheDocument();

    await userEvent.type(dream, 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Add dream' }));
    expect(await screen.findByText('Title is too long.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'View Dreams' }));
    expect(props.onCreated).toHaveBeenCalledWith(expect.objectContaining({ id: 4 }));
    expect(currentLocation()).toBe('/dreams');
  });

  it('goes back, can be skipped, and shows why an area could not be created', async () => {
    vi.mocked(createVisionArea).mockRejectedValueOnce(new Error('You already have an area with that name.'));
    const props = handlers();
    renderPage(<VisionAreaWizard token="token" {...props} />);
    await userEvent.type(screen.getByLabelText(/What area of life or work is this/), 'Career');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByLabelText(/What area of life or work is this/)).toHaveValue('Career');
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Create vision area' }));
    expect(await screen.findByText('You already have an area with that name.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Back' }));
    await userEvent.click(screen.getByRole('button', { name: 'Skip the guide' }));
    expect(props.onSkip).toHaveBeenCalled();
  });
});
