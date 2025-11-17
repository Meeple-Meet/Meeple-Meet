package com.github.meeplemeet.model

/** Thrown when an operation requires a signed-in user but none is available. */
class NotSignedInException(message: String) : Exception(message)

/** Thrown when a discussion cannot be found in Firestore by the requested ID. */
class DiscussionNotFoundException(message: String = DEFAULT_MESSAGE) : Exception(message) {
  companion object {
    const val DEFAULT_MESSAGE = "Discussion does not exist."
  }
}

/** Thrown when an account cannot be found in Firestore by the requested ID. */
class AccountNotFoundException(message: String = DEFAULT_MESSAGE) : Exception(message) {
  companion object {
    const val DEFAULT_MESSAGE = "Account does not exist."
  }
}

/** Thrown when a user attempts an action they do not have permission to perform. */
class PermissionDeniedException(message: String) : Exception(message)

/** Throws when an account handle has already been assigned to another user. */
class HandleAlreadyTakenException(message: String = DEFAULT_MESSAGE) : Exception(message) {
  companion object {
    const val DEFAULT_MESSAGE = "This handle has already been assigned."
  }
}

/** Throws when an account handle has already been assigned to another user. */
class InvalidHandleFormatException(message: String = DEFAULT_MESSAGE) : Exception(message) {
  companion object {
    const val DEFAULT_MESSAGE =
        "Handle should be of size 4-32 and only contain letters, digits or underscores"
  }
}

/** Thrown when a game cannot be found in Firestore by the requested ID. */
class GameNotFoundException(message: String = DEFAULT_MESSAGE) : Exception(message) {
  companion object {
    const val DEFAULT_MESSAGE = "Game does not exist."
  }
}

/**
 * Exception thrown when a Nominatim API search fails.
 *
 * @param message A human-readable message describing the failure.
 */
class LocationSearchException(message: String) : Exception(message)

/**
 * Exception thrown when a BGG API request or search fails.
 *
 * @param message A human-readable message describing the failure.
 */
class GameSearchException(message: String) : Exception(message)

/**
 * Exception thrown when a required field cannot be parsed from a BGG XML response.
 *
 * @param itemId The BGG item ID that failed to parse (may be null if unknown).
 * @param field The name of the field that failed to parse.
 * @param message A human-readable message describing the parsing error.
 */
class GameParseException(val itemId: String?, val field: String, message: String) :
    Exception(message)
