/**
 * Cloud Function to fan out account-level notifications via FCM.
 *
 * Triggers when a new document is created in accounts/{accountId}/notifications/{notificationId}
 * and sends a push notification for:
 *  - FRIEND_REQUEST
 *  - JOIN_DISCUSSION
 *  - JOIN_SESSION
 */
import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

type NotificationType = "FRIEND_REQUEST" | "JOIN_DISCUSSION" | "JOIN_SESSION";

interface Account {
  uid: string;
  name?: string;
  handle?: string;
  fcmToken?: string;
}

interface Discussion {
  uid: string;
  name?: string;
  session?: {
    name?: string;
    location?: { name?: string };
    date?: admin.firestore.Timestamp;
  };
}

interface NotificationDoc {
  senderId?: string;
  discussionId?: string;
  type?: NotificationType;
}

async function getAccount(uid: string): Promise<Account | null> {
  try {
    const doc = await admin.firestore().collection("accounts").doc(uid).get();
    if (!doc.exists) return null;
    const data = doc.data() as Partial<Account>;
    return {
      uid,
      name: data.name,
      handle: data.handle,
      fcmToken: data.fcmToken,
    };
  } catch (error) {
    logger.error(`Failed to fetch account ${uid}`, error);
    return null;
  }
}

async function getDiscussion(uid: string): Promise<Discussion | null> {
  try {
    const doc = await admin.firestore().collection("discussions").doc(uid).get();
    if (!doc.exists) return null;
    const data = doc.data() as Partial<Discussion>;
    return {
      uid,
      name: data.name,
      session: data.session,
    };
  } catch (error) {
    logger.error(`Failed to fetch discussion ${uid}`, error);
    return null;
  }
}

function buildTitleAndBody(
  type: NotificationType,
  sender: Account | null,
  discussion: Discussion | null
): { title: string; body: string } {
  const senderName = sender?.name || "Someone";

  switch (type) {
    case "FRIEND_REQUEST":
      return {
        title: "New friend request",
        body: `${senderName} sent you a friend request`,
      };
    case "JOIN_DISCUSSION":
      return {
        title: "Discussion invite",
        body: `${senderName} invited you to join ${discussion?.name ?? "a discussion"}`,
      };
    case "JOIN_SESSION": {
      const sessionName =
        discussion?.session?.name || discussion?.name || "a session";
      return {
        title: "Session invite",
        body: `${senderName} invited you to join ${sessionName}`,
      };
    }
    default:
      return { title: "Notification", body: `${senderName} sent you an invite` };
  }
}

export const onAccountNotificationCreated = onDocumentCreated(
  {
    document: "accounts/{accountId}/notifications/{notificationId}",
    region: "us-central1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const receiverId = event.params.accountId;
    const notif = event.data?.data() as NotificationDoc | undefined;

    if (!notif?.type || !notif.senderId) {
      logger.warn(
        "Notification missing type or senderId, skipping push",
        notif
      );
      return;
    }

    const [receiver, sender, discussion] = await Promise.all([
      getAccount(receiverId),
      getAccount(notif.senderId),
      notif.discussionId ? getDiscussion(notif.discussionId) : Promise.resolve(null),
    ]);

    if (!receiver?.fcmToken) {
      logger.info(`No FCM token for receiver ${receiverId}, skipping push`);
      return;
    }

    const { title, body } = buildTitleAndBody(notif.type, sender, discussion);

    const message: admin.messaging.Message = {
      token: receiver.fcmToken,
      notification: { title, body },
      data: {
        type: "account_notification",
        notificationType: notif.type,
        discussionId: notif.discussionId ?? "",
        senderId: notif.senderId,
      },
      android: {
        priority: "high",
        notification: {
          channelId: "fcm_default_channel",
          sound: "default",
        },
      },
    };

    try {
      await admin.messaging().send(message);
      logger.info(
        `Sent ${notif.type} push to ${receiverId} for notification ${event.params.notificationId}`
      );
    } catch (error) {
      logger.error("Failed to send account notification push", error);
    }
  }
);
