package com.github.meeplemeet.model

class NotSignedInException(message: String) : Exception(message)

class DiscussionNotFoundException(message: String) : Exception(message)

class AccountNotFoundException(message: String) : Exception(message)

class PermissionDeniedException(message: String) : Exception(message)
