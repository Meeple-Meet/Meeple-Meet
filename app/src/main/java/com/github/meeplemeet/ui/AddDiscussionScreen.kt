package com.github.meeplemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.NavigationActions
import com.github.meeplemeet.ui.theme.AppColors
import kotlinx.coroutines.launch

/**
 * Screen for creating a new discussion with title, description, and selected members.
 *
 * Supports live search for adding members, displays selected members, and allows creating or
 * discarding the discussion.
 *
 * @param navigation NavigationActions to handle navigation events
 * @param viewModel FirestoreViewModel for creating discussions and accessing accounts
 * @param currentUser The currently logged-in user
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDiscussionScreen(
    navigation: NavigationActions,
    viewModel: FirestoreViewModel,
    currentUser: Account
) {
    val scope = rememberCoroutineScope()

    /** State for discussion title and description */
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    /** Live search state for adding members */
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    /** List of members selected for the new discussion */
    val selectedMembers = remember { mutableStateListOf<Account>() }

    /**
     * Handles live search whenever [searchQuery] changes. Filters out the current user and
     * already-selected members.
     */
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            dropdownExpanded = false
            return@LaunchedEffect
        }

        isSearching = true
        /** Simulated backend search */
        searchResults =
            fakeSearchAccounts(searchQuery).filter {
                it.uid != currentUser.uid && it !in selectedMembers
            }

        dropdownExpanded = searchResults.isNotEmpty()
        isSearching = false
    }

    /** Main layout scaffold */
    Scaffold(
        containerColor = AppColors.primary,
        topBar = {
            Column {
                /** App bar with title and back button */
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = AppColors.primary,
                        titleContentColor = AppColors.textIcons,
                        navigationIconContentColor = AppColors.textIcons
                    ),
                    title = { Text(text = "Add Discussion") },
                    navigationIcon = {
                        IconButton(onClick = { navigation.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                /** Decorative divider under the top bar */
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(0.7f)
                        .padding(horizontal = 0.dp)
                        .align(Alignment.CenterHorizontally),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            /** Title input field */
            OutlinedTextField(
                value = title,
                colors = TextFieldDefaults.colors().copy(
                    focusedTextColor = AppColors.textIcons,
                    unfocusedTextColor = AppColors.textIcons,
                    unfocusedIndicatorColor = AppColors.textIcons,
                    focusedIndicatorColor = AppColors.textIcons,
                    unfocusedLabelColor = AppColors.textIconsFade,
                    focusedLabelColor = AppColors.textIconsFade,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodySmall,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** Description input field */
            OutlinedTextField(
                value = description,
                colors = TextFieldDefaults.colors().copy(
                    focusedTextColor = AppColors.textIcons,
                    unfocusedTextColor = AppColors.textIcons,
                    unfocusedIndicatorColor = AppColors.textIcons,
                    focusedIndicatorColor = AppColors.textIcons,
                    unfocusedLabelColor = AppColors.textIconsFade,
                    focusedLabelColor = AppColors.textIconsFade,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodySmall,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            /** Row for search and member selection */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Members:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                /** Autocomplete search field */
                Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        colors = TextFieldDefaults.colors().copy(
                            focusedTextColor = AppColors.textIcons,
                            unfocusedTextColor = AppColors.textIcons,
                            unfocusedIndicatorColor = AppColors.textIcons,
                            focusedIndicatorColor = AppColors.textIcons,
                            unfocusedLabelColor = AppColors.textIconsFade,
                            focusedLabelColor = AppColors.textIconsFade,
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = CircleShape,
                        onValueChange = { searchQuery = it },
                        label = { Text("Add Members") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.clickable {
                                        searchQuery = ""
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    )

                    /** Dropdown menu showing search results */
                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.fillMaxWidth().background(AppColors.divider)
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
                                            searchQuery = ""
                                            dropdownExpanded = false
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

            Spacer(modifier = Modifier.height(20.dp))

            /** Divider between search and selected members */
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.9f)
                    .padding(horizontal = 0.dp)
                    .align(Alignment.CenterHorizontally),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )

            /** Display list of selected members */
            if (selectedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn ( modifier = Modifier.heightIn(max = 200.dp)) {
                    items(selectedMembers) { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            /** Member avatar */
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

                            /** Member name */
                            Text(text = member.name, modifier = Modifier.weight(1f), maxLines = 1, color = AppColors.textIcons,
                                fontStyle = MaterialTheme.typography.bodySmall.fontStyle)

                            /** Remove member button */
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = "Remove",
                                tint = AppColors.negative,
                                modifier = Modifier.clickable { selectedMembers.remove(member) }
                            )
                        }
                    }
                }
            }

            /** Spacer to move buttons higher */
            Spacer(modifier = Modifier.height(24.dp))

            /** Buttons section */
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.createDiscussion(
                                title, description, currentUser, *selectedMembers.toTypedArray()
                            )
                            navigation.goBack() // TODO: navigate to new discussion screen
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(0.5f),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative)
                ) { Text(text = "Create Discussion", style = MaterialTheme.typography.bodySmall) }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { navigation.goBack() },
                    modifier = Modifier.fillMaxWidth(0.3f),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)
                ) { Text(text = "Discard",
                    style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

/**
 * Fake search function to simulate backend account search. Filters a hard-coded list of accounts by
 * the query string.
 *
 * @param query The search query
 * @return List of matching [Account] objects
 */
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
