/**
 * Firebase Cloud Functions for Meeple Meet
 *
 * Entry point for all Cloud Functions.
 * Exports BGG-related functions and notification functions.
 */

// Export BGG functions
export {
  ping,
  getGamesByIds,
  searchGames,
  handleGetGamesByIds,
  handleSearchGames,
  _clearInMemoryStateForTests
} from "./bgg/index";

// Export notification functions
export {
  onMessageCreated,
  onAccountNotificationCreated,
} from "./notifications/index";
