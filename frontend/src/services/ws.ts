import { Client } from '@stomp/stompjs';

/**
 * Real-time notification socket (STOMP over WebSocket).
 *
 * The gateway proxies /ws/** to notification-service, which authenticates the
 * JWT on the STOMP CONNECT frame (browsers cannot set HTTP headers on a WS
 * handshake). The URL is derived from VITE_API_BASE_URL (the same env the RTK
 * baseQuery uses), so dev (relative → Vite proxy), Docker (relative → Nginx
 * proxy) and Vercel (absolute → gateway FQDN) all resolve correctly.
 */

function resolveBaseUrl(): string {
  const apiBase = import.meta.env.VITE_API_BASE_URL ?? '';
  if (apiBase) {
    // https://gateway → wss://gateway, http://gateway → ws://gateway
    return apiBase.replace(/^http/, 'ws');
  }
  // Relative calls: same origin as the page (dev = Vite server, Docker = Nginx).
  return `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`;
}

export interface NotificationSocket {
  client: Client;
  /** Best-effort close; safe to call multiple times. */
  disconnect: () => void;
}

/**
 * Opens a STOMP connection and subscribes the user to their notification
 * topic. The client auto-reconnects (exponential backoff) until disconnect()
 * is called, so a dropped connection recovers without app involvement.
 */
export function connectNotificationSocket(
  token: string,
  userId: string,
  onNotification: (payload: unknown) => void,
): NotificationSocket {
  const client = new Client({
    brokerURL: `${resolveBaseUrl()}/ws`,
    // The JWT is presented on the STOMP CONNECT frame — the gateway lets the
    // handshake through and notification-service validates it.
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });

  client.onConnect = () => {
    client.subscribe(`/topic/notifications/${userId}`, (message) => {
      try {
        onNotification(JSON.parse(message.body));
      } catch {
        // A malformed push must never crash the socket — polling still covers it.
      }
    });
  };

  client.activate();
  return { client, disconnect: () => void client.deactivate() };
}
