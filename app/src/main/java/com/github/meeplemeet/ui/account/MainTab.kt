package com.github.meeplemeet.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.CreateAccountViewModel
import com.github.meeplemeet.model.auth.AuthenticationViewModel
import com.github.meeplemeet.model.images.ImageFileUtils
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainTab(
    authViewModel: AuthenticationViewModel = viewModel(),
    createAccountViewModel: CreateAccountViewModel = viewModel(),
    account: Account,
    onFriendsClick: (account: Account) -> Unit,
    onNotificationClick: (account: Account) -> Unit,
    onSignOut: () -> Unit
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(ProfileScreenUi.xxLargePadding)
              .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(ProfileScreenUi.extraLargeSpacing),
      horizontalAlignment = Alignment.CenterHorizontally) {
        PublicInfo(
            account = account,
            authViewModel = authViewModel,
            createAccountViewModel = createAccountViewModel,
            onFriendsClick = onFriendsClick,
            onNotificationClick = onNotificationClick,
            onSignOut = onSignOut)

        PrivateInfo(
            account = account,
            authViewModel = authViewModel,
            createAccountViewModel = createAccountViewModel)
      }
}

@Composable
fun PublicInfo(
    account: Account,
    authViewModel: AuthenticationViewModel,
    createAccountViewModel: CreateAccountViewModel,
    onFriendsClick: (account: Account) -> Unit,
    onNotificationClick: (account: Account) -> Unit,
    onSignOut: () -> Unit
) {
  Box(
      modifier =
          Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.secondary)) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.large),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally) {
              Row(
                  modifier = Modifier.fillMaxWidth().padding(start = Dimensions.Padding.xLarge),
                  verticalAlignment = Alignment.CenterVertically) {

                    // 🔹 SECTION 1: Avatar
                    DisplayAvatar(createAccountViewModel, account)

                    Spacer(modifier = Modifier.weight(1f))

                    // 🔹 SECTION 2: Action buttons
                    PublicInfoActions(
                        account = account,
                        authViewModel = authViewModel,
                        onFriendsClick = onFriendsClick,
                        onNotificationClick = onNotificationClick,
                        onSignOut = onSignOut)
                  }

              // 🔹 SECTION 3: Input fields
              PublicInfoInputs(account = account, createAccountViewModel = createAccountViewModel)
            }
      }
}

@Composable
fun PublicInfoActions(
    account: Account,
    authViewModel: AuthenticationViewModel,
    onFriendsClick: (Account) -> Unit,
    onNotificationClick: (Account) -> Unit,
    onSignOut: () -> Unit
) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Button(
                  modifier = Modifier.size(56.dp),
                  onClick = { onFriendsClick(account) },
                  shape = RoundedCornerShape(18.dp),
                  contentPadding = PaddingValues(0.dp),
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = AppColors.secondary, contentColor = AppColors.textIcons),
                  elevation = ButtonDefaults.buttonElevation(4.dp)) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Friends",
                        modifier = Modifier.size(28.dp))
                  }

              Button(
                  modifier = Modifier.size(56.dp),
                  onClick = { onNotificationClick(account) },
                  shape = RoundedCornerShape(18.dp),
                  contentPadding = PaddingValues(0.dp),
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = AppColors.secondary, contentColor = AppColors.textIcons),
                  elevation = ButtonDefaults.buttonElevation(4.dp)) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = "Notifications",
                        modifier = Modifier.size(28.dp))
                  }
            }

        Button(
            onClick = {
              onSignOut()
              authViewModel.signOut()
            },
            modifier = Modifier.width(140.dp).height(46.dp),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(4.dp)) {
              Text("Logout")
            }
      }
}

