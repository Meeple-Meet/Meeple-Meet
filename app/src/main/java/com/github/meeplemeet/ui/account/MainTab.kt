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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.CreateAccountViewModel
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.images.ImageFileUtils
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PublicInfoTestTags {

  // ROOT
  const val PUBLIC_INFO = "public_info_root"

  // ------------------------------------------------------------
  // SECTION 1 — Avatar
  // ------------------------------------------------------------
  const val AVATAR_CONTAINER = "public_info_avatar_container"
  const val AVATAR_IMAGE = "public_info_avatar_image"
  const val AVATAR_PLACEHOLDER = "public_info_avatar_placeholder"
  const val AVATAR_EDIT_ICON = "public_info_avatar_edit_icon"

  // Avatar Chooser Dialog
  const val AVATAR_CHOOSER_DIALOG = "avatar_chooser_dialog"
  const val AVATAR_CHOOSER_CAMERA = "avatar_chooser_camera"
  const val AVATAR_CHOOSER_GALLERY = "avatar_chooser_gallery"
  const val AVATAR_CHOOSER_REMOVE = "avatar_chooser_remove"
  const val AVATAR_CHOOSER_CANCEL = "avatar_chooser_cancel"

  // Permission Denied Alert
  const val CAMERA_PERMISSION_DIALOG = "camera_permission_dialog"
  const val CAMERA_PERMISSION_OK = "camera_permission_ok"

  // ------------------------------------------------------------
  // SECTION 2 — Action Buttons
  // ------------------------------------------------------------
  const val ACTIONS_CONTAINER = "public_info_actions"

  const val ACTION_FRIENDS = "action_friends"
  const val ACTION_NOTIFICATIONS = "action_notifications"
  const val ACTION_LOGOUT = "action_logout"

  // ------------------------------------------------------------
  // SECTION 3 — Inputs
  // ------------------------------------------------------------

  // USERNAME
  const val INPUT_USERNAME = "input_username"
  const val ERROR_USERNAME = "error_username_blank"

  // HANDLE
  const val INPUT_HANDLE = "input_handle"
  const val ERROR_HANDLE = "error_handle_message"

  // DESCRIPTION
  const val INPUT_DESCRIPTION = "input_description"

  // ------------------------------------------------------------
  // TOAST (email verification toast inside EmailSection but triggered here)
  // ------------------------------------------------------------
  const val GLOBAL_TOAST = "global_toast"
}

object PrivateInfoTestTags {

  // ------------------------------------------------------------
  // PRIVATE INFO ROOT
  // ------------------------------------------------------------
  const val PRIVATE_INFO = "private_info_root"
  const val PRIVATE_INFO_TITLE = "private_info_title"

  // ------------------------------------------------------------
  // EMAIL SECTION
  // ------------------------------------------------------------
  const val EMAIL_SECTION = "email_section"

  const val EMAIL_INPUT = "email_input"
  const val EMAIL_VERIFIED_LABEL = "email_verified_label"
  const val EMAIL_NOT_VERIFIED_LABEL = "email_not_verified_label"

  const val EMAIL_SEND_BUTTON = "email_send_verification_btn"
  const val EMAIL_TOAST = "email_section_toast"

  // ------------------------------------------------------------
  // ROLES SECTION
  // ------------------------------------------------------------
  const val ROLES_SECTION_TITLE = "roles_section_title"

  const val ROLE_SHOP_CHECKBOX = "role_checkbox_shop_owner"
  const val ROLE_SPACE_CHECKBOX = "role_checkbox_space_renter"

  // Confirmation dialog
  const val ROLE_DIALOG = "roles_remove_dialog"
  const val ROLE_DIALOG_CONFIRM = "roles_remove_dialog_confirm"
  const val ROLE_DIALOG_CANCEL = "roles_remove_dialog_cancel"
  const val ROLE_DIALOG_TEXT = "roles_remove_dialog_text"
}

@Composable
fun MainTab(
    viewModel: ProfileScreenViewModel = viewModel(),
    account: Account,
    onFriendsClick: (account: Account) -> Unit,
    onNotificationClick: (account: Account) -> Unit,
    onSignOut: () -> Unit
) {

  val focusManager = LocalFocusManager.current
  Column(
      modifier =
          Modifier.fillMaxSize()
              .padding(Dimensions.Padding.xxLarge)
              .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
              .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge),
      horizontalAlignment = Alignment.CenterHorizontally) {
        PublicInfo(
            account = account,
            viewModel = viewModel,
            onFriendsClick = onFriendsClick,
            onNotificationClick = onNotificationClick,
            onSignOut = onSignOut)

        PrivateInfo(account = account, viewModel = viewModel)

        var pref by remember { mutableStateOf(NotificationPreference.EVERYONE) }

        NotificationSettingsSection(preference = pref, onPreferenceChange = { pref = it })
      }
}

