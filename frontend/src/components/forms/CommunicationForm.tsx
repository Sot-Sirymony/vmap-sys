import { Textarea } from '../common/Textarea';
import { tr } from '../../i18n/translate';

export function CommunicationForm() {
  return <Textarea placeholder={tr('Message body')} />;
}
