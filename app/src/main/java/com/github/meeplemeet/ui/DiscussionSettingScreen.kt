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
import androidx.compose.material.icons.filled.Close
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
import kotlinx.coroutines.launch

/**
 * Displays the discussion settings screen, allowing users to view and edit discussion details,
 * manage members, and perform actions such as deleting or leaving the discussion.
 *
 * @param viewModel The FirestoreViewModel for data operations.
 * @param discussionId The ID of the discussion to manage.
 * @param modifier Modifier for styling this composable.
 */
@Composable
fun DiscussionSettingScreen(
    viewModel: FirestoreViewModel,
    discussionId: String,
    modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()

  /** --- Data states --- */
  val discussion by viewModel.discussionFlow(discussionId).collectAsState()
  val currentAccount by viewModel.account.collectAsState()

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
  LaunchedEffect(discussion?.participants) {
    val uids = discussion?.participants.orEmpty()
    selectedMembers.clear()
    for (uid in uids) {
      viewModel.getOtherAccount(uid) { acc ->
        if (selectedMembers.none { it.uid == acc.uid }) {
          selectedMembers.add(acc)
        }
      }
    }
    /** Add current account only if not already present */
    currentAccount?.let {
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
    /** Placeholder for backend search */
    searchResults =
        fakeSearchAccounts(searchQuery).filter {
          it.uid != currentAccount!!.uid && it !in selectedMembers
        }

    dropdownExpanded = searchResults.isNotEmpty()
    isSearching = false
  }

  discussion?.let { d ->
    val isAdmin = d.admins.contains(currentAccount!!.uid)
    val isOwner = d.creatorId == currentAccount!!.uid
    val isMember = !isAdmin && !isOwner

    /** --- Name + Description --- */
    var newName by remember { mutableStateOf(d.name) }
    var newDesc by remember { mutableStateOf(d.description) }

    Scaffold(
        topBar = {
          TopBar(
              text = "Discussion Settings",
              /** save Name and Description on back */
              onReturn = {
                viewModel.setDiscussionName(
                    discussion = d, name = newName, changeRequester = currentAccount!!)
                viewModel.setDiscussionDescription(
                    discussion = d, description = newDesc, changeRequester = currentAccount!!)
              }
              /*Todo: navigation back*/ )
        },
        bottomBar = {
          Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 25.dp),
              horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                /** Delete button only if not member */
                OutlinedButton(
                    onClick = { if (!isMember) showDeleteDialog = true },
                    enabled = !isMember,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).testTag("delete_button")) {
                      Icon(
                          imageVector = Icons.Default.Delete,
                          contentDescription = null,
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("Delete Discussion")
                    }
                /** Leave button is always enabled */
                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    enabled = true,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.weight(1f).testTag("leave_button")) {
                      Text("Leave Discussion")
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
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(140.dp))

                /** --- Discussion Name --- */
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag("discussion_name"),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor =
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    /**
                     * To make the text centered, we use an invisible leading icon to offset the
                     * trailing icon
                     */
                    leadingIcon = {
                      Icon(
                          imageVector = Icons.Default.Edit,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.background // Make it invisible
                          )
                    },
                    /** Trailing edit icon only if admin */
                    trailingIcon = {
                      Icon(
                          imageVector = Icons.Default.Edit,
                          contentDescription = "Edit",
                          tint =
                              if (isAdmin) MaterialTheme.colorScheme.onSurface
                              else MaterialTheme.colorScheme.background)
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
                )

                /** --- Description TextField --- */
                TextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    readOnly = !isAdmin,
                    enabled = isAdmin,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = 0.dp, end = 6.dp)
                            .testTag("discussion_description"),
                    /** Makes the textField look like a line */
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedIndicatorColor = MaterialTheme.colorScheme.background,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.background,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface),
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
                          tint =
                              if (isAdmin) MaterialTheme.colorScheme.onSurface
                              else MaterialTheme.colorScheme.background)
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

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
                    currentAccount = currentAccount!!,
                    discussion = d)

                /** --- Delete Discussion (confirm dialog) --- */
                if (showDeleteDialog) {
                  AlertDialog(
                      onDismissRequest = { showDeleteDialog = false },
                      title = { Text("Delete Discussion") },
                      modifier = Modifier.testTag("delete_discussion_display"),
                      text = {
                        Text(
                            "Are you sure you want to delete ${d.name}? This action cannot be undone.")
                      },
                      confirmButton = {
                        TextButton(
                            /** Only owner can delete */
                            onClick = {
                              coroutineScope.launch {
                                /*Todo: navigation to other screen after deletion*/
                                viewModel.deleteDiscussion(d, currentAccount!!)
                              }
                              showDeleteDialog = false
                            }) {
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
                      modifier = Modifier.testTag("leave_discussion_display"),
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
                                viewModel.removeUserFromDiscussion(
                                    d, currentAccount!!, currentAccount!!)
                                /*Todo: navigation to next screen after leaving the group*/
                              }
                              showLeaveDialog = false
                            }) {
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
fun TopBar(text: String, onReturn: () -> Unit = {}) {
  /** --- Top App Bar --- */
  Column {
    CenterAlignedTopAppBar(
        navigationIcon = {
          IconButton(onClick = { onReturn() }, modifier = Modifier.testTag("back_button")) {
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(
              text = text,
              style = MaterialTheme.typography.headlineSmall,
          )
        },
        actions = { /* Add trailing icons here if needed */},
        colors =
            TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background),
    )
    /** --- Divider --- */
    HorizontalDivider(
        modifier =
            Modifier.fillMaxWidth(0.7f) // 70% width to create middle effect
                .padding(horizontal = 0.dp)
                .align(Alignment.CenterHorizontally),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
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

  /** --- Members Header + Search Field --- */
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = "Members:",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold)

    /** Autocomplete Search Field + Dropdown (only for non-members) */
    if (!isMember) {

      /** Spacer between title and search field */
      Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {

        /** --- Search Field --- */
        OutlinedTextField(
            value = searchQuery,
            shape = RoundedCornerShape(28.dp),
            onValueChange = onSearchQueryChange,
            label = { Text("Add Members") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
              if (searchQuery.isNotBlank()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear",
                    modifier =
                        Modifier.clickable {
                          onSearchQueryChange("")
                          onDropdownExpandedChange(false)
                        })
              }
            },
            enabled = true)

        /** --- Dropdown Menu for search results --- */
        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { onDropdownExpandedChange(false) },
            modifier = Modifier.fillMaxWidth().background(Color.White)) {
              when {
                isSearching -> {
                  DropdownMenuItem(text = { Text("Searching...") }, onClick = {})
                }
                searchResults.isEmpty() -> {
                  DropdownMenuItem(text = { Text("No results") }, onClick = {})
                }
                else -> {
                  searchResults.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                          selectedMembers.add(account)
                          onSearchQueryChange("")
                          onDropdownExpandedChange(false)
                        },
                        leadingIcon = {
                          Box(
                              modifier =
                                  Modifier.size(32.dp)
                                      .clip(CircleShape)
                                      .background(Color.LightGray),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    text = account.name.firstOrNull()?.toString() ?: "A",
                                    color = Color(0xFFFFA000),
                                    fontWeight = FontWeight.Bold)
                              }
                        })
                  }
                }
              }
            }
      }
    }
  }
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
                  modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray),
                  contentAlignment = Alignment.Center) {

                    /** First letter of name or A if name is empty */
                    Text(
                        text = member.name.firstOrNull()?.toString() ?: "A",
                        color = Color(0xFFFFA000),
                        fontWeight = FontWeight.Bold)
                  }
              Spacer(modifier = Modifier.width(12.dp))

              /** Member name takes up remaining space */
              Text(text = member.name, modifier = Modifier.weight(1f), maxLines = 1)

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
                    "Owner" -> Color(0xFF4CAF50) // Green
                    "Admin" -> Color(0xFF1976D2) // Blue
                    else -> Color(0xFFB0BEC5) // Gray
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
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                  }
            }
      }
      item {
        /** --- Divider after the list --- */
        HorizontalDivider(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp, end = 60.dp), // 70% width to create middle effect
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
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
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray),
                contentAlignment = Alignment.Center) {
                  /** First letter of name or A if name is empty */
                  Text(
                      text = selectedMember?.name?.firstOrNull()?.toString() ?: "A",
                      color = Color(0xFFFFA000),
                      fontWeight = FontWeight.Bold)
                }
            Spacer(modifier = Modifier.width(12.dp))
            /** Member name */
            Text(
                text = selectedMember?.name ?: "",
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
          }
        },
        text = {},
        confirmButton = {
          /** OWNER or ADMIN can make admin, but only if the target is not already admin or owner */
          if (!selectedIsAdmin && !selectedIsOwner) {
            TextButton(
                onClick = {
                  viewModel.addAdminToDiscussion(
                      discussion = discussion,
                      admin = selectedMember!!,
                      changeRequester = currentAccount)
                  selectedMember = null
                }) {
                  Text("Make Admin", modifier = Modifier.testTag("make_admin_button"))
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
                    Text("Remove from Group")
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
                    Text("Remove Admin")
                  }
            }
            /** Cancel button */
            TextButton(onClick = { selectedMember = null }) { Text("Cancel") }
          }
        })
  }
}
