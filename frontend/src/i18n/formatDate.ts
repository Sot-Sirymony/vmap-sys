import { currentLanguage } from './translate';

const KM_WEEKDAYS = ['អាទិត្យ', 'ច័ន្ទ', 'អង្គារ', 'ពុធ', 'ព្រហស្បតិ៍', 'សុក្រ', 'សៅរ៍'];
const KM_MONTHS = ['មករា', 'កុម្ភៈ', 'មីនា', 'មេសា', 'ឧសភា', 'មិថុនា', 'កក្កដា', 'សីហា', 'កញ្ញា', 'តុលា', 'វិច្ឆិកា', 'ធ្នូ'];

type DateParts = { weekday?: boolean; year?: boolean };

/**
 * NFR-03: a date in the current language. Browsers often ship without Khmer
 * date names (Chrome falls back to English), so Khmer is built here instead
 * of relying on toLocaleDateString('km-KH').
 */
export function formatDate(date: Date, { weekday = false, year = false }: DateParts = {}) {
  if (currentLanguage() === 'km') {
    const parts = [`${date.getDate()}`, KM_MONTHS[date.getMonth()]];
    if (year) {
      parts.push(`${date.getFullYear()}`);
    }
    return (weekday ? `ថ្ងៃ${KM_WEEKDAYS[date.getDay()]} ` : '') + parts.join(' ');
  }
  return date.toLocaleDateString('en-US', {
    month: weekday ? 'long' : 'short',
    day: 'numeric',
    ...(weekday ? { weekday: 'long' } : {}),
    ...(year ? { year: 'numeric' } : {}),
  });
}
