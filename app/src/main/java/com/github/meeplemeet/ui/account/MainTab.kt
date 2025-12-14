package com.github.meeplemeet.ui.account
// AI was used for this file

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.FirebaseProvider
import com.github.meeplemeet.R
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.account.NotificationSettings
import com.github.meeplemeet.model.account.ProfileScreenViewModel
import com.github.meeplemeet.model.images.ImageFileUtils
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.space_renter.SpaceRenter
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MainTabTestTags {
  const val CONTENT_SCROLL = "main_tab_content_scroll"
}

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
  const val ACTION_FRIENDS = "action_friends"
  const val ACTION_NOTIFICATIONS = "action_notifications"
  const val NOTIF_COUNT = "notif_count"
  const val ACTION_LOGOUT = "Logout Button"

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
  // SECTION 4 — Businesses
  // ------------------------------------------------------------
  const val BUSINESS_CARD = "businesses_section"
}

object EmailVerificationTestTags {
  const val VERIFICATION_SECTION = "verification_section"
  const val USER_EMAIL = "user_email"
  const val RESEND_MAIL_VERIFICATION_BTN = "resend_mail_verification_btn"
}

object PrivateInfoTestTags {

  // ------------------------------------------------------------
  // PRIVATE INFO ROOT
  // ------------------------------------------------------------
  const val COLLAPSABLE = "collapsable"

  // ------------------------------------------------------------
  // EMAIL SECTION
  // ------------------------------------------------------------
  const val EMAIL_SECTION = "email_section"
  const val EMAIL_INPUT = "email_input"

  const val EMAIL_SEND_BUTTON = "email_send_verification_btn"
  const val EMAIL_TOAST = "email_section_toast"
  const val EMAIL_ERROR_LABEL = "email_error_label"

  // ------------------------------------------------------------
  // ROLES SECTION
  // ------------------------------------------------------------
  const val ROLE_SHOP_CHECKBOX = "role_checkbox_shop_owner"
  const val ROLE_SPACE_CHECKBOX = "role_checkbox_space_renter"

  // Confirmation dialog
  const val ROLE_DIALOG = "roles_remove_dialog"
  const val ROLE_DIALOG_CONFIRM = "roles_remove_dialog_confirm"
  const val ROLE_DIALOG_CANCEL = "roles_remove_dialog_cancel"
  const val ROLE_DIALOG_TEXT = "roles_remove_dialog_text"
}

object NotificationsSectionTestTags {
  const val NOTIFICATION_SECTION_TITLE = "notification_section_title"
  const val RADIO_EVERYONE = "radio_everyone"
  const val RADIO_FRIENDS = "radio_friends"
  const val RADIO_NONE = "radio_none"
}

object EmailSectionTestTags {
  const val CHANGE_EMAIL_TITLE = "change_email_title"
  const val NEW_EMAIL_INPUT = "new_email_input"
  const val NEW_EMAIL_ERROR_LABEL = "new_email_error_label"
  const val NEW_EMAIL_VALID_LABEL = "new_email_valid_label"
  const val CONFIRM_EMAIL_INPUT = "confirm_email_input"
  const val EMAILS_DONT_MATCH_LABEL = "emails_dont_match_label"
  const val PASSWORD_INPUT = "password_input"
  const val CHANGE_EMAIL_BUTTON = "change_email_button"
}

object PreferencesSectionTestTags {
  const val PREFERENCES_SECTION_TITLE = "preferences_section_title"
  const val RADIO_LIGHT = "radio_light"
  const val RADIO_DARK = "radio_dark"
  const val RADIO_SYSTEM = "radio_system"
}

object DeleteAccSectionTestTags {
  const val BUTTON = "delete_acc_button"
  const val POPUP = "delete_acc_popup"
  const val CONFIRM = "delete_acc_popup_confirm"
  const val CANCEL = "delete_acc_popup_cancel"
}

object ProfileNavigationTestTags {
  const val SETTINGS_ROW_PREFERENCES = "settings_row_preferences"
  const val SETTINGS_ROW_NOTIFICATIONS = "settings_row_notifications"
  const val SETTINGS_ROW_BUSINESSES = "settings_row_businesses"
  const val SETTINGS_ROW_EMAIL = "settings_row_email"
  const val SUB_PAGE_BACK_BUTTON = "sub_page_back_button"
}

object MainTabUi {
  val ACTION_BUTTON_SIZE = 56.dp
  val LOGOUT_BUTTON_W = 125.dp
  val LOGOUT_BUTTON_H = 46.dp
  val AVATAR_SIZE = 130.dp
  val OFFSET_X = 4.dp
  val OFFSET_Y = (-4).dp
  val OFFSET_EMAIL = 35.dp
  val PADDING_BOTTOM_SCROLL = 110.dp
  const val MAX_NOTIF_COUNT = 9

  object Misc {
    const val DELETE_ACCOUNT = "Delete Account"
    const val DIALOG_TITLE = "Delete your account"
    const val DIALOG_DESC =
        "Do you really want to delete your account? All data related to your account will be erased"
    const val DIALOG_CONFIRM = "Confirm"
    const val DIALOG_CANCEL = "Cancel"
  }

  object PublicInfo {
    const val NOTIF_BTN_DESC = "Notifications"
    const val FRIENDS_BTN_DESC = "Friends"
    const val LOGOUT_BTN = "Logout"
    const val NAME_INPUT_FIELD = "Username"
    const val HANDLE_INPUT_FIELD = "Handle"
    const val DESC_INPUT_FIELD = "Description"
    const val NAME_INPUT_FIELD_ERR = "Username cannot be blank."
    const val HANDLE_PREFIX = "@"

    const val AVATAR_HOLDER_DESC = "Placeholder Image"
    const val AVATAR_IMAGE_DESC = "User's Image"
    const val AVATAR_EDIT_DESC = "Edit image"

    const val NO_PERMS_TITLE = "Permission denied"
    const val NO_PERMS_TEXT = "Camera access is required to take a profile picture"
    const val NO_PERMS_OK = "OK"

    const val AVATAR_DIALOG_TITLE = "Change Profile Picture"
    const val AVATAR_DIALOG_PROMPT = "Choose a source"

    const val AVATAR_CAMERA_OPT = "Camera"
    const val AVATAR_GALLERY_OPT = "Gallery"
    const val AVATAR_REMOVE_OPT = "Remove Photo"
    const val AVATAR_CANCEL_OPT = "Cancel"
  }

  object PrivateInfo {
    const val TITLE = "Private Info"
    const val EMAIL_INPUT_FIELD = "Email"

    const val TOAST_MSG = "Sent"
    const val SEND_ICON_DESC = "Resend Verification Email"
    const val EMAIL_INVALID_MSG = "Invalid Email Format"
    const val ROLES_TITLE = "Your Roles"
    const val SELL_ITEMS_LABEL = "Sell Items"
    const val SELL_ITEMS_DESC = "List your shop and games you sell"

