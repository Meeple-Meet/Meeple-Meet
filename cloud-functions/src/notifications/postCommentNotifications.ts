/**
 * Cloud Function to send push notifications for post comments.
 *
 * - Top-level comments notify the post author.
 * - Direct replies notify the parent comment's author.
 * - Self-notifications are skipped.
 */
import * as admin from "firebase-admin";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

interface CommentDoc {
  authorId?: string;
  parentId?: string;
  text?: string;
}

interface PostDoc {
  authorId?: string;
  title?: string;
}

interface Account {
  uid: string;
  name?: string;
  fcmToken?: string;
}

async function getAccount(uid: string): Promise<Account | null> {
  try {
    const snap = await admin.firestore().collection("accounts").doc(uid).get();
    if (!snap.exists) return null;
    const data = snap.data() as Partial<Account>;
    return { uid, name: data.name, fcmToken: data.fcmToken };
  } catch (error) {
    logger.error(`Failed to fetch account ${uid}`, error);
    return null;
  }
}

export const onPostCommentCreated = onDocumentCreated(
  {
    document: "posts/{postId}/comments/{commentId}",
    region: "us-central1",
    memory: "256MiB",
    timeoutSeconds: 60,
  },
  async (event) => {
    const { postId, commentId } = event.params;
    const comment = event.data?.data() as CommentDoc | undefined;

    if (!comment?.authorId || !comment.parentId) {
      logger.info("Comment missing authorId or parentId, skipping notification.");
      return;
    }

    const [postSnap, parentCommentSnap] = await Promise.all([
      admin.firestore().collection("posts").doc(postId).get(),
      comment.parentId === postId
          ? Promise.resolve(null)
          : admin.firestore()
                .collection("posts")
                .doc(postId)
                .collection("comments")
                .doc(comment.parentId)
                .get(),
    ]);

    if (!postSnap.exists) {
      logger.warn(`Post ${postId} not found; skipping comment notification.`);
      return;
    }

    const post = postSnap.data() as PostDoc;

    // Determine receiver
    let receiverId: string | null = null;
    let isReply = false;

    if (comment.parentId === postId) {
      receiverId = post.authorId ?? null;
    } else {
      if (parentCommentSnap?.exists) {
        const parent = parentCommentSnap.data() as CommentDoc;
        receiverId = parent.authorId ?? null;
        isReply = true;
      }
    }

    if (!receiverId) {
      logger.info("No receiver for this comment notification (missing receiverId).");
      return;
    }

    if (receiverId === comment.authorId) {
      logger.info("Skipping self-notification for comment author.");
      return;
    }

    const [receiver, sender] = await Promise.all([
      getAccount(receiverId),
      getAccount(comment.authorId),
    ]);

    if (!receiver?.fcmToken) {
      logger.info(`Receiver ${receiverId} has no FCM token; skipping push.`);
      return;
    }

    const senderName = sender?.name || "Someone";
    const textSnippet = (comment.text || "").slice(0, 80);
    const title = post.title || "New comment";
    const body = isReply
        ? `${senderName} replied to your comment: ${textSnippet}`
        : `${senderName} commented on your post: ${textSnippet}`;

    const message: admin.messaging.Message = {
      token: receiver.fcmToken,
      notification: { title, body },
      data: {
        type: isReply ? "comment_reply" : "post_comment",
        postId,
        commentId,
        parentId: comment.parentId,
        senderId: comment.authorId,
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
        `Sent ${isReply ? "reply" : "comment"} notification for comment ${commentId} on post ${postId}`
      );
    } catch (error) {
      logger.error("Failed to send post comment notification", error);
    }
  }
);
