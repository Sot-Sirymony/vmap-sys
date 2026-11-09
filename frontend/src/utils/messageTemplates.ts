import { tr } from '../i18n/translate';

/**
 * V15 (AH-FR-50, AH-FR-65): the program's follow-up and thank-you templates,
 * used when AI is off. They only fill a new message; the participant edits and
 * saves it.
 */
export function followUpTemplate(name: string, subject: string, request: string): string {
  return [
    tr('Dear {name},', { name: name || tr('there') }),
    tr('I hope you are well. I am following up on my message about "{subject}".', { subject: subject || tr('my request') }),
    request ? tr('I had asked whether you could help with: {request}', { request }) : '',
    tr('I know you are busy, so even a short reply or a 15-minute call would help. Thank you for considering it.'),
    tr('Best regards'),
  ].filter(Boolean).join('\n\n');
}

export function thankYouTemplate(name: string, request: string, progress: string): string {
  return [
    tr('Dear {name},', { name: name || tr('there') }),
    request ? tr('Thank you for your help with: {request}', { request }) : tr('Thank you for your help.'),
    progress,
    tr('Your support made a real difference, and I will keep you posted.'),
    tr('Best regards'),
  ].filter(Boolean).join('\n\n');
}

/** A progress line for the thank-you, from the linked task or goal: "Concept note is now 80% done." */
export function progressLine(title: string | null | undefined, percent: number | null | undefined): string {
  if (!title || percent === null || percent === undefined) {
    return '';
  }
  return tr('{title} is now {percent}% done.', { title, percent: Math.round(percent) });
}
