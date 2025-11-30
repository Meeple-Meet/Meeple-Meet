package com.github.meeplemeet.ui.discussions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.Discussion
import com.github.meeplemeet.model.discussions.DiscussionDetailsViewModel
import com.github.meeplemeet.model.images.ImageFileUtils
import com.github.meeplemeet.model.offline.OfflineModeManager
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.components.PhotoDialogBottomBar
import com.github.meeplemeet.ui.components.PhotoDialogTopBar
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

object UITestTags {
  const val DELETE_BUTTON = "delete_button"
  const val LEAVE_BUTTON = "leave_button"
  const val DISCUSSION_DESCRIPTION = "discussion_description"
  const val DELETE_DISCUSSION_DISPLAY = "delete_discussion_display"
  const val LEAVE_DISCUSSION_DISPLAY = "leave_discussion_display"
  const val DELETE_DISCUSSION_CONFIRM_BUTTON = "confirm_delete_button"
  const val LEAVE_DISCUSSION_CONFIRM_BUTTON = "confirm_leave_button"
  const val MAKE_ADMIN_BUTTON = "make_admin_button"
  const val DISCUSSION_NAME = "discussion_name"
  const val PROFILE_PICTURE = "profile_picture"
  const val PROFILE_PICTURE_CAMERA_OPTION = "profile_picture_camera_option"
  const val PROFILE_PICTURE_GALLERY_OPTION = "profile_picture_gallery_option"
  const val PROFILE_PICTURE_DIALOG_TITLE = "profile_picture_dialog_title"

  fun memberRowTag(uid: String) = "member_row_$uid"
}

private const val TEXT_GROUP_INFO = "Group Info"
private const val TEXT_LEAVE = "Leave"
private const val TEXT_DELETE = "Delete"
private const val TEXT_DELETE_DISCUSSION = "Delete Discussion"
private const val TEXT_LEAVE_DISCUSSION = "Leave Discussion"
private const val TEXT_DESCRIPTION = "Description:"
private const val TEXT_EDIT = "Edit"
private const val TEXT_MANAGE_MEMBER_PERMISSIONS = "Manage member permissions and actions"
private const val TEXT_MAKE_ADMIN = "Make Admin"
private const val TEXT_REMOVE_ADMIN = "Remove Admin"
private const val TEXT_REMOVE_FROM_GROUP = "Remove from Group"
private const val TEXT_CLOSE = "Close"
private const val TEXT_DELETE_CONFIRM = "Delete"

/**
 * Displays the discussion details/settings screen for managing discussion metadata and
 * participants.
 *
 * Provides comprehensive discussion management UI with:
 * - **Profile picture**: Display and edit discussion profile picture (admin only) (NEW)
 * - **Discussion info**: View/edit name, description, creation date
 * - **Participant management**: Add members, remove members, promote/demote admins
 * - **Participant search**: Real-time search with autocomplete dropdown
 * - **Permission controls**: Different UI based on admin/participant role
 * - **Actions**: Leave discussion or delete discussion (admin only)
 *
 * ## Profile Picture Features (NEW)
 * - Circular profile picture displayed at top of screen
 * - Camera icon overlay button (visible to admins only)
 * - Click camera icon → menu appears with Camera/Gallery options
 * - Camera option: Capture new photo (requires CAMERA permission)
 * - Gallery option: Select existing photo from device
 * - Photo is uploaded to Firebase Storage and discussion is updated
 * - Profile picture loads via Coil library from Firebase Storage URL
 *
 * ## Permission Model
 * - **Admins**: Can edit name/description, manage profile picture, add/remove members, manage admin
 *   roles, delete discussion
 * - **Participants**: Can view info, add members (if allowed), leave discussion
 * - **Creator**: Original creator, shown with "Creator" badge, always an admin
 *
 * ## Photo Upload Flow
 * 1. Admin clicks camera icon on profile picture
 * 2. Selects Camera or Gallery from menu
 * 3. Photo is cached via [ImageFileUtils]
 * 4. [DiscussionDetailsViewModel.setDiscussionProfilePicture] uploads and updates discussion
 * 5. UI refreshes automatically via discussionFlow
 *
 * @param account Current logged-in user account.
 * @param discussion The discussion to display and manage.
 * @param modifier Modifier for styling this composable.
 * @param viewModel ViewModel for discussion management operations.
 * @param onBack Navigation callback to exit the screen.
 * @param onLeave Callback invoked after user leaves the discussion.
 * @param onDelete Callback invoked after discussion is deleted.
 * @see DiscussionDetailsViewModel.setDiscussionProfilePicture for profile picture management
 * @see ImageFileUtils for photo caching
 * @see Discussion.profilePictureUrl for storage location
 */
