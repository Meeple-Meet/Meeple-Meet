package com.github.meeplemeet

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.ui.SignInScreen
import com.github.meeplemeet.ui.SignUpScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.ktx.Firebase

/**
 * `MainActivity` is the entry point of the application. It sets up the content view with the
 * `onCreate` methods. You can run the app by running the `app` configuration in Android Studio. NB:
 * Make sure you have an Android emulator running or a physical device connected.
 */
class MainActivity : ComponentActivity() {
  // Disable emulators to use real Firebase backend
  private val USE_AUTH_EMULATOR = true
  private val USE_FIRESTORE_EMULATOR = true

  private lateinit var auth: FirebaseAuth
  private val viewModel = AuthViewModel()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    auth = Firebase.auth

    try {
      if (USE_AUTH_EMULATOR) {
        auth.useEmulator("10.0.2.2", 9099)
        Log.d("Firebase", "Using Auth emulator")
      }
      val firestore = FirebaseFirestore.getInstance()
      if (USE_FIRESTORE_EMULATOR) {
        firestore.useEmulator("10.0.2.2", 8080)
        Log.d("Firebase", "Using Firestore emulator")
      }
      firestore.firestoreSettings =
          FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
      Log.d("Firebase", "Firebase setup complete")
    } catch (e: Exception) {
      Log.e("Firebase", "Firebase setup error: ${e.message}")
    }

    setContent {
      val navController = rememberNavController()

      Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = "SignInScreen") {
          composable("SignInScreen") {
            SignInScreen(navController = navController, viewModel = viewModel)
          }
          composable("SignUpScreen") {
            SignUpScreen(navController = navController, viewModel = viewModel)
          }
          composable("HomeScreen") {
            Surface(modifier = Modifier.fillMaxSize()) {
              Text(text = "You are signed in!", style = MaterialTheme.typography.headlineSmall)
            }
          }
        }
      }
    }
  }
}