@Composable
fun PublicInfoInputs(account: Account, createAccountViewModel: CreateAccountViewModel) {
  var name by remember { mutableStateOf(account.name) }
  var desc by remember { mutableStateOf(account.description ?: "") }

  // NAME VALIDATION
  val nameError = name.isBlank()

  FocusableInputField(
      label = { Text("Username") },
      value = name,
      onValueChange = { name = it },
      isError = nameError,
      onFocusChanged = { focused ->
        if (!focused && !nameError) {
          createAccountViewModel.setAccountName(account, name)
        }
      })
  if (nameError) {
    Text(
        text = "Username cannot be blank.",
        color = AppColors.negative,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp))
  }

  // HANDLE VALIDATION
  val errorMsg by createAccountViewModel.errorMessage.collectAsState()
  var handle by remember { mutableStateOf(account.handle) }
  var showErrors by remember { mutableStateOf(false) }
  val errorHandle = showErrors && errorMsg.isNotBlank() && handle != account.handle

  FocusableInputField(
      value = handle,
      onValueChange = {
        handle = it
        if (it.isNotBlank()) {
          showErrors = true
          createAccountViewModel.checkHandleAvailable(it)
        } else {
          showErrors = false
        }
      },
      label = { Text("Handle") },
      leadingIcon = {
        Text(
            "@",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.textIcons,
            modifier = Modifier.padding(start = 8.dp))
      },
      singleLine = true,
      isError = errorHandle,
      onFocusChanged = {focused ->
          if (!focused && !errorHandle)
              createAccountViewModel.setAccountHandle(account, handle)
      })

  if (errorHandle) {
    Text(
        text = errorMsg,
        color = AppColors.negative,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp))
  }

  // DESCRIPTION
  FocusableInputField(
      label = { Text("Description") },
      value = desc,
      onValueChange = { desc = it },
      singleLine = false,
      onFocusChanged = { focused ->
        if (!focused) createAccountViewModel.setAccountDescription(account, desc)
      })

  Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
}

@Composable
fun DisplayAvatar(createAccountViewModel: CreateAccountViewModel, account: Account) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showChooser by remember { mutableStateOf(false) }
  var isSending by remember { mutableStateOf(false) }
  var showPermissionDenied by remember { mutableStateOf(false) }

  // --- Upload function ---
  val setPhoto: suspend (String) -> Unit = { path ->
    isSending = true
    try {
      createAccountViewModel.setAccountPhotoUrl(account, newPhotoUrl = path)
    } finally {
      isSending = false
    }
  }

  // --- Camera launcher ---
  val cameraLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
          scope.launch {
            val path = ImageFileUtils.saveBitmapToCache(context, bitmap)
            setPhoto(path)
          }
        }
      }

  // --- Camera permission ---
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
          cameraLauncher.launch(null)
        } else {
          showPermissionDenied = true
        }
      }

  // --- Gallery launcher ---
  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
          scope.launch {
            val path = ImageFileUtils.cacheUriToFile(context, uri)
            setPhoto(path)
          }
        }
      }

  // --- Avatar display ---
  Box(
      modifier = Modifier.size(130.dp).clickable { showChooser = true },
      contentAlignment = Alignment.TopEnd) {
        if (account.photoUrl.isNullOrBlank()) {
          Box(
              modifier =
                  Modifier.size(130.dp).clip(CircleShape).background(AppColors.textIconsFade),
              contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Placeholder Image",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(60.dp))
              }
        } else {
          Image(
              painter = rememberAsyncImagePainter(account.photoUrl),
              contentDescription = "Profile Image",
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(130.dp).clip(CircleShape))
        }

        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit image",
            tint = Color.White,
            modifier =
                Modifier.size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(4.dp))
      }

  // --- Chooser dialog ---
  if (showChooser) {
    AvatarChooserDialog(
        onDismiss = { showChooser = false },
        onCamera = {
          showChooser = false
          cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        },
        onGallery = {
          showChooser = false
          galleryLauncher.launch("image/*")
        },
        onRemove = {
          if (!account.photoUrl.isNullOrBlank()) {
            showChooser = false
            scope.launch { setPhoto("") }
          } else null
        })
  }

  // --- Permission denied ---
  if (showPermissionDenied) {
    AlertDialog(
        onDismissRequest = { showPermissionDenied = false },
        title = { Text(text = "Permission denied") },
        text = { Text(text = "Camera access is required to take a profile picture.") },
        confirmButton = {
          TextButton(onClick = { showPermissionDenied = false }) { Text(text = "OK") }
        })
  }
}