    const val RENT_SPACES_LABEL = "Rent out Spaces"
    const val RENT_SPACES_DESC = "Offer your spaces for other players to book."
    const val DIALOG_CONFIRM = "Confirm"
    const val DIALOG_CANCEL = "Cancel"

    const val ROLE_ACTION_SHOP =
        "Are you sure you want to stop selling items? Your shops will be removed from the platform.\nThis action is irreversible."
    const val ROLE_ACTION_SPACE =
        "Are you sure you want to stop renting out spaces? Your spaces will be removed from the platform.\nThis action is irreversible."
  }

  object NotificationsSection {
    const val TITLE = "Notification Settings"
    const val OPT_EVERY = "Accept notifications from everyone."
    const val OPT_FRIENDS = "Accept notifications from friends only."
    const val OPT_NONE = "Accept notifications from no one."
  }

  object EmailSection {
    const val CHANGE_EMAIL_TITLE = "Change your email"
    const val NEW_EMAIL_INPUT_FIELD = "New Email"
    const val CONFIRM_EMAIL_INPUT_FIELD = "Confirm Email"
    const val NEW_EMAIL_INVALID_MSG = "Invalid Email Format"
    const val NEW_EMAIL_VALID_MSG = "Valid Email"
    const val EMAILS_DONT_MATCH_MSG = "Emails do not match"
    const val PASSWORD_INPUT_FIELD = "Password"
    const val CHANGE_EMAIL_BUTTON_TEXT = "Change Email"
  }

  object SettingRows {
    const val HEADER = "Settings"
    const val PREFERENCES = "Preferences"
    const val NOTIF = "Manage Notifications"
    const val BUSINESSES = "Manage Your Businesses"
    const val EMAIL = "Manage Your Email"
  }

  object PreferencesPage {
    const val HEADER = "Theme"
    const val OPT_LIGHT = "Light"
    const val OPT_DARK = "Dark"
    const val OPT_SYSTEM = "System Settings"
  }

  object Businesses {
    const val HEADER = "Your Businesses"
    const val TEXT_NO_ROLES = "You have no businesses. Select a role to get started!"
    const val TEXT_ROLES_NO_BUSINESS = "You have no businesses yet."
  }
}

sealed class ProfilePage {
  data object Main : ProfilePage()

  data object Preferences : ProfilePage()

  data object NotificationSettings : ProfilePage()

  data object Businesses : ProfilePage()

  data object Email : ProfilePage()
}

/**
 * Main entry composable
 *
 * @param viewModel VM used by this screen
 * @param account current user
 * @param onFriendsClick callback upon clicking on the friend's button
 * @param onNotificationClick callback upon clicking on notifications button
 * @param onSignOutOrDel callback upon signing out/deleting account
 * @param onDelete callback upon deleting account
 * @param onInputFocusChanged callback when input focus state changes
 */
@Composable
fun MainTab(
    viewModel: ProfileScreenViewModel,
    account: Account,
    onFriendsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSignOutOrDel: () -> Unit,
    onDelete: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit = {},
    onSpaceRenterClick: (String) -> Unit,
    onShopClick: (String) -> Unit
) {
  var currentPage by remember { mutableStateOf<ProfilePage>(ProfilePage.Main) }
  val online by OfflineModeManager.hasInternetConnection.collectAsStateWithLifecycle()
  val offlineData by OfflineModeManager.offlineModeFlow.collectAsStateWithLifecycle()

  val businesses by viewModel.businesses.collectAsState()
  val uiState by viewModel.uiState.collectAsState()

  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer =
        androidx.lifecycle.LifecycleEventObserver { _, event ->
          if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
            viewModel.loadAccountBusinesses(account)
          }
        }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  when (currentPage) {
    ProfilePage.Main ->
        MainTabContent(
            viewModel = viewModel,
            account = account,
            onFriendsClick = onFriendsClick,
            onNotificationClick = onNotificationClick,
            onSignOutOrDel = onSignOutOrDel,
            onDelete = onDelete,
            onNavigate = { page -> currentPage = page },
            onInputFocusChanged = onInputFocusChanged,
            online = online)
    ProfilePage.Preferences ->
        SubPageScaffold("Preferences", onBack = { currentPage = ProfilePage.Main }) {
          PreferencesPage(
              preference =
                  if (online) {
                    account.themeMode
                  } else {
                    offlineData.accounts[account.uid]?.second?.get(Account::themeMode.name)
                        as? ThemeMode ?: account.themeMode
                  },
              onPreferenceChange = {
                if (online) viewModel.setAccountTheme(account, it)
                else OfflineModeManager.setAccountChange(account, Account::themeMode.name, it)
              })
        }
    ProfilePage.NotificationSettings ->
        SubPageScaffold("Notifications", onBack = { currentPage = ProfilePage.Main }) {
          NotificationSettingsSection(
              preference =
                  if (online) {
                    account.notificationSettings
                  } else {
                    offlineData.accounts[account.uid]
                        ?.second
                        ?.get(Account::notificationSettings.name) as? NotificationSettings
                        ?: account.notificationSettings
                  },
              onPreferenceChange = {
                if (online) viewModel.setAccountNotificationSettings(account, it)
                else
                    OfflineModeManager.setAccountChange(
                        account, Account::notificationSettings.name, it)
              })
        }
    ProfilePage.Businesses ->
        SubPageScaffold("Your Businesses", onBack = { currentPage = ProfilePage.Main }) {
          ManageBusinessesPage(
              viewModel, account, businesses, onSpaceRenterClick, onShopClick, online)
        }
    ProfilePage.Email ->
        SubPageScaffold("Email Settings", onBack = { currentPage = ProfilePage.Main }) {
          // Get email from Firebase Auth instead of Firestore account
          // Refresh whenever this page is composed or uiState changes
          val currentUser = FirebaseProvider.auth.currentUser
          val email = currentUser?.email ?: account.email

          // Refresh email and verification status when entering this section
          LaunchedEffect(currentPage) {
            if (currentPage == ProfilePage.Email) {
              viewModel.syncEmail()
              viewModel.refreshEmailVerificationStatus()
            }
          }

          EmailSection(
              email = email,
              online = online,
              onEmailChange = { /* Email field is disabled, this is not used */},
              onFocusChanged = { focused -> onInputFocusChanged(focused) },
              onChangeEmail = { newEmail, password -> viewModel.changeEmail(newEmail, password) },
              isLoading = uiState.isLoading,
              errorMsg = uiState.errorMsg,
              successMsg = uiState.successMsg)
        }
  }
}

/**
 * Handles the content of the preference sub-page
 *
 * @param preference User's theme preference
 * @param onPreferenceChange callback upon preference change
 */
