package com.github.meeplemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Discussion
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
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
}

/**
 * Displays the discussion infos screen, allowing users to view and edit discussion details, manage
 * members, and perform actions such as deleting or leaving the discussion.
 *
 * @param viewModel The FirestoreViewModel for data operations.
 * @param discussion The discussion to manage.
 * @param modifier Modifier for styling this composable.
 */
@Composable
fun DiscussionDetailsScreen(
    viewModel: FirestoreViewModel,
    account: Account,
    discussion: Discussion,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onLeave: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
  val coroutineScope = rememberCoroutineScope()

  /** --- Search state --- */
  var searchResults by remember { mutableStateOf<List<Account>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var dropdownExpanded by remember { mutableStateOf(false) }

  val selectedMembers = remember { mutableStateListOf<Account>() }

  /** delete and leave alert state */
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showLeaveDialog by remember { mutableStateOf(false) }

  /** Populate selectedMembers with current discussion participants */
  LaunchedEffect(discussion.participants) {
    val uids = discussion.participants
    selectedMembers.clear()
    for (uid in uids) {
      viewModel.getOtherAccount(uid) { acc ->
        if (selectedMembers.none { it.uid == acc.uid }) {
          selectedMembers.add(acc)
        }
      }
    }
    /** Add current account only if not already present */
    account.let {
      if (selectedMembers.none { member -> member.uid == it.uid }) {
        selectedMembers.add(it)
      }
    }
  }

  /** Live search effect */
  LaunchedEffect(searchQuery) {
    if (searchQuery.isBlank()) {
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

  discussion.let { d ->
    val isAdmin = d.admins.contains(account.uid)
    val isOwner = d.creatorId == account.uid
    val isMember = !isAdmin && !isOwner

    /** --- Name + Description --- */
    var newName by remember { mutableStateOf(d.name) }
    var newDesc by remember { mutableStateOf(d.description) }

    Scaffold(
        topBar = {
          TopBarWithDivider(
              text = d.name,
              /**
               * Save Name and Description on back — this is the only time the DB is updated here
               */
              onReturn = {
                if (discussion.admins.contains(account.uid)) {
                  viewModel.setDiscussionName(
                      discussion = d, name = newName, changeRequester = account)
                  viewModel.setDiscussionDescription(
                      discussion = d, description = newDesc, changeRequester = account)
                }
                onBack()
              })
        },
        bottomBar = {
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 25.dp),
              horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                /** The actual deletion happens only after the confirmation dialog */
                /** Delete button only if not member */
                if (discussion.creatorId == account.uid)
                    OutlinedButton(
                        onClick = { if (!isMember) showDeleteDialog = true },
                        enabled = !isMember,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.negative),
                        modifier = Modifier.weight(1f).testTag(UITestTags.DELETE_BUTTON)) {
                          Icon(
                              imageVector = Icons.Default.Delete,
                              contentDescription = null,
                              tint = AppColors.textIcons)
                          Spacer(modifier = Modifier.width(8.dp))
                          Text("Delete Discussion", color = AppColors.textIcons)
                        }
                /** The actual leave operation happens only after the confirmation dialog */
                /** Leave button is always enabled */
                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative),
                    modifier = Modifier.weight(1f).testTag(UITestTags.LEAVE_BUTTON)) {
                      Text("Leave Discussion", color = AppColors.textIcons)
                    }
              }
        }) { padding ->

          /** --- Main Content --- */
          Column(
              modifier = modifier.padding(padding).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)) {

                /** --- Discussion Icon --- */
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Icon",
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(140.dp),
                    tint = AppColors.textIcons)

                /** --- Discussion Name --- */
                /** These ensure only admins can edit the name field */
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag(UITestTags.DISCUSSION_NAME),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
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
                          contentDescription = "Edit",
                          tint = if (isAdmin) AppColors.textIcons else Color.Transparent)
                    },
                    textStyle =
                        LocalTextStyle.current.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            textAlign = TextAlign.Center,
                        ),
                )

                /** --- Discussion Description --- */
                Text(
                    text = "Description:",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            textDecoration = TextDecoration.Underline),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    color = AppColors.textIcons)

                /** --- Description TextField --- */
                TextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 0.dp, end = 6.dp)
                            .testTag(UITestTags.DISCUSSION_DESCRIPTION),
                    /** Makes the textField look like a line */
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = AppColors.textIcons,
                            unfocusedIndicatorColor = AppColors.textIconsFade,
                            cursorColor = AppColors.textIcons,
                            focusedTextColor = AppColors.textIcons,
                            unfocusedTextColor = AppColors.textIcons),
                    singleLine = true,
                    /**
                     * To make the text left-aligned, we use an invisible leading icon to offset the
                     * trailing icon
                     */
                    trailingIcon = {
                      Icon(
                          imageVector = Icons.Default.Edit,
                          contentDescription = "Edit",
                          modifier = Modifier,
                          tint = if (isAdmin) AppColors.textIcons else Color.Transparent)
                    },
                    textStyle =
                        LocalTextStyle.current.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                            textAlign = TextAlign.Start),
                )

                /** --- Divider --- */
                HorizontalDivider(
                    modifier =
                        Modifier.fillMaxWidth(0.945f) // 70% width to create middle effect
                            .padding(horizontal = 0.dp)
                            .align(Alignment.CenterHorizontally),
                    thickness = 1.75.dp,
                    color = AppColors.divider)

                /** Row for search and member selection */
                if (discussion.admins.contains(account.uid))
                    MemberSearchField(
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        searchResults = searchResults,
                        isSearching = isSearching,
                        dropdownExpanded = dropdownExpanded,
                        onDismiss = { dropdownExpanded = false },
                        onSelect = { account ->
                          selectedMembers.add(account)
                          viewModel.addUserToDiscussion(discussion, account, account)
                          searchQuery = ""
                          dropdownExpanded = false
                        })

                /** --- Members List --- */
                MemberList(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedMembers = selectedMembers,
                    searchResults = searchResults,
                    dropdownExpanded = dropdownExpanded,
                    onDropdownExpandedChange = { dropdownExpanded = it },
                    isSearching = isSearching,
                    isMember = isMember,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    viewModel = viewModel,
                    currentAccount = account,
                    discussion = d)

                /** --- Delete Discussion (confirm dialog) --- */
                if (showDeleteDialog) {
                  AlertDialog(
                      onDismissRequest = { showDeleteDialog = false },
                      title = { Text("Delete Discussion") },
                      modifier = Modifier.testTag(UITestTags.DELETE_DISCUSSION_DISPLAY),
                      text = {
                        Text(
                            "Are you sure you want to delete ${d.name}? This action cannot be undone.")
                      },
                      confirmButton = {
                        TextButton(
                            /** Only owner can delete */
                            onClick = {
                              coroutineScope.launch {
                                viewModel.deleteDiscussion(d, account)
                                onDelete()
                              }
                              showDeleteDialog = false
                            },
                            modifier =
                                Modifier.testTag(UITestTags.DELETE_DISCUSSION_CONFIRM_BUTTON)) {
                              Text("Delete")
                            }
                      },
                      dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                      })
                }
                /** --- Leave Discussion (confirm dialog) --- */
                if (showLeaveDialog) {
                  AlertDialog(
                      onDismissRequest = { showLeaveDialog = false },
                      title = { Text("Leave Discussion") },
                      modifier = Modifier.testTag(UITestTags.LEAVE_DISCUSSION_DISPLAY),
                      text = {
                        Text(
                            "Are you sure you want to leave ${d.name}? You will no longer see messages or members.")
                      },
                      confirmButton = {
                        TextButton(
                            /** Everyone can leave */
                            onClick = {
                              coroutineScope.launch {

                                /** leave discussion */
                                viewModel.removeUserFromDiscussion(d, account, account)
                                onLeave()
                              }
                              showLeaveDialog = false
                            },
                            modifier =
                                Modifier.testTag(UITestTags.LEAVE_DISCUSSION_CONFIRM_BUTTON)) {
                              Text("Leave")
                            }
                      },
                      dismissButton = {
                        TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
                      })
                }
              }
        }
  }
}

