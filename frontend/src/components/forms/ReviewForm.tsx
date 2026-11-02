import { Textarea } from '../common/Textarea';
import { tr } from '../../i18n/translate';

export function ReviewForm() {
  return <Textarea placeholder={tr('Review notes')} />;
}