@Composable
fun PreferencesPage(preference: ThemeMode, onPreferenceChange: (ThemeMode) -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = MainTabUi.PreferencesPage.HEADER,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(PreferencesSectionTestTags.PREFERENCES_SECTION_TITLE))

    RadioOptionRow(
        label = MainTabUi.PreferencesPage.OPT_LIGHT,
        selected = preference == ThemeMode.LIGHT,
        modifier = Modifier.testTag(PreferencesSectionTestTags.RADIO_LIGHT),
        onClick = { onPreferenceChange(ThemeMode.LIGHT) })

    RadioOptionRow(
        label = MainTabUi.PreferencesPage.OPT_DARK,
        selected = preference == ThemeMode.DARK,
        modifier = Modifier.testTag(PreferencesSectionTestTags.RADIO_DARK),
        onClick = { onPreferenceChange(ThemeMode.DARK) })

    RadioOptionRow(
        label = MainTabUi.PreferencesPage.OPT_SYSTEM,
        selected = preference == ThemeMode.SYSTEM_DEFAULT,
        modifier = Modifier.testTag(PreferencesSectionTestTags.RADIO_SYSTEM),
        onClick = { onPreferenceChange(ThemeMode.SYSTEM_DEFAULT) })
  }
}

/**
 * Handles the content of the manage businesses sub-page
 *
 * @param viewModel VM used by this screen
 * @param account current user
 * @param businesses pair of shops and space renters owned by the user
 * @param onSpaceRenterClick callback upon clicking on a space renter card
 * @param onShopClick callback upon clicking on a shop card
 */
@Composable
fun ManageBusinessesPage(
    viewModel: ProfileScreenViewModel,
    account: Account,
    businesses: Pair<List<Shop>, List<SpaceRenter>> = viewModel.businesses.collectAsState().value,
    onSpaceRenterClick: (String) -> Unit,
    onShopClick: (String) -> Unit,
    online: Boolean
) {
  RolesSection(account = account, viewModel = viewModel, businesses = businesses, online = online)
  Column(modifier = Modifier.fillMaxWidth().padding(Dimensions.Padding.large)) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.Padding.small),
        verticalAlignment = Alignment.CenterVertically) {
          Text(
              text = MainTabUi.Businesses.HEADER,
              style = MaterialTheme.typography.bodyLarge,
              fontSize = Dimensions.TextSize.largeHeading,
              modifier = Modifier.weight(1f))
        }

    if (hasNoRoles(account)) Text(text = MainTabUi.Businesses.TEXT_NO_ROLES)
    else if (businesses.second.isEmpty() && businesses.first.isEmpty())
        Text(text = MainTabUi.Businesses.TEXT_ROLES_NO_BUSINESS)
    else {
      Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
        // show all shops first
        businesses.first.forEach { shop ->
          ShopCard(shop = shop, onClick = { onShopClick(shop.id) })
        }
        // then show all space renters
        businesses.second.forEach { spaceRenter ->
          SpaceRenterCard(
              spaceRenter = spaceRenter, onClick = { onSpaceRenterClick(spaceRenter.id) })
        }
      }
    }
  }
}

/**
 * Helper function to know the user's roles
 *
 * @param account user to examine his roles
 */
private fun hasNoRoles(account: Account): Boolean {
  return !account.shopOwner && !account.spaceRenter
}

/**
 * Main tab of the profile screen. Displays all the account information that is important and
 * manageable by the user Also handles the navigation between the main and subpages
 *
 * @param viewModel the viewmodel used for this screen
 * @param account current user of the app
 * @param onFriendsClick callback to navigate to the friend's tab
 * @param onNotificationClick callback to navigate to the notification's tab
 * @param onSignOutOrDel callback upon signing out
 * @param onDelete callback upon account deletion
 * @param onNavigate callback upon navigation to a subpage
 * @param onInputFocusChanged callback when input focus state changes
 */
@Composable
fun MainTabContent(
    viewModel: ProfileScreenViewModel = viewModel(),
    account: Account,
    online: Boolean,
    onFriendsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSignOutOrDel: () -> Unit,
    onDelete: () -> Unit,
    onNavigate: (ProfilePage) -> Unit,
    onInputFocusChanged: (Boolean) -> Unit = {}
) {
  var showDelDialog by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val uiState by viewModel.uiState.collectAsState()
  var toast by remember { mutableStateOf<ToastData?>(null) }

  // Get current email (prefer auth email as it's the one being verified)
  val currentUser = FirebaseProvider.auth.currentUser
  val userEmail = currentUser?.email ?: account.email

  // Refresh email status on resume
  LaunchedEffect(Unit) { viewModel.refreshEmailVerificationStatus() }

  Column(
      modifier =
          Modifier.padding(Dimensions.Padding.xxLarge)
              .verticalScroll(rememberScrollState())
              .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
              .testTag(MainTabTestTags.CONTENT_SCROLL),
      horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth().testTag(PublicInfoTestTags.PUBLIC_INFO)) {
          Column(
              verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
              horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = Dimensions.Padding.xLarge),
                    verticalAlignment = Alignment.CenterVertically) {
                      DisplayAvatar(viewModel, account)

                      Spacer(modifier = Modifier.weight(1f))

                      PublicInfoActions(
                          account = account,
                          viewModel = viewModel,
                          onFriendsClick = onFriendsClick,
                          onNotificationClick = onNotificationClick,
                          onSignOut = onSignOutOrDel)
                    }

                PublicInfoInputs(
                    account = account,
                    viewModel = viewModel,
                    online = online,
                    onInputFocusChanged = onInputFocusChanged)
              }
        }
        Spacer(modifier = Modifier.height(Dimensions.Spacing.xxLarge))

        if (!uiState.isEmailVerified) {
          Text(
              text = "Verify Your Email",
              fontSize = Dimensions.TextSize.heading,
              modifier = Modifier.fillMaxWidth().padding(bottom = Dimensions.Padding.medium))

          // The Blue Card
          Box(
              contentAlignment = Alignment.BottomCenter,
              modifier = Modifier.testTag(EmailVerificationTestTags.VERIFICATION_SECTION)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.neutral),
                    shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                    modifier = Modifier.fillMaxWidth()) {
                      Box(
                          modifier =
                              Modifier.fillMaxSize().padding(horizontal = Dimensions.Padding.large),
                          contentAlignment = Alignment.Center) {
                            // Email Address
                            Text(
                                text = userEmail,
                                color = AppColors.textIcons,
                                modifier =
                                    Modifier.align(Alignment.CenterStart)
                                        .testTag(EmailVerificationTestTags.USER_EMAIL))

                            // Send Icon
                            IconButton(
                                onClick = {
                                  viewModel.sendVerificationEmail()
                                  viewModel.refreshEmailVerificationStatus()
                                  toast = ToastData(message = MainTabUi.PrivateInfo.TOAST_MSG)
                                },
                                enabled = online,
                                modifier =
                                    Modifier.align(Alignment.CenterEnd)
                                        .testTag(
                                            EmailVerificationTestTags
                                                .RESEND_MAIL_VERIFICATION_BTN)) {
                                  Icon(
                                      imageVector = Icons.AutoMirrored.Filled.Send,
                                      contentDescription = MainTabUi.PrivateInfo.SEND_ICON_DESC,
                                      tint = AppColors.textIcons,
                                      modifier = Modifier.size(Dimensions.IconSize.large))
                                }
                          }
                    }

                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .offset(y = MainTabUi.OFFSET_EMAIL)
                            .zIndex(1f)) {
                      ToastHost(toast = toast, onToastFinished = { toast = null })
                    }
              }

          Spacer(modifier = Modifier.height(Dimensions.Spacing.xxxLarge))
        }

        Text(
            text = MainTabUi.SettingRows.HEADER,
            fontSize = Dimensions.TextSize.heading,
            modifier = Modifier.fillMaxWidth())

        SettingsRow(
            icon = Icons.Default.Palette,
            label = MainTabUi.SettingRows.PREFERENCES,
            onClick = { onNavigate(ProfilePage.Preferences) },
            modifier = Modifier.testTag(ProfileNavigationTestTags.SETTINGS_ROW_PREFERENCES))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimensions.DividerThickness.standard,
            color = AppColors.textIcons.copy(alpha = 0.5f))

        SettingsRow(
            icon = Icons.Outlined.NotificationsNone,
            label = MainTabUi.SettingRows.NOTIF,
            onClick = { onNavigate(ProfilePage.NotificationSettings) },
            modifier = Modifier.testTag(ProfileNavigationTestTags.SETTINGS_ROW_NOTIFICATIONS))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimensions.DividerThickness.standard,
            color = AppColors.textIcons.copy(alpha = 0.5f))

        SettingsRow(
            icon = Icons.Default.Store,
            label = MainTabUi.SettingRows.BUSINESSES,
            onClick = { onNavigate(ProfilePage.Businesses) },
            modifier = Modifier.testTag(ProfileNavigationTestTags.SETTINGS_ROW_BUSINESSES))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimensions.DividerThickness.standard,
            color = AppColors.textIcons.copy(alpha = 0.5f))

        SettingsRow(
            icon = Icons.Default.Email,
            label = MainTabUi.SettingRows.EMAIL,
            onClick = { onNavigate(ProfilePage.Email) },
            modifier = Modifier.testTag(ProfileNavigationTestTags.SETTINGS_ROW_EMAIL))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = Dimensions.DividerThickness.standard,
            color = AppColors.textIcons.copy(alpha = 0.5f))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(top = Dimensions.Padding.xxLarge)) {
              Button(
                  onClick = { showDelDialog = true },
                  shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
                  elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.medium),
                  modifier = Modifier.testTag(DeleteAccSectionTestTags.BUTTON),
                  colors =
                      ButtonColors(
                          containerColor = AppColors.negative,
                          disabledContainerColor = AppColors.negative,
                          contentColor = AppColors.textIcons,
                          disabledContentColor = AppColors.textIcons)) {
                    Text(text = MainTabUi.Misc.DELETE_ACCOUNT)
                  }

              DeleteAccountDialog(
                  showDelDialog,
                  onCancel = { showDelDialog = false },
                  onConfirm = {
                    showDelDialog = false
                    onSignOutOrDel()
                    viewModel.deleteAccount(account)
                    onDelete()
                  })
            }

        if (!uiState.isEmailVerified) {
          Spacer(modifier = Modifier.height(MainTabUi.PADDING_BOTTOM_SCROLL))
        }
      }
}