/**
 * Displays a top app bar with a title and a back button.
 *
 * @param text The title text to display in the top bar.
 * @param onReturn Callback invoked when the back button is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithDivider(
    text: String,
    onReturn: () -> Unit = {},
    trailingIcons: @Composable () -> Unit = {}
) {
  /** --- Top App Bar --- */
  Column {
    CenterAlignedTopAppBar(
        navigationIcon = {
          IconButton(
              onClick = { onReturn() },
              modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.textIcons)
              }
        },
        title = {
          Text(
              text = text,
              modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE),
              style = MaterialTheme.typography.bodyMedium,
              color = AppColors.textIcons)
        },
        actions = { /* Add trailing icons here if needed */
          trailingIcons()
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = AppColors.primary))
    /** --- Divider --- */
    HorizontalDivider(
        modifier =
            Modifier.fillMaxWidth(0.7f) // 70% width to create middle effect
                .padding(horizontal = 0.dp)
                .align(Alignment.CenterHorizontally),
        thickness = 1.dp,
        color = AppColors.textIconsFade)
  }
}

/**
 * Displays the list of discussion members and allows searching, adding, and managing members.
 *
 * @param searchQuery The current search query for member autocomplete.
 * @param onSearchQueryChange Callback invoked when the search query changes.
 * @param selectedMembers The list of currently selected (discussion) members.
 * @param searchResults The list of accounts matching the search query.
 * @param dropdownExpanded Whether the dropdown for search results is expanded.
 * @param onDropdownExpandedChange Callback to update dropdown expanded state.
 * @param isSearching Whether a search is currently in progress.
 * @param isMember Whether the current user is a regular member (not admin/owner).
 * @param modifier Modifier for styling this composable.
 * @param viewModel The FirestoreViewModel for data operations.
 * @param currentAccount The account of the current user.
 * @param discussion The current discussion object.
 */
