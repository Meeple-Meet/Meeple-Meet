package com.github.meeplemeet.model.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionSettingScreen(
    viewModel: FirestoreViewModel,
    currentAccount: Account,
    discussionId: String,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val discussion by viewModel.discussionFlow(discussionId).collectAsState()

    discussion?.let { d ->
        val isAdmin = d.admins.contains(currentAccount.uid)
        val isOwner = d.admins.firstOrNull() == currentAccount.uid

        Scaffold(
            topBar = { TopBar(text = "Discussion Settings") }
        ) { padding ->
            Column(
                modifier = modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {


                // --- Name + Description ---
                if (isAdmin) {
                    var newName by remember { mutableStateOf(d.name) }
                    var newDesc by remember { mutableStateOf(d.description) }

                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Icon",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(140.dp)

                    )

                    TextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        // to make the text centered, we use an invisible leading icon to offset the trailing icon
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
                                contentDescription = "Edit"
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize, textAlign = TextAlign.Center, ),

                    )
                    Text(
                        text = "Description:",
                        style = MaterialTheme.typography.titleLarge.copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                    TextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 0.dp, end = 6.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedIndicatorColor = MaterialTheme.colorScheme.background,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.background,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        // to make the text centered, we use an invisible leading icon to offset the trailing icon
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                modifier = Modifier
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize, textAlign = TextAlign.Start),
                        )
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth(0.945f) // 70% width to create middle effect
                            .padding(horizontal = 0.dp)
                            .align(Alignment.CenterHorizontally),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.setDiscussionName(d, currentAccount, newName)
                                viewModel.setDiscussionDescription(d, currentAccount, newDesc)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Changes") }
                } else {
                    Text(text = d.name, style = MaterialTheme.typography.headlineSmall)
                    Text(text = d.description, style = MaterialTheme.typography.bodyMedium)
                }

                // --- Member List ---
                Text("Members", style = MaterialTheme.typography.titleMedium)
                d.participants.forEach { uid ->
                    val isParticipantAdmin = d.admins.contains(uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = uid) // placeholder: replace with account name lookup
                            if (isParticipantAdmin) {
                                Text("(Admin)", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // --- Admin & Owner Controls ---
                        if (isAdmin && uid != currentAccount.uid) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {

                                // Owner can remove admins
                                if (isOwner && isParticipantAdmin) {
                                    TextButton(onClick = {
                                        coroutineScope.launch {
                                            /* TODO: remove Admin as Owner viewModel.removeAdminFromDiscussion(d, uid) */
                                        }
                                    }) {
                                        Text("Remove Admin")
                                    }
                                }

                                // Admins can promote others
                                if (!isParticipantAdmin && !isOwner) {
                                    TextButton(onClick = {
                                        coroutineScope.launch {
//                                            viewModel.addAdminToDiscussion(d, uid)
                                        }
                                    }) {
                                        Text("Make Admin")
                                    }
                                }

                                // Both Admins and Owners can remove users
                                TextButton(onClick = {
                                    coroutineScope.launch {
//                                        viewModel.removeUserFromDiscussion(d, uid)
                                    }
                                }) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }

                // --- Admin-only actions ---
                if (isAdmin) {
                    Button(
                        onClick = { /* TODO: open user search dialog */ },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Participant") }

                    Button(
                        onClick = { /* TODO: open admin selection dialog */ },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add Admin") }

                    // --- Delete Discussion (confirm dialog) ---
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete Discussion for Everyone") }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Delete Discussion") },
                            text = { Text("Are you sure you want to delete ${d.name}? This action cannot be undone.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    coroutineScope.launch {
                                        viewModel.deleteDiscussion(d, currentAccount)
                                    }
                                    showDeleteDialog = false
                                }) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(text: String) {
    Column {
        CenterAlignedTopAppBar(
            navigationIcon = {
                IconButton(onClick = { /* TODO: handle back navigation */ }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            title = {
                Text(
                    text = "Discussion Settings",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            actions = { /* Add trailing icons here if needed */ },
            colors = TopAppBarDefaults.mediumTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),

            )
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(0.7f) // 70% width to create middle effect
                .padding(horizontal = 0.dp)
                .align(Alignment.CenterHorizontally),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}