/**
 * Composable used to display a settings row, they lead to the opening of a subpage
 *
 * @param icon Icon to display at the start of the row
 * @param label Text to the right of the icon
 * @param onClick callback upon clicking on the row
 * @param modifier modifier applied to the composable (mainly used for testTags)
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(vertical = Dimensions.Padding.large, horizontal = Dimensions.Padding.medium),
      verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppColors.textIcons)
        Spacer(modifier = Modifier.width(Dimensions.Padding.extraLarge))
        Text(label, color = AppColors.textIcons, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.textIcons)
      }
}

/**
 * Handles sub-pages as it creates a fake "scaffold". The point is that it switches the page's
 * content to only display what it's given and handles back navigation to the main tab of the screen
 *
 * @param title "Top bar" text
 * @param onBack callback to return to the main page
 * @param content Content of the subpage scaffold
 */
@Composable
fun SubPageScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
  val focusManager = LocalFocusManager.current

  Column(
      Modifier.fillMaxSize().padding(Dimensions.Padding.extraLarge).pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
      }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
              onClick = onBack,
              modifier = Modifier.testTag(ProfileNavigationTestTags.SUB_PAGE_BACK_BUTTON)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
          Text(title, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))

        content()
      }
}

/////////////////////////////////////////////////////////////////////////////////////
// PUBLIC INFO SECTION
//////////////////////////////////////////////////////////////////////////////////////

/**
 * Composable representing all 3 buttons in the public info section
 *
 * @param account Current user
 * @param viewModel viewmodel used by this screen
 * @param onFriendsClick callback to navigate to the friend's tab
 * @param onNotificationClick callback to navigate to the notifications's tab
 * @param onSignOut callback to sign out
 */
@Composable
fun PublicInfoActions(
    account: Account,
    viewModel: ProfileScreenViewModel,
    onFriendsClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSignOut: () -> Unit
) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
            verticalAlignment = Alignment.CenterVertically) {
              // Friends Button
              Button(
                  modifier =
                      Modifier.size(MainTabUi.ACTION_BUTTON_SIZE)
                          .testTag(PublicInfoTestTags.ACTION_FRIENDS),
                  onClick = onFriendsClick,
                  shape = RoundedCornerShape(Dimensions.CornerRadius.extraLarge),
                  contentPadding = PaddingValues(0.dp),
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = AppColors.secondary, contentColor = AppColors.textIcons),
                  elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.high)) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = MainTabUi.PublicInfo.FRIENDS_BTN_DESC,
                        modifier = Modifier.size(Dimensions.IconSize.extraLarge))
                  }

              // Notifications Button
              Box(modifier = Modifier.size(MainTabUi.ACTION_BUTTON_SIZE)) {
                Button(
                    modifier =
                        Modifier.matchParentSize().testTag(PublicInfoTestTags.ACTION_NOTIFICATIONS),
                    onClick = onNotificationClick,
                    shape = RoundedCornerShape(Dimensions.CornerRadius.extraLarge),
                    contentPadding = PaddingValues(0.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AppColors.secondary,
                            contentColor = AppColors.textIcons),
                    elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.high)) {
                      Icon(
                          Icons.Outlined.NotificationsNone,
                          contentDescription = MainTabUi.PublicInfo.NOTIF_BTN_DESC,
                          modifier = Modifier.size(Dimensions.IconSize.extraLarge))
                    }

                val notificationCount = account.notifications.filter { !it.read }.size

                if (notificationCount > 0) {
                  Box(
                      modifier =
                          Modifier.size(Dimensions.IconSize.large)
                              .align(Alignment.TopEnd)
                              .offset(MainTabUi.OFFSET_X, MainTabUi.OFFSET_Y)
                              .background(AppColors.negative, CircleShape)
                              .testTag(PublicInfoTestTags.NOTIF_COUNT),
                      contentAlignment = Alignment.Center) {
                        Text(
                            text =
                                if (notificationCount > MainTabUi.MAX_NOTIF_COUNT) "9+"
                                else notificationCount.toString(),
                            color = AppColors.primary,
                            fontSize = Dimensions.TextSize.small)
                      }
                }
              }
            }

        // Logout Button
        Button(
            onClick = {
              onSignOut()
              viewModel.signOut()
            },
            modifier =
                Modifier.width(MainTabUi.LOGOUT_BUTTON_W)
                    .height(MainTabUi.LOGOUT_BUTTON_H)
                    .testTag(PublicInfoTestTags.ACTION_LOGOUT),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AppColors.secondary,
                    disabledContainerColor = AppColors.secondary,
                    contentColor = AppColors.textIcons,
                    disabledContentColor = AppColors.textIcons),
            shape = RoundedCornerShape(Dimensions.CornerRadius.large),
            elevation = ButtonDefaults.buttonElevation(Dimensions.Elevation.high)) {
              Text(text = MainTabUi.PublicInfo.LOGOUT_BTN)
            }
      }
}