//////////////////////////////////////////////////////////////////////////////////////
// PUBLIC INFO SECTION
/////////////////////////////////////////////////////////////////////////////////////

@Composable
fun PublicInfo(
    account: Account,
    viewModel: ProfileScreenViewModel,
    onFriendsClick: (account: Account) -> Unit,
    onNotificationClick: (account: Account) -> Unit,
    onSignOut: () -> Unit
) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(AppColors.secondary)
              .testTag(PublicInfoTestTags.PUBLIC_INFO)) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.large),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally) {
              Row(
                  modifier = Modifier.fillMaxWidth().padding(start = Dimensions.Padding.xLarge),
                  verticalAlignment = Alignment.CenterVertically) {

                    // 🔹 SECTION 1: Avatar
                    DisplayAvatar(viewModel, account)

                    Spacer(modifier = Modifier.weight(1f))

                    // 🔹 SECTION 2: Action buttons
                    PublicInfoActions(
                        account = account,
                        viewModel = viewModel,
                        onFriendsClick = onFriendsClick,
                        onNotificationClick = onNotificationClick,
                        onSignOut = onSignOut)
                  }

              // 🔹 SECTION 3: Input fields
              PublicInfoInputs(account = account, viewModel = viewModel)
            }
      }
}

@Composable
fun PublicInfoActions(
    account: Account,
    viewModel: ProfileScreenViewModel,
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
              // Friends Button
              Button(
                  modifier = Modifier.size(56.dp).testTag(PublicInfoTestTags.ACTION_FRIENDS),
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

              // Notifications Button
              Button(
                  modifier = Modifier.size(56.dp).testTag(PublicInfoTestTags.ACTION_NOTIFICATIONS),
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

        // Logout Button
        Button(
            onClick = {
              onSignOut()
              viewModel.signOut()
            },
            modifier =
                Modifier.width(140.dp).height(46.dp).testTag(PublicInfoTestTags.ACTION_LOGOUT),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.textIconsFade,
                    disabledContainerColor = AppColors.textIconsFade,
                    contentColor = Color.Transparent,
                    disabledContentColor = Color.Transparent),
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(4.dp)) {
              Text("Logout", color = AppColors.primary)
            }
      }
}

