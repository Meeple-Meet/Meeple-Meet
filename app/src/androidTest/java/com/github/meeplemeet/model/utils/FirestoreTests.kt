package com.github.meeplemeet.model.utils

import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Before
import org.junit.BeforeClass

var firestoreEmulatorLaunched = false
var authEmulatorLaunched = false

open class FirestoreTests {
  lateinit var viewModels: List<FirestoreViewModel>

  companion object {
    @BeforeClass
    @JvmStatic
    fun globalSetUp() {
      if (!firestoreEmulatorLaunched) {
        firestoreEmulatorLaunched = true
        FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
      }
      if (!authEmulatorLaunched) {
        authEmulatorLaunched = true
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
      }
    }
  }

  @Before
  fun setUp() {
    viewModels = List(5) { FirestoreViewModel() }
  }
}
