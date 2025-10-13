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
    const val DEFAULT_MESSAGE = "Handle should only contain letters and digits"
  }
}
