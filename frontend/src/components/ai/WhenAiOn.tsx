import { type ReactNode, useEffect, useState } from 'react';
import { getAiStatus } from '../../api/aiApi';

/** Shows its children only while AI is switched on for everyone (AI-NFR-02: off means no AI controls at all). */
export function WhenAiOn({ token, children }: { token: string | null; children: ReactNode }) {
  const [on, setOn] = useState(false);
  useEffect(() => {
    if (!token) {
      return;
    }
    getAiStatus(token).then((status) => setOn(status.reason !== 'OFF')).catch(() => setOn(false));
  }, [token]);
  return on ? <>{children}</> : null;
}