@Composable
fun DiscussionDetailsScreen(
    account: Account,
    discussion: Discussion,
    modifier: Modifier = Modifier,
    viewModel: DiscussionDetailsViewModel = viewModel(),
    onBack: () -> Unit = {},
    onLeave: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val scrollState = rememberScrollState()
  var isInputFocused by remember { mutableStateOf(false) }

  /** --- Search state --- */
  var searchResults by remember { mutableStateOf<List<Account>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var dropdownExpanded by remember { mutableStateOf(false) }

  val selectedMembers = remember { mutableStateListOf<Account>() }

  /** --- Image source selection state --- */
  var showImageSourceMenu by remember { mutableStateOf(false) }

  /** --- Image upload handler --- */
  val uploadImage: suspend (String) -> Unit = { path ->
    try {
      viewModel.setDiscussionProfilePicture(discussion, account, context, path)
    } catch (_: Exception) {
      // Handle error silently or show a snackbar
    }
  }

  /** --- Camera launcher --- */
  val cameraLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
          coroutineScope.launch {
            val path = ImageFileUtils.saveBitmapToCache(context, bitmap)
            uploadImage(path)
          }
        }
      }

  /** --- Camera permission launcher --- */
  val cameraPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
          cameraLauncher.launch(null)
        }
      }

  /** --- Gallery launcher --- */
  val galleryLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
          coroutineScope.launch {
            val path = ImageFileUtils.cacheUriToFile(context, uri)
            uploadImage(path)
          }
        }
      }

  /** delete and leave alert state */
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showLeaveDialog by remember { mutableStateOf(false) }

  /** Populate selectedMembers with current discussion participants */
  LaunchedEffect(discussion.participants) {
    selectedMembers.clear()
    viewModel.getAccounts(discussion.participants, context) { accounts ->
      selectedMembers.addAll(accounts.filterNotNull())
    }
    /** Add current account only if not already present */
    account.let {
      if (selectedMembers.none { member -> member.uid == it.uid }) {
        selectedMembers.add(it)
      }
    }
  }

  val online by OfflineModeManager.hasInternetConnection.collectAsStateWithLifecycle()

  /** Live search effect */
  LaunchedEffect(searchQuery) {
    if (searchQuery.isBlank() || !online) {
      searchResults = emptyList()
      dropdownExpanded = false
      return@LaunchedEffect
    }

    isSearching = true
    viewModel.searchByHandle(searchQuery)
    viewModel.handleSuggestions.collect { list ->
      searchResults = list.filter { it.uid != account.uid && it !in selectedMembers }
      dropdownExpanded = searchResults.isNotEmpty()
      isSearching = false
    }
  }

  val isAdmin = discussion.admins.contains(account.uid) || discussion.creatorId == account.uid

  /** --- Name + Description --- */
  var newName by remember { mutableStateOf(discussion.name) }
  var newDesc by remember { mutableStateOf(discussion.description) }

  Scaffold(
      topBar = {
        TopBarWithDivider(
            text = TEXT_GROUP_INFO,
            /** Save Name and Description on back — this is the only time the DB is updated here */
            onReturn = {
              if (online && discussion.admins.contains(account.uid)) {
                viewModel.setDiscussionName(
                    discussion = discussion, name = newName, changeRequester = account)
                viewModel.setDiscussionDescription(
                    discussion = discussion, description = newDesc, changeRequester = account)
              }
              onBack()
            })
      },
      bottomBar = {
        val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
        if (!(shouldHide && isInputFocused)) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          start = Dimensions.Spacing.xxxLarge,
                          end = Dimensions.Spacing.xxxLarge,
                          bottom = Dimensions.Padding.xxLarge.plus(Dimensions.Spacing.extraSmall)),
              horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {
                /** The actual leave operation happens only after the confirmation dialog */
                /** Leave button is always enabled */
                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    enabled = online,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
                    modifier = Modifier.weight(1f).testTag(UITestTags.LEAVE_BUTTON)) {
                      Text(TEXT_LEAVE, color = AppColors.textIcons)
                    }

                /** The actual deletion happens only after the confirmation dialog */
                /** Delete button only if not member */
                if (discussion.creatorId == account.uid)
                    OutlinedButton(
                        onClick = { if (isAdmin) showDeleteDialog = true },
                        enabled = isAdmin && online,
                        colors =
                            ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative),
                        modifier = Modifier.weight(1f).testTag(UITestTags.DELETE_BUTTON)) {
                          Icon(
                              imageVector = Icons.Default.Delete,
                              contentDescription = null,
                              tint = AppColors.textIcons)
                          Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                          Text(TEXT_DELETE, color = AppColors.textIcons)
                        }
              }
        }
      }) { padding ->

        /** --- Main Content --- */
        Column(
            modifier =
                modifier
                    .padding(padding)
                    .padding(Dimensions.Spacing.extraLarge)
                    .verticalScroll(scrollState)
                    .pointerInput(Unit) {
                      detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
            verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {

              /** --- Discussion Icon/Profile Picture --- */
              Box(
                  modifier =
                      Modifier.align(Alignment.CenterHorizontally)
                          .size(Dimensions.IconSize.massive.times(Dimensions.Multipliers.double))
                          .clip(CircleShape)
                          .clickable(enabled = isAdmin) {
                            if (isAdmin && online) showImageSourceMenu = true
                          }
                          .testTag(UITestTags.PROFILE_PICTURE)) {
                    if (discussion.profilePictureUrl != null) {
                      AsyncImage(
                          model = discussion.profilePictureUrl,
                          contentDescription = "Discussion Profile Picture",
                          modifier = Modifier.fillMaxSize(),
                          contentScale = ContentScale.Crop)
                    } else {
                      Icon(
                          imageVector = Icons.Default.AccountCircle,
                          contentDescription = "Icon",
                          modifier = Modifier.fillMaxSize(),
                          tint = AppColors.textIcons)
                    }
                  }

              /** --- Fullscreen Profile Picture Dialog --- */
              if (showImageSourceMenu) {
                ProfilePictureDialog(
                    discussionName = newName,
                    profilePictureUrl = discussion.profilePictureUrl,
                    onDismiss = { showImageSourceMenu = false },
                    onTakePhoto = {
                      showImageSourceMenu = false
                      val permission = Manifest.permission.CAMERA
                      if (ContextCompat.checkSelfPermission(context, permission) ==
                          PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch(null)
                      } else {
                        cameraPermissionLauncher.launch(permission)
                      }
                    },
                    onChooseFromGallery = {
                      showImageSourceMenu = false
                      galleryLauncher.launch("image/*")
                    })
              }

              /** --- Discussion Name --- */
              /** These ensure only admins can edit the name field */
              FocusableInputField(
                  value = newName,
                  onValueChange = { newName = it },
                  readOnly = !isAdmin,
                  enabled = isAdmin && online,
                  modifier =
                      Modifier.fillMaxWidth()
                          .padding(horizontal = Dimensions.Spacing.extraMedium)
                          .onFocusChanged { isInputFocused = it.isFocused }
                          .testTag(UITestTags.DISCUSSION_NAME),
                  colors =
                      TextFieldDefaults.colors(
                          focusedContainerColor = AppColors.primary,
                          unfocusedContainerColor = AppColors.primary,
                          disabledContainerColor = AppColors.primary,
                          disabledTextColor = AppColors.textIcons,
                          focusedIndicatorColor = AppColors.textIcons,
                          unfocusedIndicatorColor = AppColors.textIconsFade,
                          cursorColor = AppColors.textIcons,
                          focusedTextColor = AppColors.textIcons,
                          unfocusedTextColor = AppColors.textIcons),
                  singleLine = true,
                  /**
                   * To make the text centered, we use an invisible leading icon to offset the
                   * trailing icon
                   */
                  leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.Transparent // Make it invisible
                        )
                  },
                  /** Trailing edit icon only if admin */
                  trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = TEXT_EDIT,
                        tint = if (isAdmin && online) AppColors.textIcons else Color.Transparent)
                  },
                  textStyle =
                      LocalTextStyle.current.copy(
                          fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                          textAlign = TextAlign.Center,
                      ),
              )

              /** --- Discussion Description --- */
              Text(
                  text = TEXT_DESCRIPTION,
                  style =
                      MaterialTheme.typography.titleLarge.copy(
                          textDecoration = TextDecoration.Underline),
                  modifier =
                      Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.extraMedium),
                  color = AppColors.textIcons)

              /** --- Description TextField --- */
              FocusableInputField(
                  value = newDesc,
                  onValueChange = { newDesc = it },
                  readOnly = !isAdmin,
                  enabled = isAdmin && online,
                  modifier =
                      Modifier.fillMaxWidth()
                          .padding(
                              start = Dimensions.Spacing.none, end = Dimensions.Padding.mediumSmall)
                          .onFocusChanged { isInputFocused = it.isFocused }
                          .testTag(UITestTags.DISCUSSION_DESCRIPTION),
                  /** Makes the textField look like a line */
                  colors =
                      TextFieldDefaults.colors(
                          focusedContainerColor = AppColors.primary,
                          unfocusedContainerColor = AppColors.primary,
                          disabledContainerColor = AppColors.primary,
                          disabledTextColor = AppColors.textIcons,
                          focusedIndicatorColor = AppColors.textIcons,
                          unfocusedIndicatorColor = AppColors.textIconsFade,
                          cursorColor = AppColors.textIcons,
                          focusedTextColor = AppColors.textIcons,
                          unfocusedTextColor = AppColors.textIcons),
                  singleLine = false,
                  minLines = 5,
                  maxLines = 5,
                  /**
                   * To make the text left-aligned, we use an invisible leading icon to offset the
                   * trailing icon
                   */
                  trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = TEXT_EDIT,
                        modifier = Modifier,
                        tint = if (isAdmin && online) AppColors.textIcons else Color.Transparent)
                  },
                  textStyle =
                      LocalTextStyle.current.copy(
                          fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                          textAlign = TextAlign.Start),
              )

              /** Row for search and member selection */
              if (isAdmin && online)
                  MemberSearchField(
                      searchQuery = searchQuery,
                      onQueryChange = { searchQuery = it },
                      searchResults = searchResults,
                      isSearching = isSearching,
                      dropdownExpanded = dropdownExpanded,
                      onDismiss = { dropdownExpanded = false },
                      onFocusChanged = { isInputFocused = it },
                      onSelect = { newAccount ->
                        viewModel.addUserToDiscussion(discussion, account, newAccount)
                        searchQuery = ""
                        dropdownExpanded = false
                      })

              /** --- Members List --- */
              MemberList(
                  selectedMembers = selectedMembers,
                  isMember = !isAdmin && online,
                  modifier = Modifier.align(Alignment.CenterHorizontally),
                  viewModel = viewModel,
                  currentAccount = account,
                  discussion = discussion)

              /** --- Delete Discussion (confirm dialog) --- */
              if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    modifier = Modifier.testTag(UITestTags.DELETE_DISCUSSION_DISPLAY),
                    containerColor = AppColors.primary,
                    title = {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.padding(bottom = Dimensions.Spacing.medium)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = AppColors.negative,
                                modifier = Modifier.size(Dimensions.IconSize.extraLarge))
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.extraMedium))
                            Text(
                                TEXT_DELETE_DISCUSSION,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.textIcons)
                          }
                    },
                    text = {
                      Text(
                          "Are you sure you want to delete ${discussion.name}? This action cannot be undone.",
                          style = MaterialTheme.typography.bodyMedium,
                          color = AppColors.textIcons)
                    },
                    confirmButton = {
                      Button(
                          /** Only owner can delete */
                          onClick = {
                            coroutineScope.launch {
                              viewModel.deleteDiscussion(context, discussion, account)
                              onDelete()
                            }
                            showDeleteDialog = false
                          },
                          colors =
                              ButtonDefaults.buttonColors(
                                  containerColor = AppColors.negative,
                                  contentColor = AppColors.textIcons),
                          modifier =
                              Modifier.testTag(UITestTags.DELETE_DISCUSSION_CONFIRM_BUTTON)) {
                            Text(TEXT_DELETE_CONFIRM, fontWeight = FontWeight.SemiBold)
                          }
                    },
                    dismissButton = {
                      OutlinedButton(
                          onClick = { showDeleteDialog = false },
                          colors =
                              ButtonDefaults.outlinedButtonColors(
                                  contentColor = AppColors.textIconsFade)) {
                            Text("Cancel", fontWeight = FontWeight.Medium)
                          }
                    })
              }
              /** --- Leave Discussion (confirm dialog) --- */
              if (showLeaveDialog) {
                AlertDialog(
                    onDismissRequest = { showLeaveDialog = false },
                    modifier = Modifier.testTag(UITestTags.LEAVE_DISCUSSION_DISPLAY),
                    containerColor = AppColors.primary,
                    title = {
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.padding(bottom = Dimensions.Spacing.medium)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = AppColors.affirmative,
                                modifier = Modifier.size(Dimensions.IconSize.extraLarge))
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.large))
                            Text(
                                TEXT_LEAVE_DISCUSSION,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.textIcons)
                          }
                    },
                    text = {
                      Text(
                          "Are you sure you want to leave ${discussion.name}? You will no longer see messages or members.",
                          style = MaterialTheme.typography.bodyMedium,
                          color = AppColors.textIcons)
                    },
                    confirmButton = {
                      Button(
                          /** Everyone can leave */
                          onClick = {
                            coroutineScope.launch {

                              /** leave discussion */
                              viewModel.removeUserFromDiscussion(discussion, account, account)
                              onLeave()
                            }
                            showLeaveDialog = false
                          },
                          colors =
                              ButtonDefaults.buttonColors(
                                  containerColor = AppColors.affirmative,
                                  contentColor = AppColors.textIcons),
                          modifier = Modifier.testTag(UITestTags.LEAVE_DISCUSSION_CONFIRM_BUTTON)) {
                            Text(TEXT_LEAVE, fontWeight = FontWeight.SemiBold)
                          }
                    },
                    dismissButton = {
                      OutlinedButton(
                          onClick = { showLeaveDialog = false },
                          colors =
                              ButtonDefaults.outlinedButtonColors(
                                  contentColor = AppColors.textIconsFade)) {
                            Text("Cancel", fontWeight = FontWeight.Medium)
                          }
                    })
              }
            }
      }
}

