import { useCallback, useEffect, useState } from 'react';
import {
  useGetNotificationPreferencesQuery,
  useRegisterPushSubscriptionMutation,
  useRemovePushSubscriptionMutation,
  useUpdateNotificationPreferencesMutation,
} from '../services/rtk/notificationApi';
import type { PushSubscriptionPayload } from '../services/api';

/**
 * Day 16 — Web Push subscription lifecycle for the notification bell toggle.
 *
 * Flow:
 *   enable  → Notification.requestPermission() → register /sw.js →
 *             pushManager.subscribe(VAPID key) → POST subscription → PUT pref on
 *   disable → unsubscribe from pushManager → DELETE subscription → PUT pref off
 *
 * The toggle reflects the EFFECTIVE state (preference AND an active browser
 * subscription), so it never claims "on" when only half the setup exists.
 * The VAPID public key is baked at build time via VITE_VAPID_PUBLIC_KEY
 * (same pattern as VITE_API_BASE_URL) — without it the feature is absent.
 */

const VAPID_PUBLIC_KEY = import.meta.env.VITE_VAPID_PUBLIC_KEY as string | undefined;

/** True when this browser can do Web Push AND the build has a VAPID key baked in. */
export function isPushSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'serviceWorker' in navigator &&
    'PushManager' in window &&
    typeof Notification !== 'undefined' &&
    Boolean(VAPID_PUBLIC_KEY)
  );
}

export interface WebPushState {
  /** Feature available for this user/build/browser. */
  pushSupported: boolean;
  /** Effective on = preference on AND a live browser subscription. */
  pushEnabled: boolean;
  /** A permission/subscription action is in flight. */
  busy: boolean;
  /** Browser permission was explicitly denied. */
  denied: boolean;
  /** Last action error (user-facing message). */
  error: string | null;
  togglePush: () => Promise<void>;
}

/**
 * @param active whether the caller (a signed-in PATIENT) should run the flow —
 *        pass false for other roles / logged-out states so nothing fires.
 */
export function useWebPush(active: boolean): WebPushState {
  const supported = isPushSupported();

  const { data: prefs } = useGetNotificationPreferencesQuery(undefined, {
    skip: !active || !supported,
  });
  const [updatePreferences] = useUpdateNotificationPreferencesMutation();
  const [registerSubscription] = useRegisterPushSubscriptionMutation();
  const [removeSubscription] = useRemovePushSubscriptionMutation();

  const [pushEnabled, setPushEnabled] = useState(false);
  const [busy, setBusy] = useState(false);
  const [denied, setDenied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Derive the effective state from the live browser subscription + prefs.
  // (A preference row alone means nothing — the browser must be subscribed.)
  useEffect(() => {
    if (!active || !supported) {
      setPushEnabled(false);
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const registration = await navigator.serviceWorker.getRegistration();
        const subscription = registration
          ? await registration.pushManager.getSubscription()
          : null;
        if (cancelled) return;
        setPushEnabled(!!subscription && (prefs?.webPushEnabled ?? true));
        setDenied(Notification.permission === 'denied');
      } catch {
        // No SW registered yet / push manager unavailable — treat as off.
        if (!cancelled) setPushEnabled(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [active, supported, prefs?.webPushEnabled]);

  const togglePush = useCallback(async () => {
    if (busy || !supported) return;
    setBusy(true);
    setError(null);
    try {
      if (pushEnabled) {
        await disablePush(removeSubscription, updatePreferences);
        setPushEnabled(false);
      } else {
        const enabled = await enablePush(registerSubscription, updatePreferences);
        setPushEnabled(enabled);
        setDenied(!enabled && Notification.permission === 'denied');
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not update push notifications');
    } finally {
      setBusy(false);
    }
  }, [busy, pushEnabled, registerSubscription, removeSubscription, updatePreferences]);

  return { pushSupported: supported && active, pushEnabled, busy, denied, error, togglePush };
}

async function enablePush(
  registerSubscription: (p: PushSubscriptionPayload) => { unwrap: () => Promise<void> },
  updatePreferences: (p: { webPushEnabled: boolean }) => { unwrap: () => Promise<unknown> }
): Promise<boolean> {
  // 1. OS/browser permission — the user's real consent gate.
  if (Notification.permission === 'denied') return false;
  if (Notification.permission !== 'granted') {
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return false;
  }

  // 2. Register the service worker (idempotent).
  const registration = await navigator.serviceWorker.register('/sw.js');

  // 3. Subscribe — reuse an existing subscription if the browser already has one.
  let subscription = await registration.pushManager.getSubscription();
  if (!subscription) {
    try {
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY!),
      });
    } catch (e) {
      // Some browsers throw InvalidStateError when a (hidden) subscription
      // already exists — recover by re-reading it.
      if ((e as Error).name === 'InvalidStateError') {
        subscription = await registration.pushManager.getSubscription();
      } else {
        throw e;
      }
    }
  }
  if (!subscription) return false;

  // 4. Tell the backend (upsert by endpoint) + flip the preference on.
  await registerSubscription({
    endpoint: subscription.endpoint,
    keys: {
      p256dh: arrayBufferToBase64Url(subscription.getKey('p256dh')),
      auth: arrayBufferToBase64Url(subscription.getKey('auth')),
    },
  }).unwrap();
  await updatePreferences({ webPushEnabled: true }).unwrap();
  return true;
}

async function disablePush(
  removeSubscription: (p: { endpoint: string }) => { unwrap: () => Promise<void> },
  updatePreferences: (p: { webPushEnabled: boolean }) => { unwrap: () => Promise<unknown> }
): Promise<void> {
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = registration
    ? await registration.pushManager.getSubscription()
    : null;
  if (subscription) {
    const endpoint = subscription.endpoint;
    await subscription.unsubscribe();
    await removeSubscription({ endpoint }).unwrap();
  }
  await updatePreferences({ webPushEnabled: false }).unwrap();
}

/**
 * 'base64url' (no padding) → Uint8Array, for pushManager.subscribe.
 * Explicitly backed by an ArrayBuffer so the result satisfies BufferSource
 * under TS 5.9's generic typed-array typings.
 */
function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = window.atob(base64);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}

/** ArrayBuffer (browser key material) → base64url string for the backend. */
function arrayBufferToBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return '';
  const bytes = new Uint8Array(buffer);
  let binary = '';
  bytes.forEach((b) => {
    binary += String.fromCharCode(b);
  });
  return window
    .btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}
