package com.github.meeplemeet

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.Authentication.AuthRepoFirebase
import com.github.meeplemeet.model.systems.FirestoreRepository
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.ui.SignInScreen
import com.github.meeplemeet.ui.SignUpScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * `MainActivity` is the entry point of the application. It sets up the content view with the
 * `onCreate` methods. You can run the app by running the `app` configuration in Android Studio. NB:
 * Make sure you have an Android emulator running or a physical device connected.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Set up dependencies for AuthViewModel
    val firestore = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    // Configure Firebase emulators for testing - only if available
    try {
      Log.d("MainActivity", "Attempting to configure Firebase emulators...")

      // Try to connect to emulators - this will throw if they're not running
      auth.useEmulator("10.0.2.2", 9099)
      firestore.useEmulator("10.0.2.2", 8080)

      Log.d("MainActivity", "Firebase emulators configured successfully")
    } catch (e: IllegalStateException) {
      // Emulators already configured - this is normal
      Log.d("MainActivity", "Firebase emulators already configured")
    } catch (e: Exception) {
      // Emulators not available - continue with production Firebase
      Log.d(
          "MainActivity",
          "Firebase emulators not available, using production Firebase: ${e.message}")
      // This is fine - tests can still run against production (but be careful with real data)
    }

    val firestoreRepo = FirestoreRepository(firestore)
    val authRepo = AuthRepoFirebase(auth = auth, firestoreRepository = firestoreRepo)

    val viewModelFactory =
        object : ViewModelProvider.Factory {
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return AuthViewModel(authRepo) as T
          }
        }

    val authViewModel: AuthViewModel =
        ViewModelProvider(this, viewModelFactory)[AuthViewModel::class.java]

    setContent {
      AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) { AuthNavigation(viewModel = authViewModel) }
      }
    }
  }
}

@Composable
private fun AuthNavigation(viewModel: AuthViewModel) {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = "sign_in") {
    composable("sign_in") { SignInScreen(navController = navController, viewModel = viewModel) }

    composable("sign_up") { SignUpScreen(navController = navController, viewModel = viewModel) }
  }
}
