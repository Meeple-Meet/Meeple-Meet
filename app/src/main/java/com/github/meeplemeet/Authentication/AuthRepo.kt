package com.github.meeplemeet.Authentication

import androidx.credentials.Credential
import kotlinx.coroutines.flow.Flow

/**
 * Interface for authentication repository supporting email/password and Google sign-in.
 */
interface AuthRepo {
    /**
     * Registers a new user using email and password.
     * @param email The user's email address.
     * @param password The user's password.
     * @return Result containing the created User or an error.
     */
    suspend fun registerWithEmail(email: String, password: String): Result<User>

    /**
     * Authenticates an existing user with email and password.
     * @param email The user's email address.
     * @param password The user's password.
     * @return Result containing the authenticated User or an error.
     */
    suspend fun loginWithEmail(email: String, password: String): Result<User>

    /**
     * Authenticates a user using a Google credential obtained from the Credential Manager API.
     * @param credential The Google credential object containing authentication data.
     * @return Result containing the authenticated User or an error.
     */
    suspend fun loginWithGoogle(credential: Credential): Result<User>

    /**
     * Signs out the currently authenticated user.
     */
    suspend fun logout(): Result<Unit>

    /**
     * Returns the currently authenticated user, or null if no user is signed in.
     * @return The current User or null.
     */
    suspend fun getCurrentUser(): User?
}