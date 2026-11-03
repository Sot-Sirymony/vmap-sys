import { API_BASE_URL, apiClient, clearApiCache, reportSessionEnded } from './apiClient';
import type { AiChatTurn, AiConversation, AiFocus, AiMessage, AiSafetyAlert, AiStatus } from '../types/vision';

// V9 Phase 3: AI consent, the Dream Coach chat and crisis alerts.

export function getAiStatus(token: string) {
  return apiClient<AiStatus>('/ai/status', { token });
}

export function setAiConsent(token: string, consent: boolean) {
  return apiClient<AiStatus>('/ai/consent', { method: 'PUT', token, body: JSON.stringify({ consent }) });
}

export function listAiConversations(token: string) {
  return apiClient<AiConversation[]>('/ai/conversations', { token });
}

export function getAiConversation(token: string, id: number) {
  return apiClient<AiConversation>(`/ai/conversations/${id}`, { token });
}

export function deleteAiConversation(token: string, id: number) {
  return apiClient<void>(`/ai/conversations/${id}`, { method: 'DELETE', token });
}

/** Clears the whole Dream Coach history of the signed-in person. */
export function deleteAllAiConversations(token: string) {
  return apiClient<void>('/ai/conversations', { method: 'DELETE', token });
}

export function sendAiMessage(token: string, conversationId: number | null, message: string, language: string, focus?: AiFocus) {
  return apiClient<AiChatTurn>('/ai/chat', {
    method: 'POST',
    token,
    body: JSON.stringify({ conversationId, message, language, ...focus }),
  });
}

/**
 * AI-NFR-01: sends a message and streams the reply. onText gets each piece
 * as it arrives; the promise resolves with the saved turn, whose reply text is
 * the one to keep. A problem before the stream starts (no consent, daily
 * limit) rejects with the server's message, like any other call.
 */
export async function streamAiMessage(
  token: string,
  conversationId: number | null,
  message: string,
  language: string,
  focus: AiFocus,
  onText: (text: string) => void,
): Promise<AiChatTurn> {
  const response = await fetch(`${API_BASE_URL}/ai/chat/stream`, {
    method: 'POST',
    // JSON too: a problem found before streaming starts answers as a JSON error.
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ conversationId, message, language, ...focus }),
  });
  clearApiCache();
  if (!response.ok || !response.body) {
    reportSessionEnded(response, token);
    let errorMessage = 'AI help is temporarily unavailable.';
    try {
      const error = (await response.json()) as { message?: string };
      errorMessage = error.message || errorMessage;
    } catch {
      // Not JSON: keep the general message.
    }
    throw new Error(errorMessage);
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const frame = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const event = /^event: (.*)$/m.exec(frame)?.[1];
      const data = /^data: (.*)$/m.exec(frame)?.[1];
      if (event && data) {
        const payload = JSON.parse(data) as { text?: string; message?: string };
        if (event === 'delta' && payload.text) {
          onText(payload.text);
        } else if (event === 'done') {
          return payload as unknown as AiChatTurn;
        } else if (event === 'error') {
          throw new Error(payload.message || 'AI help is temporarily unavailable.');
        }
      }
      boundary = buffer.indexOf('\n\n');
    }
  }
  throw new Error('AI help is temporarily unavailable.');
}

export function rateAiMessage(token: string, id: number, rating: number) {
  return apiClient<AiMessage>(`/ai/messages/${id}/rating`, { method: 'PUT', token, body: JSON.stringify({ rating }) });
}

export function listAiSafetyAlerts(token: string) {
  return apiClient<AiSafetyAlert[]>('/coach/ai-alerts', { token });
}

export function acknowledgeAiSafetyAlert(token: string, id: number) {
  return apiClient<AiSafetyAlert>(`/coach/ai-alerts/${id}/acknowledge`, { method: 'POST', token });
}
