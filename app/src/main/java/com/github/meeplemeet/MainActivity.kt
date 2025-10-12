package com.github.meeplemeet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.github.meeplemeet.model.systems.AuthRepoFirebase
import com.github.meeplemeet.model.viewmodels.AuthViewModel
import com.github.meeplemeet.ui.SignInScreen
import com.github.meeplemeet.ui.theme.AppTheme

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
        Surface(modifier = Modifier.fillMaxSize()) {
          SignInScreen(AuthViewModel(AuthRepoFirebase()))
        }
      }
    }
  }
}
