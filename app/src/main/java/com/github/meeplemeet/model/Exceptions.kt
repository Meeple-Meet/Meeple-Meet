package com.github.meeplemeet.model

/** Thrown when an operation requires a signed-in user but none is available. */
class NotSignedInException(message: String) : Exception(message)

/** Thrown when a discussion cannot be found in Firestore by the requested ID. */
class DiscussionNotFoundException(message: String) : Exception(message)

/** Thrown when an account cannot be found in Firestore by the requested ID. */
class AccountNotFoundException(message: String) : Exception(message)

/** Thrown when a user attempts an action they do not have permission to perform. */
class PermissionDeniedException(message: String) : Exception(message)

/** Thrown when a game cannot be found in Firestore by the requested ID. */
class GameNotFoundException(message: String) : Exception(message)
