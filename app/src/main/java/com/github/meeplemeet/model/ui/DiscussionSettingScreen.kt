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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import kotlinx.coroutines.launch

@Composable
fun DiscussionSettingScreen(
    viewModel: FirestoreViewModel,
    currentAccount: Account,
    discussionId: String,
    modifier: Modifier = Modifier
) {
  val coroutineScope = rememberCoroutineScope()
  val discussion by viewModel.discussionFlow(discussionId).collectAsState()

    // 🔎 Search state
    var searchResults by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val selectedMembers = remember { mutableStateListOf<Account>() }

    // Populate selectedMembers with current discussion participants
    LaunchedEffect(discussion?.participants) {
        // Only run when participants list changes (i.e., on discussion load)
        val uids = discussion?.participants.orEmpty()
        for (uid in uids) {
            // Avoid duplicates
            if (selectedMembers.none { it.uid == uid }) {
                val account = viewModel.getAccount(uid)
                selectedMembers.add(account)
            }
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

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
                it.uid != currentAccount.uid && it !in selectedMembers
            }

        dropdownExpanded = searchResults.isNotEmpty()
        isSearching = false
    }


    discussion?.let { d ->
    val isAdmin = d.admins.contains(currentAccount.uid)
    val isOwner = d.creatorId == currentAccount.uid
    val isMember = !isAdmin && !isOwner

    Scaffold(
        topBar = {
          TopBar(
              text = "Discussion Settings",
          /*Todo: navigation and save data*/ )
        },
        bottomBar = {
            OutlinedButton(
                onClick = { if (!isMember) showDeleteDialog = true },
                enabled = !isMember,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 50.dp, vertical = 25.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Discussion for Everyone")
            }
        }
    ) { padding ->
          Column(
              modifier =
                  modifier.padding(padding).padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // --- Name + Description ---
                  var newName by remember { mutableStateOf(d.name) }
                  var newDesc by remember { mutableStateOf(d.description) }

                  Icon(
                      imageVector = Icons.Default.AccountCircle,
                      contentDescription = "Icon",
                      modifier = Modifier.align(Alignment.CenterHorizontally).size(140.dp))

                  TextField(
                      value = newName,
                      onValueChange = { newName = it },
                      readOnly = !isAdmin,
                      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
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
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = if (isAdmin) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background)
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
                      onValueChange = {newDesc = it },
                      readOnly = !isAdmin,
                      modifier = Modifier.fillMaxWidth().padding(start = 0.dp, end = 6.dp),
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
                            tint = if (isAdmin) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background
                        )
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
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

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
                                  viewModel.deleteDiscussion(d, currentAccount)
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
          IconButton(onClick = { onReturn() }) {
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
    modifier: Modifier = Modifier
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Members:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

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
                                modifier = Modifier.clickable {
                                    onSearchQueryChange("")
                                    onDropdownExpandedChange(false)
                                }
                            )
                        }
                    },
                    enabled = true
                )

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { onDropdownExpandedChange(false) },
                    modifier = Modifier.fillMaxWidth().background(Color.White)
                ) {
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
                                            modifier = Modifier.size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color.LightGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = account.name.firstOrNull()?.toString() ?: "A",
                                                color = Color(0xFFFFA000),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(0.dp))


    // ✅ Selected Members
    var selectedMember by remember { mutableStateOf<Account?>(null) }
    if (selectedMembers.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn {
            items(selectedMembers) { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 14.dp)
                        .clickable {
                            if (!isMember) selectedMember = member
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = member.name.firstOrNull()?.toString() ?: "A",
                            color = Color(0xFFFFA000),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = member.name, modifier = Modifier.weight(1f), maxLines = 1)
                    // --- Status Badge ---
                    // Placeholder logic for status: default to "Member"
                    val status = remember(member) {
                        // TODO: Replace with real logic to determine status from roles
                        "Member"
                    }
                    val badgeColor = when (status) {
                        "Owner" -> Color(0xFF4CAF50) // Green
                        "Admin" -> Color(0xFF1976D2) // Blue
                        else -> Color(0xFFB0BEC5) // Gray
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(badgeColor, shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = status,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier =
                    modifier.fillMaxWidth().padding(start = 60.dp, end = 60.dp), // 70% width to create middle effect
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            }
        }
    }

    // Member options dialog
    if (selectedMember != null) {
        AlertDialog(
            onDismissRequest = { selectedMember = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedMember?.name?.firstOrNull()?.toString() ?: "A",
                            color = Color(0xFFFFA000),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectedMember?.name ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {},
            confirmButton = {
                TextButton(
                    onClick = {
                        // todo: Placeholder for make admin action
                        selectedMember = null
                    }
                ) {
                    Text("Make Admin")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            // todo:  Remove from group action (for now, just remove from selectedMembers)
                            selectedMembers.remove(selectedMember!!)
                            selectedMember = null
                        }
                    ) {
                        Text("Remove from Group")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { selectedMember = null }
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
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
                Account("8", "Heidi")
            )
        return allAccounts.filter { it.name.contains(query, ignoreCase = true) }
    }