/**
 * Handles the input fields in that first section
 *
 * @param account Current user
 * @param viewModel viewmodel used by this screen
 * @param onInputFocusChanged callback when input focus state changes
 */
@Composable
fun PublicInfoInputs(
    account: Account,
    viewModel: ProfileScreenViewModel,
    online: Boolean,
    onInputFocusChanged: (Boolean) -> Unit = {}
) {
  var name by remember { mutableStateOf(account.name) }
  var desc by remember { mutableStateOf(account.description ?: "") }

  Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top) {
        // HANDLE VALIDATION
        val errorMsg by viewModel.errorMessage.collectAsState()
        var handle by remember { mutableStateOf(account.handle) }
        var showErrors by remember { mutableStateOf(false) }
        val errorHandle = showErrors && errorMsg.isNotBlank() && handle != account.handle

        FocusableInputField(
            enabled = online,
            value = handle,
            modifier =
                Modifier.testTag(PublicInfoTestTags.INPUT_HANDLE)
                    .padding(bottom = Dimensions.Padding.medium)
                    .fillMaxWidth(),
            onValueChange = {
              if (it.length < 32) handle = it
              if (it.isNotBlank()) {
                showErrors = true
                viewModel.checkHandleAvailable(it)
              } else {
                showErrors = false
              }
            },
            label = { Text(text = MainTabUi.PublicInfo.HANDLE_INPUT_FIELD) },
            leadingIcon = {
              Text(
                  text = MainTabUi.PublicInfo.HANDLE_PREFIX,
                  style = MaterialTheme.typography.bodyLarge,
                  color = AppColors.textIcons,
                  modifier = Modifier.padding(start = Dimensions.Padding.medium))
            },
            singleLine = true,
            isError = errorHandle,
            onFocusChanged = { focused ->
              onInputFocusChanged(focused)
              if (!focused && !errorHandle) viewModel.setAccountHandle(account, newHandle = handle)
            })

        if (errorHandle) {
          Text(
              text = errorMsg,
              color = AppColors.negative,
              style = MaterialTheme.typography.bodySmall,
              textAlign = TextAlign.Center,
              modifier =
                  Modifier.padding(
                          top = Dimensions.Padding.small,
                          start = Dimensions.Padding.large,
                          end = Dimensions.Padding.large,
                          bottom = Dimensions.Padding.small)
                      .testTag(PublicInfoTestTags.ERROR_HANDLE))
        }

        // NAME VALIDATION
        val nameError = name.isBlank()
        FocusableInputField(
            label = { Text(text = MainTabUi.PublicInfo.NAME_INPUT_FIELD) },
            modifier =
                Modifier.testTag(PublicInfoTestTags.INPUT_USERNAME)
                    .padding(bottom = Dimensions.Padding.medium)
                    .fillMaxWidth(),
            value = name,
            onValueChange = { if (it.length < 90) name = it },
            trailingIcon = {
              Icon(
                  imageVector = Icons.Default.Cancel,
                  contentDescription = null,
                  modifier = Modifier.clickable(onClick = { name = "" }))
            },
            isError = nameError,
            onFocusChanged = { focused ->
              onInputFocusChanged(focused)
              if (!focused && !nameError) {
                viewModel.setAccountName(account, name)
              }
            })

        if (nameError) {
          Text(
              text = MainTabUi.PublicInfo.NAME_INPUT_FIELD_ERR,
              color = AppColors.negative,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.padding(
                          start = Dimensions.Padding.extraLarge,
                          end = Dimensions.Padding.extraLarge,
                          top = Dimensions.Padding.small)
                      .testTag(PublicInfoTestTags.ERROR_USERNAME)
                      .fillMaxWidth())
        }

        // DESCRIPTION
        FocusableInputField(
            label = { Text(MainTabUi.PublicInfo.DESC_INPUT_FIELD) },
            modifier =
                Modifier.testTag(PublicInfoTestTags.INPUT_DESCRIPTION)
                    .padding(bottom = Dimensions.Padding.medium)
                    .fillMaxWidth(),
            value = desc,
            trailingIcon = {
              Icon(
                  imageVector = Icons.Default.Cancel,
                  contentDescription = null,
                  modifier = Modifier.clickable(onClick = { desc = "" }))
            },
            onValueChange = { if (it.length < 90) desc = it },
            singleLine = false,
            onFocusChanged = { focused ->
              onInputFocusChanged(focused)
              if (!focused) viewModel.setAccountDescription(account, newDescription = desc)
            })

        Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
      }
}

/**
 * Handles the avatar section of the user
 *
 * @param viewModel viewmodel used by this screen
 * @param account Current user
 */
@Composable
fun DisplayAvatar(viewModel: ProfileScreenViewModel, account: Account) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var showChooser by remember { mutableStateOf(false) }
  var showPermissionDenied by remember { mutableStateOf(false) }

  // --- Upload function ---
  val setPhoto: suspend (String) -> Unit = { path ->
    runCatching { viewModel.setAccountPhoto(account, context, path) }
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
          Modifier.size(MainTabUi.AVATAR_SIZE)
              .clickable { showChooser = true }
              .testTag(PublicInfoTestTags.AVATAR_CONTAINER),
      contentAlignment = Alignment.TopEnd) {
        if (account.photoUrl.isNullOrBlank()) {
          Box(
              modifier =
                  Modifier.size(MainTabUi.AVATAR_SIZE)
                      .clip(CircleShape)
                      .background(AppColors.textIconsFade)
                      .testTag(PublicInfoTestTags.AVATAR_PLACEHOLDER),
              contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = MainTabUi.PublicInfo.AVATAR_HOLDER_DESC,
                    tint = AppColors.divider,
                    modifier = Modifier.size(Dimensions.IconSize.giant))
              }
        } else {
          Image(
              painter = rememberAsyncImagePainter(account.photoUrl),
              contentDescription = MainTabUi.PublicInfo.AVATAR_IMAGE_DESC,
              contentScale = ContentScale.Crop,
              modifier =
                  Modifier.size(MainTabUi.AVATAR_SIZE)
                      .clip(CircleShape)
                      .testTag(PublicInfoTestTags.AVATAR_IMAGE))
        }

        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = MainTabUi.PublicInfo.AVATAR_EDIT_DESC,
            tint = AppColors.primary,
            modifier =
                Modifier.size(Dimensions.IconSize.extraLarge)
                    .background(AppColors.textIconsFade, CircleShape)
                    .padding(Dimensions.Padding.small)
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
        containerColor = AppColors.primary,
        modifier = Modifier.testTag(PublicInfoTestTags.CAMERA_PERMISSION_DIALOG),
        onDismissRequest = { showPermissionDenied = false },
        title = { Text(text = MainTabUi.PublicInfo.NO_PERMS_TITLE) },
        text = { Text(text = MainTabUi.PublicInfo.NO_PERMS_TEXT) },
        confirmButton = {
          TextButton(
              onClick = { showPermissionDenied = false },
              modifier = Modifier.testTag(PublicInfoTestTags.CAMERA_PERMISSION_OK)) {
                Text(text = MainTabUi.PublicInfo.NO_PERMS_OK)
              }
        })
  }
}

