import { Input } from '../common/Input';
import { tr } from '../../i18n/translate';

export function GoalForm() {
  return <Input placeholder={tr('Goal title')} />;
}
