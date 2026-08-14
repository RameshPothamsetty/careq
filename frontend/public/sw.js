/*
 * CareQ — service worker (Day 16 Web Push).
 *
 * Registered lazily when a patient enables push notifications in the bell.
 * The backend sends an encrypted JSON payload { type, message }; the type
 * maps to a title here (mirroring the bell's TYPE_META) and the message is
 * the same human-readable sentence the in-app notification shows.
 */
'use strict';

const TITLES = {
  'queue.joined': 'Queue joined',
  'queue.triaged': 'Urgency updated',
  'queue.called': "It's your turn!",
  'queue.completed': 'Consultation complete',
};

self.addEventListener('install', () => {
  // Activate immediately so the new SW takes over on the next load.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(clients.claim());
});

self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch {
    // Non-JSON payload — fall back to defaults below.
  }

  const type = typeof payload.type === 'string' ? payload.type : '';
  const title = TITLES[type] || 'CareQ';
  const body =
    typeof payload.message === 'string'
      ? payload.message
      : 'Your queue status has changed.';

  event.waitUntil(
    self.registration.showNotification(title, {
      body,
      tag: type || 'careq',
      renotify: false,
      // Same page as the notification click target — the patient queue.
      data: { url: '/queue' },
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/';

  event.waitUntil(
    clients
      .matchAll({ type: 'window', includeUncontrolled: true })
      .then((windowClients) => {
        for (const client of windowClients) {
          if ('focus' in client) {
            client.focus();
            // Same-tab navigation to the queue page (ignore failures — the
            // tab may be on a route the SPA rejects).
            try {
              client.navigate(url);
            } catch {
              // non-window client or navigation rejected — focus is enough
            }
            return;
          }
        }
        return clients.openWindow(url);
      })
  );
});
