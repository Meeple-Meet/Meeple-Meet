/**
 * Firebase Cloud Functions for Push Notifications.
 *
 * Entry point for all notification-related Cloud Functions.
 */

import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}

// Export notification functions
export { onMessageCreated } from "./messageNotifications";
export { onAccountNotificationCreated } from "./accountNotifications";
export { onPostCommentCreated } from "./postCommentNotifications";