/**
 * Displays the list of discussion members and allows searching, adding, and managing members.
 *
 * @param selectedMembers The list of currently selected (discussion) members.
 * @param isMember Whether the current user is a regular member (not admin/owner).
 * @param modifier Modifier for styling this composable.
 * @param viewModel The FirestoreViewModel for data operations.
 * @param currentAccount The account of the current user.
 * @param discussion The current discussion object.
 */
@Composable
fun MemberList(
    selectedMembers: MutableList<Account>,
    isMember: Boolean,
    modifier: Modifier = Modifier,
    viewModel: DiscussionDetailsViewModel = viewModel(),
    currentAccount: Account,
    discussion: Discussion,
) {
  /** Small spacer between search field and list */
  Spacer(modifier = Modifier.height(Dimensions.Spacing.none))

  /** Selected Members */
  var selectedMember by remember { mutableStateOf<Account?>(null) }

  /** Only show the list if there are members */
  if (selectedMembers.isNotEmpty()) {
    HorizontalDivider(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = Dimensions.AvatarSize.large.plus(Dimensions.Spacing.xxxxLarge),
                    end = Dimensions.AvatarSize.large.plus(Dimensions.Spacing.xxxxLarge)),
        thickness = Dimensions.DividerThickness.standard,
        color = AppColors.divider)

    /** --- Members List --- */
    Column {
      selectedMembers.forEach { member ->
        /**
         * Row is clickable only if the user can manage members. Added this for testing since
         * disabling clickable still exposes OnClick actions
         */
        val clickableModifier =
            if (!isMember && member.uid != currentAccount.uid) {
              Modifier.clickable { selectedMember = member }
            } else {
              Modifier
            }

        /** --- Each member --- */
        Row(
            modifier =
                clickableModifier
                    .fillMaxWidth()
                    .padding(
                        vertical = Dimensions.Spacing.small,
                        horizontal = Dimensions.Padding.extraMedium)
                    .testTag(UITestTags.memberRowTag(member.uid)),
            verticalAlignment = Alignment.CenterVertically) {

              /** --- Avatar Circle --- */
              Box(
                  modifier =
                      Modifier.size(Dimensions.ButtonSize.medium)
                          .clip(CircleShape)
                          .background(AppColors.primary),
                  contentAlignment = Alignment.Center) {

                    /** First letter of name or A if name is empty */
                    Text(
                        text = member.name.firstOrNull()?.toString() ?: "A",
                        color = AppColors.affirmative,
                        fontWeight = FontWeight.Bold)
                  }
              Spacer(modifier = Modifier.width(Dimensions.Spacing.extraMedium))

              /** Member name takes up remaining space */
              Text(
                  text = member.name,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  color = AppColors.textIcons)

              /** --- Status Badge --- */
              val status =
                  remember(member, discussion.admins) {
                    when {
                      discussion.creatorId == member.uid -> "Owner"
                      discussion.admins.contains(member.uid) -> "Admin"
                      else -> "Member"
                    }
                  }

              /** Badge colors based on status for now */
              val badgeColor =
                  when (status) {
                    "Owner" -> AppColors.affirmative
                    "Admin" -> AppColors.neutral
                    else -> AppColors.secondary
                  }
              /** --- Status Badge --- */
              Box(
                  modifier =
                      Modifier.padding(end = Dimensions.Spacing.medium)
                          .background(
                              badgeColor, shape = RoundedCornerShape(Dimensions.Spacing.large))
                          .padding(
                              horizontal = Dimensions.Padding.extraMedium,
                              vertical = Dimensions.Spacing.small),
                  contentAlignment = Alignment.Center) {
                    /** Badge text */
                    Text(
                        text = status,
                        color = AppColors.textIcons,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                  }
            }
      }
    }
  }

  /** Member options dialog */
  if (selectedMember != null) {
    val selectedIsAdmin = discussion.admins.contains(selectedMember!!.uid)
    val selectedIsOwner = discussion.creatorId == selectedMember!!.uid

    /** Determine if current user can remove selected member */
    val canRemove =
        when {
          discussion.creatorId == currentAccount.uid -> true // Owner can remove anyone
          discussion.admins.contains(currentAccount.uid) && !selectedIsAdmin && !selectedIsOwner ->
              true // Admin can remove members only
          else -> false
        }

    /** --- Dialog to manage selected member --- */
    AlertDialog(
        onDismissRequest = { selectedMember = null },
        containerColor = AppColors.primary,
        title = {
          /** --- Selected Member Info --- */
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(bottom = Dimensions.Spacing.medium)) {
                /** --- Avatar Circle --- */
                Box(
                    modifier =
                        Modifier.size(Dimensions.AvatarSize.medium)
                            .clip(CircleShape)
                            .background(AppColors.primary),
                    contentAlignment = Alignment.Center) {
                      /** First letter of name or A if name is empty */
                      Text(
                          text = selectedMember?.name?.firstOrNull()?.toString() ?: "A",
                          color = AppColors.affirmative,
                          fontWeight = FontWeight.Bold,
                          style = MaterialTheme.typography.titleMedium)
                    }
                Spacer(modifier = Modifier.width(Dimensions.Spacing.extraMedium))
                /** Member name */
                Text(
                    text = selectedMember?.name ?: "",
                    maxLines = 1,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.textIcons)
              }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium)) {
            Text(
                TEXT_MANAGE_MEMBER_PERMISSIONS,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.textIconsFade)

            Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

            /**
             * OWNER or ADMIN can make admin, but only if the target is not already admin or owner
             */
            if (!selectedIsAdmin && !selectedIsOwner) {
              Button(
                  onClick = {
                    viewModel.addAdminToDiscussion(discussion, currentAccount, selectedMember!!)
                    selectedMember = null
                  },
                  modifier = Modifier.fillMaxWidth().testTag(UITestTags.MAKE_ADMIN_BUTTON),
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = AppColors.neutral, contentColor = AppColors.textIcons)) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Spacing.xLarge))
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                    Text(TEXT_MAKE_ADMIN, fontWeight = FontWeight.SemiBold)
                  }
            }

            /** --- Only the owner can remove admin privileges --- */
            if (discussion.creatorId == currentAccount.uid && selectedIsAdmin) {
              OutlinedButton(
                  onClick = {
                    viewModel.removeAdminFromDiscussion(
                        discussion = discussion,
                        admin = selectedMember!!,
                        changeRequester = currentAccount)
                    selectedMember = null
                  },
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Spacing.xLarge))
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                    Text(TEXT_REMOVE_ADMIN, fontWeight = FontWeight.Medium)
                  }
            }

            /** Only owner or admin (with restrictions) can remove members */
            if (canRemove) {
              OutlinedButton(
                  onClick = {
                    viewModel.removeUserFromDiscussion(
                        discussion = discussion,
                        user = selectedMember!!,
                        changeRequester = currentAccount)
                    selectedMembers.remove(selectedMember!!)
                    selectedMember = null
                  },
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.Spacing.xLarge))
                    Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                    Text(TEXT_REMOVE_FROM_GROUP, fontWeight = FontWeight.Medium)
                  }
            }
          }
        },
        confirmButton = {},
        dismissButton = {
          TextButton(onClick = { selectedMember = null }) {
            Text(TEXT_CLOSE, color = AppColors.textIconsFade, fontWeight = FontWeight.Medium)
          }
        })
  }
}