/**
 * Dialog upon editing the avatar
 *
 * @param onDismiss callback upon dismissing
 * @param onCamera callback upon selecting camera
 * @param onGallery callback upon selecting gallery
 * @param onRemove callback upon removing user's profile picture
 */
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
            Modifier.clip(RoundedCornerShape(Dimensions.CornerRadius.extraLarge))
                .background(AppColors.primary)
                .padding(Dimensions.Padding.xLarge)
                .testTag(PublicInfoTestTags.AVATAR_CHOOSER_DIALOG)) {
          Column(
              verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large),
              modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = MainTabUi.PublicInfo.AVATAR_DIALOG_TITLE,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    text = MainTabUi.PublicInfo.AVATAR_DIALOG_PROMPT,
                    style = MaterialTheme.typography.bodyMedium)

                Button(
                    onClick = onCamera,
                    modifier =
                        Modifier.fillMaxWidth().testTag(PublicInfoTestTags.AVATAR_CHOOSER_CAMERA)) {
                      Text(text = MainTabUi.PublicInfo.AVATAR_CAMERA_OPT)
                    }
                Button(
                    onClick = onGallery,
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(PublicInfoTestTags.AVATAR_CHOOSER_GALLERY)) {
                      Text(text = MainTabUi.PublicInfo.AVATAR_GALLERY_OPT)
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
                        Text(text = MainTabUi.PublicInfo.AVATAR_REMOVE_OPT)
                      }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier =
                        Modifier.fillMaxWidth().testTag(PublicInfoTestTags.AVATAR_CHOOSER_CANCEL)) {
                      Text(text = MainTabUi.PublicInfo.AVATAR_CANCEL_OPT)
                    }
              }
        }
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// PRIVATE INFO SECTION
/////////////////////////////////////////////////////////////////////////////////////

/**
 * Handles everything related to the email field
 *
 * @param email email of the user
 * @param isVerified whether the user has his email verified or not
 * @param onEmailChange callback upon email change
 * @param onFocusChanged callback upon stop of the edition
 * @param onSendVerification callback upon click of the verify email button
 * @param onChangeEmail callback upon click of the change email button with new email and password
 * @param isLoading whether an email change operation is in progress
 * @param errorMsg error message to display if email change fails
 * @param successMsg success message to display after successful email change
 */
@Composable
fun EmailSection(
    email: String,
    online: Boolean,
    onEmailChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onChangeEmail: (newEmail: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMsg: String? = null,
    successMsg: String? = null
) {
  var localEmail by remember { mutableStateOf(email) }
  var showErrors by remember { mutableStateOf(false) }
  var newEmail by remember { mutableStateOf("") }
  var confirmEmail by remember { mutableStateOf("") }
  var showNewEmailErrors by remember { mutableStateOf(false) }
  var showConfirmEmailErrors by remember { mutableStateOf(false) }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  val emailError = showErrors && !isValidEmail(localEmail)
  val newEmailError = showNewEmailErrors && newEmail.isNotBlank() && !isValidEmail(newEmail)
  val newEmailValid = showNewEmailErrors && newEmail.isNotBlank() && isValidEmail(newEmail)
  val emailsMatch = newEmail.isNotBlank() && confirmEmail.isNotBlank() && newEmail == confirmEmail
  val emailsDontMatch =
      showConfirmEmailErrors && confirmEmail.isNotBlank() && newEmail != confirmEmail
  val isChangeEmailEnabled = password.isNotBlank() && newEmailValid && emailsMatch

  Box(modifier = Modifier.fillMaxWidth().testTag(PrivateInfoTestTags.EMAIL_SECTION)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically) {
            FocusableInputField(
                readOnly = true,
                label = { Text(text = MainTabUi.PrivateInfo.EMAIL_INPUT_FIELD) },
                value = email,
                onValueChange = { new ->
                  localEmail = new
                  onEmailChange(new)

                  showErrors = new.isNotBlank()
                },
                isError = emailError,
                modifier =
                    Modifier.weight(Dimensions.Weight.full)
                        .testTag(PrivateInfoTestTags.EMAIL_INPUT))
          }

      Row(modifier = Modifier.fillMaxWidth()) {
        if (emailError) {
          Text(
              text = MainTabUi.PrivateInfo.EMAIL_INVALID_MSG,
              color = AppColors.negative,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.padding(
                          start = Dimensions.Padding.extraLarge, top = Dimensions.Padding.small)
                      .testTag(PrivateInfoTestTags.EMAIL_ERROR_LABEL))
        }
      }

      HorizontalDivider(
          modifier = Modifier.fillMaxWidth(),
          thickness = Dimensions.DividerThickness.standard,
          color = AppColors.textIcons.copy(alpha = 0.5f))

      Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

      Text(
          text = MainTabUi.EmailSection.CHANGE_EMAIL_TITLE,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Start,
          modifier = Modifier.fillMaxWidth().testTag(EmailSectionTestTags.CHANGE_EMAIL_TITLE))

      FocusableInputField(
          label = { Text(text = MainTabUi.EmailSection.NEW_EMAIL_INPUT_FIELD) },
          enabled = online,
          value = newEmail,
          onValueChange = { new ->
            newEmail = new
            showNewEmailErrors = new.isNotBlank()
          },
          isError = newEmailError,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = Dimensions.Padding.medium)
                  .testTag(EmailSectionTestTags.NEW_EMAIL_INPUT),
          singleLine = true)

      Row(modifier = Modifier.fillMaxWidth().padding(top = Dimensions.Padding.small)) {
        if (newEmailError) {
          Text(
              text = MainTabUi.EmailSection.NEW_EMAIL_INVALID_MSG,
              color = AppColors.negative,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.padding(start = Dimensions.Padding.extraLarge)
                      .testTag(EmailSectionTestTags.NEW_EMAIL_ERROR_LABEL))
        } else if (newEmailValid) {
          Text(
              text = MainTabUi.EmailSection.NEW_EMAIL_VALID_MSG,
              color = AppColors.affirmative,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.padding(start = Dimensions.Padding.extraLarge)
                      .testTag(EmailSectionTestTags.NEW_EMAIL_VALID_LABEL))
        }
      }

      FocusableInputField(
          label = { Text(text = MainTabUi.EmailSection.CONFIRM_EMAIL_INPUT_FIELD) },
          enabled = online,
          value = confirmEmail,
          onValueChange = { new ->
            confirmEmail = new
            showConfirmEmailErrors = new.isNotBlank()
          },
          isError = emailsDontMatch,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = Dimensions.Padding.medium)
                  .testTag(EmailSectionTestTags.CONFIRM_EMAIL_INPUT),
          singleLine = true)

      Row(modifier = Modifier.fillMaxWidth().padding(top = Dimensions.Padding.small)) {
        if (emailsDontMatch) {
          Text(
              text = MainTabUi.EmailSection.EMAILS_DONT_MATCH_MSG,
              color = AppColors.negative,
              style = MaterialTheme.typography.bodySmall,
              modifier =
                  Modifier.padding(start = Dimensions.Padding.extraLarge)
                      .testTag(EmailSectionTestTags.EMAILS_DONT_MATCH_LABEL))
        }
      }

      FocusableInputField(
          label = { Text(text = MainTabUi.EmailSection.PASSWORD_INPUT_FIELD) },
          enabled = online,
          value = password,
          onValueChange = { password = it },
          visualTransformation =
              if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
              Icon(
                  imageVector =
                      if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password")
            }
          },
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = Dimensions.Padding.medium)
                  .testTag(EmailSectionTestTags.PASSWORD_INPUT),
          singleLine = true)

      Button(
          onClick = {
            if (!isLoading) {
              onChangeEmail(newEmail, password)
              // Clear password after submission for security
              password = ""
            }
          },
          enabled = isChangeEmailEnabled && !isLoading,
          modifier =
              Modifier.fillMaxWidth()
                  .padding(top = Dimensions.Padding.medium)
                  .testTag(EmailSectionTestTags.CHANGE_EMAIL_BUTTON),
          shape = ButtonDefaults.shape,
          colors = ButtonDefaults.buttonColors()) {
            if (isLoading) {
              CircularProgressIndicator(
                  modifier = Modifier.size(Dimensions.IconSize.standard),
                  color = AppColors.textIcons)
            } else {
              Text(text = MainTabUi.EmailSection.CHANGE_EMAIL_BUTTON_TEXT)
            }
          }

      // Show error message if email change fails
      errorMsg?.let { error ->
        Text(
            text = error,
            color = AppColors.negative,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = Dimensions.Padding.small, start = Dimensions.Padding.medium))
      }

      // Show success message after successful email change
      successMsg?.let { success ->
        Text(
            text = success,
            color = AppColors.affirmative,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = Dimensions.Padding.small, start = Dimensions.Padding.medium))
      }
    }
  }
}

