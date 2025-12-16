/**
 * Firebase Cloud Functions for Message Notifications.
 *
 * Sends push notifications to discussion participants when new messages arrive.
 *
 * Features:
 *  - Listens to Firestore message creation events
 *  - Queries recipient FCM tokens from Firestore
 *  - Sends notifications via Firebase Admin SDK
 *  - Handles text messages, photos, and polls
 *
 * Triggers:
 *  - onMessageCreated: Triggered when a new message is added to a discussion
 */

import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

/**
 * Message data structure from Firestore
 */
interface Message {
  uid: string;
  senderId: string;
  content: string;
  photoUrl?: string;
  poll?: {
    question: string;
    options: string[];
    allowMultipleVotes: boolean;
  };
  createdAt: admin.firestore.Timestamp;
}

/**
 * Discussion data structure from Firestore
 */
interface Discussion {
  uid: string;
  name: string;
  participants: string[];
  admins: string[];
  creatorId: string;
}

/**
 * Account data structure from Firestore
 */
interface Account {
  uid: string;
  name: string;
  handle: string;
  fcmToken?: string;
}

/**
 * Fetches FCM tokens for all discussion participants except the sender.
 *
 * @param discussionId - The discussion ID
 * @param senderId - The message sender's ID (excluded from recipients)
 * @returns Array of objects containing user IDs and their FCM tokens
 */
async function getRecipientTokens(
  discussionId: string,
  senderId: string
): Promise<Array<{ uid: string; token: string; name: string }>> {
  try {
    // Get discussion document to find participants
    const discussionDoc = await admin
      .firestore()
      .collection("discussions")
      .doc(discussionId)
      .get();

    if (!discussionDoc.exists) {
      logger.warn(`Discussion ${discussionId} not found`);
      return [];
    }

    const discussion = discussionDoc.data() as Discussion;
    const participants = discussion.participants || [];

    // Filter out the sender
    const recipients = participants.filter((uid) => uid !== senderId);

    if (recipients.length === 0) {
      logger.info("No recipients to notify");
      return [];
    }

    // Fetch FCM tokens for all recipients
    const tokenPromises = recipients.map(async (uid) => {
      try {
        const accountDoc = await admin
          .firestore()
          .collection("accounts")
          .doc(uid)
          .get();

        if (!accountDoc.exists) {
          logger.warn(`Account ${uid} not found`);
          return null;
        }

        const account = accountDoc.data() as Account;
        if (!account.fcmToken) {
          logger.info(`No FCM token for user ${uid}`);
          return null;
        }

        return {
          uid: uid,
          token: account.fcmToken,
          name: account.name,
        };
      } catch (error) {
        logger.error(`Error fetching account ${uid}:`, error);
        return null;
      }
    });

    const tokens = await Promise.all(tokenPromises);
    return tokens.filter((t) => t !== null) as Array<{
      uid: string;
      token: string;
      name: string;
    }>;
  } catch (error) {
    logger.error("Error getting recipient tokens:", error);
    return [];
  }
}

/**
 * Gets the sender's name from Firestore.
 *
 * @param senderId - The sender's user ID
 * @returns The sender's name or "Someone"
 */
async function getSenderName(senderId: string): Promise<string> {
  try {
    const accountDoc = await admin
      .firestore()
      .collection("accounts")
      .doc(senderId)
      .get();

    if (!accountDoc.exists) {
      return "Someone";
    }

    const account = accountDoc.data() as Account;
    return account.name || "Someone";
  } catch (error) {
    logger.error("Error getting sender name:", error);
    return "Someone";
  }
}

/**
 * Gets the discussion name from Firestore.
 *
 * @param discussionId - The discussion ID
 * @returns The discussion name or "a discussion"
 */
async function getDiscussionName(discussionId: string): Promise<string> {
  try {
    const discussionDoc = await admin
      .firestore()
      .collection("discussions")
      .doc(discussionId)
      .get();

    if (!discussionDoc.exists) {
      return "a discussion";
    }

    const discussion = discussionDoc.data() as Discussion;
    return discussion.name || "a discussion";
  } catch (error) {
    logger.error("Error getting discussion name:", error);
    return "a discussion";
  }
}

/**
 * Formats the notification body based on message type.
 *
 * @param message - The message object
 * @param senderName - The sender's display name
 * @returns Formatted notification body text
 */
function formatNotificationBody(message: Message, senderName: string): string {
  if (message.poll) {
    return `${senderName}: 📊 ${message.poll.question}`;
  } else if (message.photoUrl) {
    const caption = message.content.trim();
    return caption
      ? `${senderName}: 📷 ${caption}`
      : `${senderName}: 📷 Photo`;
  } else {
    return `${senderName}: ${message.content}`;
  }
}

/**
 * Sends push notifications to recipients.
 *
 * @param recipients - Array of recipient objects with tokens
 * @param title - Notification title
 * @param body - Notification body
 * @param discussionId - The discussion ID for deep linking
 */
async function sendNotifications(
  recipients: Array<{ uid: string; token: string; name: string }>,
  title: string,
  body: string,
  discussionId: string
): Promise<void> {
  if (recipients.length === 0) {
    logger.info("No recipients to send notifications to");
    return;
  }

  const tokens = recipients.map((r) => r.token);

  const message: admin.messaging.MulticastMessage = {
    notification: {
      title: title,
      body: body,
    },
    data: {
      discussionId: discussionId,
      type: "new_message",
    },
    android: {
      notification: {
        channelId: "fcm_default_channel",
        sound: "default",
      },
      priority: "high",
    },
    tokens: tokens,
  };

  try {
    const response = await admin.messaging().sendEachForMulticast(message);
    logger.info(
      `Successfully sent ${response.successCount} notifications out of ${tokens.length}`
    );

    if (response.failureCount > 0) {
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          logger.error(
            `Failed to send notification to ${recipients[idx].name} (${recipients[idx].uid}):`,
            resp.error
          );
        }
      });
    }
  } catch (error) {
    logger.error("Error sending notifications:", error);
    throw error;
  }
}

/**
 * Cloud Function triggered when a new message is created in a discussion.
 *
 * Fetches discussion participants, retrieves their FCM tokens, and sends
 * push notifications to all participants except the sender.
 */
export const onMessageCreated = onDocumentCreated(
  {
    document: "discussions/{discussionId}/messages/{messageId}",
    region: "us-central1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const discussionId = event.params.discussionId;
    const messageId = event.params.messageId;

    logger.info(
      `New message created in discussion ${discussionId}: ${messageId}`
    );

    try {
      // Get message data
      const message = event.data?.data() as Message;
      if (!message) {
        logger.warn("Message data is null");
        return;
      }

      const senderId = message.senderId;

      // Get sender name and discussion name in parallel
      const [senderName, discussionName] = await Promise.all([
        getSenderName(senderId),
        getDiscussionName(discussionId),
      ]);

      // Get recipient FCM tokens
      const recipients = await getRecipientTokens(discussionId, senderId);

      if (recipients.length === 0) {
        logger.info("No recipients with FCM tokens found");
        return;
      }

      // Format notification
      const title = discussionName;
      const body = formatNotificationBody(message, senderName);

      // Send notifications
      await sendNotifications(recipients, title, body, discussionId);

      logger.info(
        `Notifications sent successfully for message ${messageId} in discussion ${discussionId}`
      );
    } catch (error) {
      logger.error("Error processing message notification:", error);
      // Don't throw - we don't want to retry indefinitely
    }
  }
);