@Composable
fun AvatarChooserDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
  Dialog(onDismissRequest = onDismiss) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(16.dp)).background(AppColors.primary).padding(20.dp)) {
          Column(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth()) {
                Text(text = "Change profile picture", style = MaterialTheme.typography.titleMedium)
                Text(text = "Choose a source", style = MaterialTheme.typography.bodyMedium)

                Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
                  Text(text = "Camera")
                }
                Button(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
                  Text(text = "Gallery")
                }
                if (onRemove != null) {
                  Button(
                      onClick = onRemove,
                      modifier = Modifier.fillMaxWidth(),
                      colors =
                          ButtonDefaults.buttonColors(
                              containerColor = AppColors.negative,
                              contentColor = AppColors.textIcons)) {
                        Text(text = "Remove Photo")
                      }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                  Text(text = "Cancel")
                }
              }
        }
  }
}

@Composable
fun PrivateInfo(
    account: Account,
    authViewModel: AuthenticationViewModel,
    createAccountViewModel: CreateAccountViewModel
) {
  val uiState by authViewModel.uiState.collectAsState()

  var email by remember { mutableStateOf(account.email) }
  val isVerified = uiState.isEmailVerified

  Box(
      modifier =
          Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppColors.secondary)) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.xxxLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally) {

              // TITLE
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Private Info",
                    style = MaterialTheme.typography.headlineSmall,
                )
              }

              // EMAIL SECTION
              EmailSection(
                  email = email,
                  isVerified = isVerified,
                  onEmailChange = { newEmail -> email = newEmail },
                  onFocusChanged = { focused ->
                    if (!focused) {
                      createAccountViewModel.setAccountEmail(account, email)
                    }
                  },
                  onSendVerification = {
                    authViewModel.sendVerificationEmail()
                    authViewModel.refreshEmailVerificationStatus()
                  })

              // ROLES SECTION
              RolesSection(account = account, createAccountViewModel = createAccountViewModel)
              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
            }
      }
}

@Composable
fun EmailSection(
    email: String,
    isVerified: Boolean,
    onEmailChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSendVerification: () -> Unit
) {
  var toast by remember { mutableStateOf<ToastData?>(null) }

  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(AppColors.secondary)
              .padding(14.dp)) {
        Column() {
          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically) {
                FocusableInputField(
                    label = { Text("Email") },
                    value = email,
                    onValueChange = onEmailChange,
                    trailingIcon = {
                      if (!isVerified) {
                        IconButton(
                            modifier = Modifier.padding(top = 4.dp),
                            onClick = {
                              onSendVerification()
                              toast = ToastData("Sent")
                            }) {
                              Icon(
                                  imageVector = Icons.Default.Send,
                                  contentDescription = "Send verification email",
                                  tint = AppColors.textIcons)
                            }
                      }
                    },
                    onFocusChanged = onFocusChanged,
                    modifier = Modifier.weight(1f))
              }

          Row() {
            if (isVerified) {
              Text("Email Verified", color = AppColors.affirmative)
            } else {
              Text("Email Not Verified", color = AppColors.negative)
            }

            ToastHost(toast = toast, onToastFinished = { toast = null })
          }
        }
      }
}

data class ToastData(
    val message: String,
    val id: Long = System.currentTimeMillis() // unique per show
)