/** Helper function used to validate the user's email */
private fun isValidEmail(value: String): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()

data class ToastData(
    val message: String,
    val id: Long = System.currentTimeMillis() // unique per show
)

/**
 * Handles the box of text that appears upon sending the verification email
 *
 * @param toast Toast data
 * @param duration How long till the popup dissapears
 * @param onToastFinished What to do when the duration has elapsed
 */
@Composable
fun ToastHost(toast: ToastData?, duration: Long = 1500L, onToastFinished: () -> Unit) {
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
    ) {
      Box(
          modifier =
              Modifier.background(
                      color = AppColors.textIconsFade,
                      shape = RoundedCornerShape(Dimensions.CornerRadius.extraLarge))
                  .padding(
                      horizontal = Dimensions.Padding.extraLarge,
                      vertical = Dimensions.Padding.small)
                  .testTag(PrivateInfoTestTags.EMAIL_TOAST)) {
            Text(
                text = data.message,
                color = AppColors.primary,
                fontSize = Dimensions.TextSize.standard)
          }
    }
  }
}

/**
 * Handles everything related to the user's roles
 *
 * @param account Current user
 * @param viewModel viewmodel used by this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolesSection(
    account: Account,
    viewModel: ProfileScreenViewModel,
    businesses: Pair<List<Shop>, List<SpaceRenter>> = viewModel.businesses.collectAsState().value,
    online: Boolean
) {

  var expanded by remember { mutableStateOf(hasNoRoles(account)) }

  var isShopChecked by remember { mutableStateOf(account.shopOwner) }
  var isSpaceRented by remember { mutableStateOf(account.spaceRenter) }

  var showDialog by remember { mutableStateOf(false) }
  var pendingAction by remember { mutableStateOf<RoleAction?>(null) }

  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.large)) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Dimensions.Padding.small),
        verticalAlignment = Alignment.CenterVertically) {
          Text(
              text = MainTabUi.PrivateInfo.ROLES_TITLE,
              style = MaterialTheme.typography.bodyLarge,
              fontSize = Dimensions.TextSize.largeHeading,
              modifier = Modifier.weight(1f).testTag(PrivateInfoTestTags.COLLAPSABLE))

          Icon(
              imageVector = Icons.Default.ChevronRight,
              contentDescription = null,
              modifier = Modifier.rotate(if (expanded) 270f else 90f))
        }

    AnimatedVisibility(visible = expanded) {
      Column {
        RoleCheckBox(
            isChecked = isShopChecked,
            onCheckedChange = { checked ->
              if (!checked) {
                if (businesses.first.isNotEmpty()) {
                  pendingAction = RoleAction.ShopOff
                  showDialog = true
                } else {
                  isShopChecked = false
                  viewModel.setAccountRole(
                      account, isShopOwner = false, isSpaceRenter = isSpaceRented)
                }
              } else {
                isShopChecked = true
                viewModel.setAccountRole(account, isShopOwner = true, isSpaceRenter = isSpaceRented)
              }
            },
            label = MainTabUi.PrivateInfo.SELL_ITEMS_LABEL,
            description = MainTabUi.PrivateInfo.SELL_ITEMS_DESC,
            testTag = PrivateInfoTestTags.ROLE_SHOP_CHECKBOX,
            online = online)

        RoleCheckBox(
            isChecked = isSpaceRented,
            onCheckedChange = { checked ->
              if (!checked) {
                if (businesses.second.isNotEmpty()) {
                  pendingAction = RoleAction.SpaceOff
                  showDialog = true
                } else {
                  isSpaceRented = false
                  viewModel.setAccountRole(
                      account, isShopOwner = isShopChecked, isSpaceRenter = false)
                }
              } else {
                isSpaceRented = true
                viewModel.setAccountRole(account, isShopOwner = isShopChecked, isSpaceRenter = true)
              }
            },
            label = MainTabUi.PrivateInfo.RENT_SPACES_LABEL,
            description = MainTabUi.PrivateInfo.RENT_SPACES_DESC,
            testTag = PrivateInfoTestTags.ROLE_SPACE_CHECKBOX,
            online = online)
      }
    }
  }

  // Confirmation dialog
  if (showDialog && pendingAction != null) {
    RemoveCatalogDialog(
        visible = true,
        action = pendingAction!!,
        onConfirm = {
          when (pendingAction) {
            RoleAction.ShopOff -> {
              viewModel.deleteAccountShops(account)
              isShopChecked = false
              viewModel.setAccountRole(account, isShopOwner = false, isSpaceRenter = isSpaceRented)
            }
            RoleAction.SpaceOff -> {
              viewModel.deleteAccountSpaceRenters(account)
              isSpaceRented = false
              viewModel.setAccountRole(account, isShopOwner = isShopChecked, isSpaceRenter = false)
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

/**
 * Handles the dialog that pops upon removal of a role
 *
 * @param visible whether this popup is visible
 * @param action Differentiates between removing Shop/SpaceRenter role
 * @param onConfirm callback upon confirmation
 * @param onCancel callback upon cancellation
 */