@Composable
fun MemberList(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedMembers: MutableList<Account>,
    searchResults: List<Account>,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    isSearching: Boolean,
    isMember: Boolean,
    modifier: Modifier = Modifier,
    viewModel: FirestoreViewModel = viewModel(),
    currentAccount: Account,
    discussion: Discussion,
) {
  /** Small spacer between search field and list */
  Spacer(modifier = Modifier.height(0.dp))

  /** Selected Members */
  var selectedMember by remember { mutableStateOf<Account?>(null) }

  /** Only show the list if there are members */
  if (selectedMembers.isNotEmpty()) {
    Spacer(modifier = Modifier.height(4.dp))

    /** --- Members List --- */
    LazyColumn {
      items(selectedMembers) { member ->
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
                    .padding(vertical = 4.dp, horizontal = 14.dp)
                    .testTag("member_row_${member.uid}"),
            verticalAlignment = Alignment.CenterVertically) {

              /** --- Avatar Circle --- */
              Box(
                  modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.primary),
                  contentAlignment = Alignment.Center) {

                    /** First letter of name or A if name is empty */
                    Text(
                        text = member.name.firstOrNull()?.toString() ?: "A",
                        color = AppColors.affirmative,
                        fontWeight = FontWeight.Bold)
                  }
              Spacer(modifier = Modifier.width(12.dp))

              /** Member name takes up remaining space */
              Text(
                  text = member.name,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  color = AppColors.textIcons)

              /** --- Status Badge --- */
              val status =
                  remember(member) {
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
                      Modifier.padding(end = 8.dp)
                          .background(badgeColor, shape = RoundedCornerShape(12.dp))
                          .padding(horizontal = 10.dp, vertical = 4.dp),
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
      item {
        /** --- Divider after the list --- */
        HorizontalDivider(
            modifier = modifier.fillMaxWidth().padding(start = 60.dp, end = 60.dp),
            thickness = 1.dp,
            color = AppColors.divider)
      }
    }
  }

  /** Member options dialog */
  if (selectedMember != null) {
    val selectedIsAdmin = discussion.admins.contains(selectedMember!!.uid)
    val selectedIsOwner = discussion.creatorId == selectedMember!!.uid

    /** --- Dialog to manage selected member --- */
    AlertDialog(
        onDismissRequest = { selectedMember = null },
        title = {

          /** --- Selected Member Info --- */
          Row(verticalAlignment = Alignment.CenterVertically) {

            /** --- Avatar Circle --- */
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.primary),
                contentAlignment = Alignment.Center) {
                  /** First letter of name or A if name is empty */
                  Text(
                      text = selectedMember?.name?.firstOrNull()?.toString() ?: "A",
                      color = AppColors.affirmative,
                      fontWeight = FontWeight.Bold)
                }
            Spacer(modifier = Modifier.width(12.dp))
            /** Member name */
            Text(
                text = selectedMember?.name ?: "",
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.textIcons)
          }
        },
        text = {},
        confirmButton = {
          /** OWNER or ADMIN can make admin, but only if the target is not already admin or owner */
          if (!selectedIsAdmin && !selectedIsOwner) {
            TextButton(
                onClick = {
                  viewModel.addAdminToDiscussion(discussion, currentAccount, selectedMember!!)
                  selectedMember = null
                }) {
                  Text(
                      "Make Admin",
                      modifier = Modifier.testTag(UITestTags.MAKE_ADMIN_BUTTON),
                      color = AppColors.textIcons)
                }
          }
        },
        dismissButton = {
          Row {
            /** Determine if current user can remove selected member */
            val canRemove =
                when {
                  discussion.creatorId == currentAccount.uid -> true // Owner can remove anyone
                  discussion.admins.contains(currentAccount.uid) &&
                      !selectedIsAdmin &&
                      !selectedIsOwner -> true // Admin can remove members only
                  else -> false
                }

            /** Only owner or admin (with restrictions) can remove members */
            if (canRemove) {
              TextButton(
                  onClick = {
                    viewModel.removeUserFromDiscussion(
                        discussion = discussion,
                        user = selectedMember!!,
                        changeRequester = currentAccount)
                    selectedMembers.remove(selectedMember!!)
                    selectedMember = null
                  }) {
                    Text("Remove from Group", color = AppColors.negative)
                  }
            }

            Spacer(modifier = Modifier.width(8.dp))

            /** --- Only the owner can remove admin privileges --- */
            if (discussion.creatorId == currentAccount.uid && selectedIsAdmin) {
              TextButton(
                  onClick = {
                    viewModel.removeAdminFromDiscussion(
                        discussion = discussion,
                        admin = selectedMember!!,
                        changeRequester = currentAccount)
                    selectedMember = null
                  }) {
                    Text("Remove Admin", color = AppColors.negative)
                  }
            }
            /** Cancel button */
            TextButton(onClick = { selectedMember = null }) {
              Text("Cancel", color = AppColors.textIconsFade)
            }
          }
        })
  }
}
