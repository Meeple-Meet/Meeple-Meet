package com.github.meeplemeet.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import androidx.credentials.CredentialManager

@Composable
fun SignUpScreen(
    navController: NavController = NavController(LocalContext.current),
    viewModel: AuthViewModel = AuthViewModel(),
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    // Validation functions
    fun validateEmail(email: String): String? {
        return when {
            email.isEmpty() -> "Email cannot be empty"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Invalid email format"
            else -> null
        }
    }

    fun validatePassword(password: String): String? {
        return when {
            password.isEmpty() -> "Password cannot be empty"
            password.length < 6 -> "Password is too weak"
            else -> null
        }
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isEmpty() -> "Please confirm your password"
            password != confirmPassword -> "Passwords do not match"
            else -> null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        // Logo placeholder
        Box(modifier = Modifier.size(100.dp)) {
            Surface(color = Color.LightGray, modifier = Modifier.fillMaxSize()) {}
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Welcome!", style = TextStyle(fontSize = 28.sp), modifier = Modifier.padding(bottom = 16.dp))

        // Email field with error handling
        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                emailError = null // Clear error on input
            },
            label = { Text("Email") },
            singleLine = true,
            isError = emailError != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_field")
        )
        if (emailError != null) {
            Text(
                text = emailError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Password field with visibility toggle and error handling
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null // Clear error on input
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = passwordError != null,
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.testTag("password_visibility_toggle")
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("password_field")
        )
        if (passwordError != null) {
            Text(
                text = passwordError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Confirm password field with visibility toggle and error handling
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                confirmPasswordError = null // Clear error on input
            },
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = confirmPasswordError != null,
            trailingIcon = {
                IconButton(
                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                    modifier = Modifier.testTag("confirm_password_visibility_toggle")
                ) {
                    Icon(
                        imageVector = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_password_field")
        )
        if (confirmPasswordError != null) {
            Text(
                text = confirmPasswordError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sign up button with loading state and validation
        Button(
            onClick = {
                // Validate all fields before submission
                val emailValidation = validateEmail(email)
                val passwordValidation = validatePassword(password)
                val confirmPasswordValidation = validateConfirmPassword(password, confirmPassword)

                emailError = emailValidation
                passwordError = passwordValidation
                confirmPasswordError = confirmPasswordValidation

                // Only proceed if all validation passes
                if (emailValidation == null && passwordValidation == null && confirmPasswordValidation == null) {
                    viewModel.registerWithEmail(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_up_button"),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("loading_indicator"),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Sign Up")
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text("OR", style = TextStyle(fontSize = 16.sp, color = Color.Gray), modifier = Modifier.padding(vertical = 4.dp))
        Button(
            onClick = { viewModel.googleSignIn(context, credentialManager) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("google_sign_up_button"),
            enabled = !uiState.isLoading
        ) {
            Text("Connect with Google", color = Color.Black)
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Already have an account? ")
            Text(
                text = "Log in.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { navController.navigate("SignInScreen") }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Error message display for authentication errors
    if (uiState.errorMsg != null) {
        // Display authentication errors that come from the backend
        val errorMessage = when {
            uiState.errorMsg!!.contains("email-already-in-use") -> "Email already in use"
            uiState.errorMsg!!.contains("weak-password") -> "Password is too weak"
            uiState.errorMsg!!.contains("invalid-email") -> "Invalid email format"
            else -> uiState.errorMsg!!
        }

        Snackbar(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(errorMessage)
        }
    }
}