@Composable
fun PublicInfoInputs(account: Account, viewModel: ProfileScreenViewModel) {
  var name by remember { mutableStateOf(account.name) }
  var desc by remember { mutableStateOf(account.description ?: "") }

  // NAME VALIDATION
  val nameError = name.isBlank()

  FocusableInputField(
      label = { Text("Username") },
      modifier = Modifier.testTag(PublicInfoTestTags.INPUT_USERNAME),
      value = name,
      onValueChange = { name = it },
      isError = nameError,
      onFocusChanged = { focused ->
        if (!focused && !nameError) {
          viewModel.setAccountName(account, name)
        }
      })
  if (nameError) {
    Text(
        text = "Username cannot be blank.",
        color = AppColors.negative,
        style = MaterialTheme.typography.bodySmall,
        modifier =
            Modifier.padding(start = 16.dp, top = 4.dp).testTag(PublicInfoTestTags.ERROR_USERNAME))
  }

  // HANDLE VALIDATION
  val errorMsg by viewModel.errorMessage.collectAsState()
  var handle by remember { mutableStateOf(account.handle) }
  var showErrors by remember { mutableStateOf(false) }
  val errorHandle = showErrors && errorMsg.isNotBlank() && handle != account.handle

  FocusableInputField(
      value = handle,
      modifier = Modifier.testTag(PublicInfoTestTags.INPUT_HANDLE),
      onValueChange = {
        handle = it
        if (it.isNotBlank()) {
          showErrors = true
          viewModel.checkHandleAvailable(it)
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
      onFocusChanged = { focused ->
        if (!focused && !errorHandle) viewModel.setAccountHandle(account, handle)
      })

  if (errorHandle) {
    Text(
        text = errorMsg,
        color = AppColors.negative,
        style = MaterialTheme.typography.bodySmall,
        modifier =
            Modifier.padding(start = 16.dp, top = 4.dp).testTag(PublicInfoTestTags.ERROR_HANDLE))
  }

  // DESCRIPTION
  FocusableInputField(
      label = { Text("Description") },
      modifier = Modifier.testTag(PublicInfoTestTags.INPUT_DESCRIPTION),
      value = desc,
      onValueChange = { desc = it },
      singleLine = false,
      onFocusChanged = { focused -> if (!focused) viewModel.setAccountDescription(account, desc) })

  Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
}

@Composable
fun DisplayAvatar(viewModel: ProfileScreenViewModel, account: Account) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showChooser by remember { mutableStateOf(false) }
  var isSending by remember { mutableStateOf(false) }
  var showPermissionDenied by remember { mutableStateOf(false) }

  // --- Upload function ---
  val setPhoto: suspend (String) -> Unit = { path ->
    isSending = true
    try {

      viewModel.setAccountPhoto(account, context, path)
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
      modifier =
          Modifier.size(130.dp)
              .clickable { showChooser = true }
              .testTag(PublicInfoTestTags.AVATAR_CONTAINER),
      contentAlignment = Alignment.TopEnd) {
        if (account.photoUrl.isNullOrBlank()) {
          Box(
              modifier =
                  Modifier.size(130.dp)
                      .clip(CircleShape)
                      .background(AppColors.textIconsFade)
                      .testTag(PublicInfoTestTags.AVATAR_PLACEHOLDER),
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
              modifier =
                  Modifier.size(130.dp).clip(CircleShape).testTag(PublicInfoTestTags.AVATAR_IMAGE))
        }

        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit image",
            tint = Color.White,
            modifier =
                Modifier.size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .padding(4.dp)
                    .testTag(PublicInfoTestTags.AVATAR_EDIT_ICON))
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
            viewModel.removeAccountPhoto(account, context)
          }
        })
  }

  // --- Permission denied ---
  if (showPermissionDenied) {
    AlertDialog(
        modifier = Modifier.testTag(PublicInfoTestTags.CAMERA_PERMISSION_DIALOG),
        onDismissRequest = { showPermissionDenied = false },
        title = { Text(text = "Permission denied") },
        text = { Text(text = "Camera access is required to take a profile picture.") },
        confirmButton = {
          TextButton(
              onClick = { showPermissionDenied = false },
              modifier = Modifier.testTag(PublicInfoTestTags.CAMERA_PERMISSION_OK)) {
                Text(text = "OK")
              }
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
            Modifier.clip(RoundedCornerShape(16.dp))
                .background(AppColors.primary)
                .padding(20.dp)
                .testTag(PublicInfoTestTags.AVATAR_CHOOSER_DIALOG)) {
          Column(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxWidth()) {
                Text(text = "Change profile picture", style = MaterialTheme.typography.titleMedium)
                Text(text = "Choose a source", style = MaterialTheme.typography.bodyMedium)

                Button(
                    onClick = onCamera,
                    modifier =
                        Modifier.fillMaxWidth().testTag(PublicInfoTestTags.AVATAR_CHOOSER_CAMERA)) {
                      Text(text = "Camera")
                    }
                Button(
                    onClick = onGallery,
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(PublicInfoTestTags.AVATAR_CHOOSER_GALLERY)) {
                      Text(text = "Gallery")
                    }
                if (onRemove != null) {
                  Button(
                      onClick = onRemove,
                      modifier =
                          Modifier.fillMaxWidth().testTag(PublicInfoTestTags.AVATAR_CHOOSER_REMOVE),
                      colors =
                          ButtonDefaults.buttonColors(
                              containerColor = AppColors.negative,
                              contentColor = AppColors.textIcons)) {
                        Text(text = "Remove Photo")
                      }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier.fillMaxWidth().testTag(PublicInfoTestTags.AVATAR_CHOOSER_CANCEL)) {
                      Text(text = "Cancel")
                    }
              }
        }
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// PRIVATE INFO SECTION
/////////////////////////////////////////////////////////////////////////////////////

@Composable
fun PrivateInfo(account: Account, viewModel: ProfileScreenViewModel) {

  val uiState by viewModel.uiState.collectAsState()

  var email by remember { mutableStateOf(account.email) }
  val isVerified = uiState.isEmailVerified

  Box(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(AppColors.secondary)
              .testTag(PrivateInfoTestTags.PRIVATE_INFO)) {
        Column(
            modifier = Modifier.padding(Dimensions.Padding.xxxLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally) {

              // TITLE
              Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Private Info",
                    modifier = Modifier.testTag(PrivateInfoTestTags.PRIVATE_INFO_TITLE),
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
                      viewModel.setAccountEmail(account, email)
                    }
                  },
                  onSendVerification = {
                    viewModel.sendVerificationEmail()
                    viewModel.refreshEmailVerificationStatus()
                  })

              // ROLES SECTION
              RolesSection(account = account, createAccountViewModel = viewModel)
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
              .padding(14.dp)
              .testTag(PrivateInfoTestTags.EMAIL_SECTION)) {
        Column {
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
                            modifier =
                                Modifier.padding(top = 4.dp)
                                    .testTag(PrivateInfoTestTags.EMAIL_SEND_BUTTON),
                            onClick = {
                              onSendVerification()
                              toast = ToastData("Sent")
                            }) {
                              Icon(
                                  imageVector = Icons.AutoMirrored.Filled.Send,
                                  contentDescription = "Send verification email",
                                  tint = AppColors.textIcons)
                            }
                      }
                    },
                    onFocusChanged = onFocusChanged,
                    modifier = Modifier.weight(1f).testTag(PrivateInfoTestTags.EMAIL_INPUT))
              }

          Row {
            if (isVerified) {
              Text(
                  text = "Email Verified",
                  color = AppColors.affirmative,
                  modifier = Modifier.testTag(PrivateInfoTestTags.EMAIL_VERIFIED_LABEL))
            } else {
              Text(
                  text = "Email Not Verified",
                  color = AppColors.negative,
                  modifier = Modifier.testTag(PrivateInfoTestTags.EMAIL_NOT_VERIFIED_LABEL))
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
  Box(modifier = Modifier.fillMaxSize().testTag(PrivateInfoTestTags.EMAIL_TOAST)) {
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
          modifier = Modifier.align(Alignment.BottomEnd).testTag(PublicInfoTestTags.GLOBAL_TOAST)) {
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
            modifier =
                Modifier.padding(top = 4.dp).testTag(PrivateInfoTestTags.ROLES_SECTION_TITLE))
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
      testTag = PrivateInfoTestTags.ROLE_SHOP_CHECKBOX)

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
      testTag = PrivateInfoTestTags.ROLE_SPACE_CHECKBOX)

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
            "Are you sure you want to stop renting out spaces? Your spaces will be removed from the platform.\nThis action is irreversible."
      }
  if (visible) {
    AlertDialog(
        containerColor = AppColors.primary,
        modifier = Modifier.testTag(PrivateInfoTestTags.ROLE_DIALOG),
        onDismissRequest = onCancel,
        title = null,
        text = {
          Text(
              text = informativeText,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.testTag(PrivateInfoTestTags.ROLE_DIALOG_TEXT))
        },
        confirmButton = {
          TextButton(
              modifier = Modifier.testTag(PrivateInfoTestTags.ROLE_DIALOG_CONFIRM),
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
              modifier = Modifier.testTag(PrivateInfoTestTags.ROLE_DIALOG_CANCEL),
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

//////////////////////////////////////////////////////////////////////////////////////
// NOTIFICATION SETTINGS SECTION
/////////////////////////////////////////////////////////////////////////////////////
enum class NotificationPreference {
  EVERYONE,
  FRIENDS_ONLY,
  NO_ONE
}

@Composable
fun NotificationSettingsSection(
    preference: NotificationPreference,
    onPreferenceChange: (NotificationPreference) -> Unit
) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
//              .padding(16.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(AppColors.secondary)
              .padding(20.dp)) {
        Text(text = "Notification settings", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(12.dp))

        NotificationOptionRow(
            label = "Accept notifications from everyone",
            selected = preference == NotificationPreference.EVERYONE,
            onClick = { onPreferenceChange(NotificationPreference.EVERYONE) })

        NotificationOptionRow(
            label = "Accept notifications from friends only",
            selected = preference == NotificationPreference.FRIENDS_ONLY,
            onClick = { onPreferenceChange(NotificationPreference.FRIENDS_ONLY) })

        NotificationOptionRow(
            label = "Accept notifications from no one",
            selected = preference == NotificationPreference.NO_ONE,
            onClick = { onPreferenceChange(NotificationPreference.NO_ONE) })
      }
}

@Composable
private fun NotificationOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(text = label)
      }
}