@Composable
private fun RemoveCatalogDialog(
    visible: Boolean,
    action: RoleAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
  val informativeText =
      when (action) {
        RoleAction.ShopOff -> MainTabUi.PrivateInfo.ROLE_ACTION_SHOP
        RoleAction.SpaceOff -> MainTabUi.PrivateInfo.ROLE_ACTION_SPACE
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
                Text(text = MainTabUi.PrivateInfo.DIALOG_CONFIRM)
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
                Text(text = MainTabUi.PrivateInfo.DIALOG_CANCEL)
              }
        },
        shape = RoundedCornerShape(Dimensions.CornerRadius.extraLarge))
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// NOTIFICATION SETTINGS SECTION
/////////////////////////////////////////////////////////////////////////////////////

/**
 * Handles the notification settings section
 *
 * @param preference The user's selected preference
 * @param onPreferenceChange callback invoked upon change of preference
 */
@Composable
fun NotificationSettingsSection(
    preference: NotificationSettings,
    onPreferenceChange: (NotificationSettings) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = MainTabUi.NotificationsSection.TITLE,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.testTag(NotificationsSectionTestTags.NOTIFICATION_SECTION_TITLE))

    RadioOptionRow(
        label = MainTabUi.NotificationsSection.OPT_EVERY,
        selected = preference == NotificationSettings.EVERYONE,
        modifier = Modifier.testTag(NotificationsSectionTestTags.RADIO_EVERYONE),
        onClick = { onPreferenceChange(NotificationSettings.EVERYONE) })

    RadioOptionRow(
        label = MainTabUi.NotificationsSection.OPT_FRIENDS,
        selected = preference == NotificationSettings.FRIENDS_ONLY,
        modifier = Modifier.testTag(NotificationsSectionTestTags.RADIO_FRIENDS),
        onClick = { onPreferenceChange(NotificationSettings.FRIENDS_ONLY) })

    RadioOptionRow(
        label = MainTabUi.NotificationsSection.OPT_NONE,
        selected = preference == NotificationSettings.NO_ONE,
        modifier = Modifier.testTag(NotificationsSectionTestTags.RADIO_NONE),
        onClick = { onPreferenceChange(NotificationSettings.NO_ONE) })
  }
}

/**
 * Composable used for the rows options between notification settings
 *
 * @param label Description of the option
 * @param selected Whether this option is currently selected
 * @param onClick callback invoked upon click
 * @param modifier Additional modifiers to add (used for testTags normally)
 */
@Composable
fun RadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  Row(
      modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
      verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(Dimensions.Spacing.medium))
        Text(text = label)
      }
}

/**
 * Handles the delete account dialog
 *
 * @param show Whether this dialog is visible
 * @param onCancel callback upon cancellation of the operation
 * @param onConfirm callback upon confirmation
 */
@Composable
fun DeleteAccountDialog(show: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
  if (show) {
    AlertDialog(
        containerColor = AppColors.primary,
        modifier = Modifier.testTag(DeleteAccSectionTestTags.POPUP),
        onDismissRequest = onCancel,
        title = { Text(text = MainTabUi.Misc.DIALOG_TITLE) },
        text = { Text(text = MainTabUi.Misc.DIALOG_DESC) },
        confirmButton = {
          TextButton(
              onClick = onConfirm,
              modifier = Modifier.testTag(DeleteAccSectionTestTags.CONFIRM),
              colors =
                  ButtonColors(
                      containerColor = AppColors.negative,
                      disabledContentColor = AppColors.negative,
                      contentColor = AppColors.textIcons,
                      disabledContainerColor = AppColors.textIcons)) {
                Text(MainTabUi.Misc.DIALOG_CONFIRM)
              }
        },
        dismissButton = {
          TextButton(
              onClick = onCancel,
              modifier = Modifier.testTag(DeleteAccSectionTestTags.CANCEL),
              colors =
                  ButtonColors(
                      containerColor = AppColors.affirmative,
                      disabledContentColor = AppColors.affirmative,
                      contentColor = AppColors.textIcons,
                      disabledContainerColor = AppColors.textIcons)) {
                Text(MainTabUi.Misc.DIALOG_CANCEL)
              }
        })
  }
}

/**
 * Generic business card used for both shops and space renters
 *
 * @param icon Icon to display on the left
 * @param label Label to display
 * @param onClick Callback upon clicking the card
 */
@Composable
fun BusinessCard(icon: Int, label: String, onClick: () -> Unit) {
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .testTag(PublicInfoTestTags.BUSINESS_CARD),
      colors = CardDefaults.cardColors(containerColor = AppColors.primary)) {
        Row(
            modifier = Modifier.padding(Dimensions.Padding.large).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
              Icon(
                  painter = painterResource(id = icon),
                  contentDescription = null,
                  tint = AppColors.neutral,
                  modifier = Modifier.size(Dimensions.IconSize.xxLarge),
              )

              Spacer(modifier = Modifier.width(Dimensions.Spacing.large))

              Text(
                  text = label,
                  style = MaterialTheme.typography.bodyLarge,
                  fontSize = Dimensions.TextSize.largeHeading,
                  modifier = Modifier.weight(1f) // <-- makes label take remaining space
                  )

              Spacer(modifier = Modifier.width(Dimensions.Spacing.large))

              Icon(
                  imageVector = Icons.Default.ChevronRight,
                  contentDescription = null,
                  tint = AppColors.textIcons,
                  modifier = Modifier.size(Dimensions.IconSize.large))
            }
      }
}

/**
 * Composable for displaying a shop card
 *
 * @param shop The shop to display
 * @param onClick Callback upon clicking the card
 */
@Composable
fun ShopCard(shop: Shop, onClick: () -> Unit) {
  BusinessCard(icon = R.drawable.ic_storefront, label = shop.name, onClick = onClick)
}

/**
 * Composable for displaying a space renter card
 *
 * @param spaceRenter The space renter to display
 * @param onClick Callback upon clicking the card
 */
@Composable
fun SpaceRenterCard(spaceRenter: SpaceRenter, onClick: () -> Unit) {
  BusinessCard(icon = R.drawable.ic_table, label = spaceRenter.name, onClick = onClick)
}
