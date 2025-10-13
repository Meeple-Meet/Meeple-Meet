package com.github.meeplemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.meeplemeet.model.repositories.AuthRepository
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.ui.SignInScreen
import com.github.meeplemeet.ui.theme.AppTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

object FirebaseProvider {
  val db: FirebaseFirestore by lazy { Firebase.firestore }
  val auth: FirebaseAuth by lazy { Firebase.auth }
}

/**
 * `MainActivity` is the entry point of the application. It sets up the content view with the
 * `onCreate` methods. You can run the app by running the `app` configuration in Android Studio. NB:
 * Make sure you have an Android emulator running or a physical device connected.
 */
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      AppTheme {
        Surface(modifier = Modifier.fillMaxSize()) { SignInScreen(AuthViewModel(AuthRepository())) }
      }
    }
  }
}