@Composable
fun ToastHost(toast: ToastData?, duration: Long = 1500L, onToastFinished: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize()) {
    toast?.let { data ->
      var visible by remember(data.id) { mutableStateOf(true) }

      LaunchedEffect(data.id) {
        visible = true
        delay(duration)
        visible = false
        onToastFinished()
      }

      AnimatedVisibility(
          visible = visible,
          enter = fadeIn() + scaleIn(),
          exit = fadeOut() + scaleOut(),
          modifier = Modifier.align(Alignment.BottomEnd)) {
            Box(
                modifier =
                    Modifier.background(
                            color = AppColors.textIconsFade, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 0.dp)) {
                  Text(text = data.message, color = Color.White, fontSize = 14.sp)
                }
          }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesSection(account: Account, createAccountViewModel: CreateAccountViewModel) {

  var isShopChecked by remember { mutableStateOf(account.shopOwner) }
  var isSpaceRented by remember { mutableStateOf(account.spaceRenter) }

  var showDialog by remember { mutableStateOf(false) }
  var pendingAction by remember { mutableStateOf<RoleAction?>(null) }

  Box(
      modifier = Modifier.fillMaxWidth().background(AppColors.secondary).padding(14.dp),
      contentAlignment = Alignment.CenterStart) {
        Text(
            "I also want to:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp))
      }

  RoleCheckBox(
      isChecked = isShopChecked,
      onCheckedChange = { checked ->
        if (!checked) {
          pendingAction = RoleAction.ShopOff
          showDialog = true
        } else {
          isShopChecked = true
          createAccountViewModel.setAccountRole(
              account, isShopOwner = true, isSpaceRenter = isSpaceRented)
        }
      },
      label = "Sell Items",
      description = "List your shop and games you sell",
      testTag = CreateAccountTestTags.CHECKBOX_OWNER)

  RoleCheckBox(
      isChecked = isSpaceRented,
      onCheckedChange = { checked ->
        if (!checked) {
          pendingAction = RoleAction.SpaceOff
          showDialog = true
        } else {
          isSpaceRented = true
          createAccountViewModel.setAccountRole(
              account, isShopOwner = isShopChecked, isSpaceRenter = true)
        }
      },
      label = "Rent out spaces",
      description = "Offer your play spaces for other players to book.",
      testTag = CreateAccountTestTags.CHECKBOX_RENTER)

  // CONFIRMATION DIALOG
  if (showDialog && pendingAction != null) {
    RemoveCatalogDialog(
        visible = true,
        action = pendingAction!!, // It's fine to use !! here as we check for null above
        onConfirm = {
          when (pendingAction) {
            RoleAction.ShopOff -> {
                // Todo: Delete user's shops from the platform
              isShopChecked = false
              createAccountViewModel.setAccountRole(
                  account, isShopOwner = false, isSpaceRenter = isSpaceRented)
            }
            RoleAction.SpaceOff -> {
                // Todo: Delete user's spaces from the platform
              isSpaceRented = false
              createAccountViewModel.setAccountRole(
                  account, isShopOwner = isShopChecked, isSpaceRenter = false)
            }
            else -> {}
          }
          showDialog = false
          pendingAction = null
        },
        onCancel = {
          showDialog = false
          pendingAction = null
        })
  }
}

private enum class RoleAction {
  ShopOff,
  SpaceOff
}

@Composable
private fun RemoveCatalogDialog(
    visible: Boolean,
    action: RoleAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
  val informativeText =
      when (action) {
        RoleAction.ShopOff ->
            "Are you sure you want to stop selling items? Your shops will be removed from the platform.\nThis action is irreversible."
        RoleAction.SpaceOff ->
            "Are you sure you want to stop renting out spaces? Your listings will be removed from the platform.\nThis action is irreversible."
      }
  if (visible) {
    AlertDialog(
        containerColor = AppColors.primary,
        onDismissRequest = onCancel,
        title = null,
        text = { Text(text = informativeText, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
          TextButton(
              onClick = onConfirm,
              colors =
                  ButtonColors(
                      containerColor = AppColors.divider,
                      contentColor = AppColors.textIcons,
                      disabledContainerColor = AppColors.divider,
                      disabledContentColor = AppColors.textIcons)) {
                Text("Confirm")
              }
        },
        dismissButton = {
          TextButton(
              onClick = onCancel,
              colors =
                  ButtonColors(
                      containerColor = AppColors.affirmative,
                      disabledContainerColor = AppColors.affirmative,
                      contentColor = AppColors.textIcons,
                      disabledContentColor = AppColors.textIcons)) {
                Text("Cancel")
              }
        },
        shape = RoundedCornerShape(16.dp))
  }
}
