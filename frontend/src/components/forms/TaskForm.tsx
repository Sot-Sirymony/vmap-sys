import { Input } from '../common/Input';
import { tr } from '../../i18n/translate';

export function TaskForm() {
  return <Input placeholder={tr('Task title')} />;
}
