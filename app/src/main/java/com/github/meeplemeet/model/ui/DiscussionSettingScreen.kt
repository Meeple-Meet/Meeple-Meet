package com.github.meeplemeet.model.ui

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun DiscussionSettingScreen(
    viewModel: FirestoreViewModel,
    discussionId: String,
    modifier: Modifier = Modifier,
    // Optional flow providers for testing
    accountFlowProvider: () -> StateFlow<Account?> = { viewModel.account },
    discussionFlowProvider: (String) -> StateFlow<Discussion?> = { viewModel.discussionFlow(it) }
) {
  val coroutineScope = rememberCoroutineScope()
  val currentAccount by accountFlowProvider().collectAsState()

  // --- Data states ---
  val discussion by discussionFlowProvider(discussionId).collectAsState()
  val account by accountFlowProvider().collectAsState()

  // 🔎 Search state
  var searchResults by remember { mutableStateOf<List<Account>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var dropdownExpanded by remember { mutableStateOf(false) }

  val selectedMembers = remember { mutableStateListOf<Account>() }

    // delete and leave alert state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }

  // Populate selectedMembers with current discussion participants
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
    // Add current account only if not already present
    account?.let {
      if (selectedMembers.none { member -> member.uid == it.uid }) {
        selectedMembers.add(it)
      }
    }
  }

  // ⌨️ Live search effect
  LaunchedEffect(searchQuery) {
    if (searchQuery.isBlank()) {
      searchResults = emptyList()
      dropdownExpanded = false
      return@LaunchedEffect
    }

    isSearching = true
    // 🚧 Placeholder for backend search
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

    // --- Name + Description ---
    var newName by remember { mutableStateOf(d.name) }
    var newDesc by remember { mutableStateOf(d.description) }

    Scaffold(
        topBar = {
          TopBar(
              text = "Discussion Settings",
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
                OutlinedButton(
                    onClick = { if (!isMember) showDeleteDialog = true },
                    enabled = !isMember,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("delete_button")) {
                      Icon(
                          imageVector = Icons.Default.Delete,
                          contentDescription = null,
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text("Delete Discussion")
                    }
                OutlinedButton(
                    onClick = { showLeaveDialog = true },
                    enabled = true,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("leave_button")) {
                      Text("Leave Discussion")
                    }
              }
        }) { padding ->
          Column(
              modifier = modifier.padding(padding).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Icon",
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(140.dp))

                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    readOnly = !isAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
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
                    // to make the text centered, we use an invisible leading icon to offset the
                    // trailing icon
                    leadingIcon = {
                      Icon(
                          imageVector = Icons.Default.Edit,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.background // Make it invisible
                          )
                    },
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
                Text(
                    text = "Description:",
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            textDecoration = TextDecoration.Underline),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                )
                TextField(
                    value = newDesc,
                    onValueChange = { newDesc = it },
                    readOnly = !isAdmin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 0.dp, end = 6.dp)
                        .testTag("discussion_description"),
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
                HorizontalDivider(
                    modifier =
                        Modifier.fillMaxWidth(0.945f) // 70% width to create middle effect
                            .padding(horizontal = 0.dp)
                            .align(Alignment.CenterHorizontally),
                    thickness = 1.75.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))

                // --- Members List ---
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

                // --- Delete Discussion (confirm dialog) ---
                if (showDeleteDialog) {
                  AlertDialog(
                      onDismissRequest = { showDeleteDialog = false },
                      title = { Text("Delete Discussion") },
                      text = {
                        Text(
                            "Are you sure you want to delete ${d.name}? This action cannot be undone.")
                      },
                      confirmButton = {
                        TextButton(
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
                // --- Leave Discussion (confirm dialog) ---
                if (showLeaveDialog) {
                  AlertDialog(
                      onDismissRequest = { showLeaveDialog = false },
                      title = { Text("Leave Discussion") },
                      text = {
                        Text(
                            "Are you sure you want to leave ${d.name}? You will no longer see messages or members.")
                      },
                      confirmButton = {
                        TextButton(
                            onClick = {
                              coroutineScope.launch {

                                /* leave discussion */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(text: String, onReturn: () -> Unit = {}) {
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
    HorizontalDivider(
        modifier =
            Modifier.fillMaxWidth(0.7f) // 70% width to create middle effect
                .padding(horizontal = 0.dp)
                .align(Alignment.CenterHorizontally),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
  }
}

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

  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = "Members:",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold)

    // 🔎 Autocomplete Search Field + Dropdown (only for non-members)
    if (!isMember) {
      Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
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

  Spacer(modifier = Modifier.height(0.dp))

  // Selected Members
  var selectedMember by remember { mutableStateOf<Account?>(null) }
  if (selectedMembers.isNotEmpty()) {
    Spacer(modifier = Modifier.height(4.dp))
    LazyColumn {
      items(selectedMembers) { member ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 14.dp)
                    .testTag("member_row_${member.uid}")
                    .clickable(
                        enabled = !isMember && member.uid != currentAccount.uid) {
                          if (member.uid != currentAccount.uid) selectedMember = member
                        },
            verticalAlignment = Alignment.CenterVertically) {
              Box(
                  modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray),
                  contentAlignment = Alignment.Center) {
                    Text(
                        text = member.name.firstOrNull()?.toString() ?: "A",
                        color = Color(0xFFFFA000),
                        fontWeight = FontWeight.Bold)
                  }
              Spacer(modifier = Modifier.width(12.dp))
              Text(text = member.name, modifier = Modifier.weight(1f), maxLines = 1)
              // --- Status Badge ---
              val status =
                  remember(member) {
                    when {
                      discussion.creatorId == member.uid -> "Owner"
                      discussion.admins.contains(member.uid) -> "Admin"
                      else -> "Member"
                    }
                  }

              // Badge colors based on status for now
              val badgeColor =
                  when (status) {
                    "Owner" -> Color(0xFF4CAF50) // Green
                    "Admin" -> Color(0xFF1976D2) // Blue
                    else -> Color(0xFFB0BEC5) // Gray
                  }
              Box(
                  modifier =
                      Modifier.padding(end = 8.dp)
                          .background(badgeColor, shape = RoundedCornerShape(12.dp))
                          .padding(horizontal = 10.dp, vertical = 4.dp),
                  contentAlignment = Alignment.Center) {
                    Text(
                        text = status,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                  }
            }
      }
      item {
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

  // Member options dialog
  if (selectedMember != null) {
    val selectedIsAdmin = discussion.admins.contains(selectedMember!!.uid)
    val selectedIsOwner = discussion.creatorId == selectedMember!!.uid

    AlertDialog(
        onDismissRequest = { selectedMember = null },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.LightGray),
                contentAlignment = Alignment.Center) {
                  Text(
                      text = selectedMember?.name?.firstOrNull()?.toString() ?: "A",
                      color = Color(0xFFFFA000),
                      fontWeight = FontWeight.Bold)
                }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selectedMember?.name ?: "",
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
          }
        },
        text = {},
        confirmButton = {
          // OWNER or ADMIN can make admin, but only if the target is not already admin or owner
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
            // Determine if current user can remove selected member
            val canRemove =
                when {
                  discussion.creatorId == currentAccount.uid -> true // Owner can remove anyone
                  discussion.admins.contains(currentAccount.uid) &&
                      !selectedIsAdmin &&
                      !selectedIsOwner -> true // Admin can remove members only
                  else -> false
                }

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

            // Only the owner can remove admin privileges
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

            TextButton(onClick = { selectedMember = null }) { Text("Cancel") }
          }
        })
  }
}

// Fake search function
fun fakeSearchAccounts(query: String): List<Account> {
  val allAccounts =
      listOf(
          Account("1", "Alice"),
          Account("2", "Bob"),
          Account("3", "Charlie"),
          Account("4", "David"),
          Account("5", "Eve"),
          Account("6", "Frank"),
          Account("7", "Grace"),
          Account("8", "Heidi"))
  return allAccounts.filter { it.name.contains(query, ignoreCase = true) }
}