/**
 * Fullscreen dialog for viewing and changing the discussion profile picture.
 *
 * @param discussionName Name of the discussion
 * @param profilePictureUrl Optional URL to the discussion's profile picture
 * @param onDismiss Callback when the dialog is dismissed
 * @param onTakePhoto Callback when the user chooses to take a photo
 * @param onChooseFromGallery Callback when the user chooses to select from gallery
 */
@Composable
fun ProfilePictureDialog(
    discussionName: String,
    profilePictureUrl: String?,
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFromGallery: () -> Unit
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties =
          DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
          // Image or default icon
          if (profilePictureUrl != null) {
            AsyncImage(
                model = profilePictureUrl,
                contentDescription = "Discussion Profile Picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable { onDismiss() })
          } else {
            Box(
                modifier = Modifier.fillMaxSize().clickable { onDismiss() },
                contentAlignment = Alignment.Center) {
                  Icon(
                      imageVector = Icons.Default.AccountCircle,
                      contentDescription = "Default Icon",
                      modifier =
                          Modifier.size(
                              Dimensions.IconSize.massive.times(Dimensions.Multipliers.quadruple)),
                      tint = Color.White.copy(alpha = Dimensions.Alpha.dialogIconTranslucent))
                }
          }
          // Top action bar
          PhotoDialogTopBar(
              Modifier.align(Alignment.TopCenter), text = discussionName, onDismiss = onDismiss)
          // Bottom action bar
          PhotoDialogBottomBar(
              Modifier.align(Alignment.BottomCenter),
              onTakePhoto = onTakePhoto,
              onChooseFromGallery = onChooseFromGallery)
        }
      }
}
