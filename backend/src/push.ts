import admin from 'firebase-admin';

let messaging: admin.messaging.Messaging | null = null;

export function initFirebase(): void {
  const jsonStr = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!jsonStr) {
    console.warn('[push] FIREBASE_SERVICE_ACCOUNT_JSON not set; push disabled');
    return;
  }

  try {
    const serviceAccount = JSON.parse(jsonStr);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
    });
    messaging = admin.messaging();
    console.log('[push] Firebase initialized');
  } catch (err) {
    console.error('[push] Failed to init Firebase', err);
  }
}

interface PushPayload {
  title: string;
  body: string;
  data?: Record<string, string>;
}

export async function sendPush(token: string, payload: PushPayload): Promise<void> {
  if (!messaging) return;

  await messaging.send({
    token,
    notification: {
      title: payload.title,
      body: payload.body,
    },
    data: payload.data,
    android: {
      priority: 'high',
    },
  });
}
