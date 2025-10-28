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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import kotlinx.coroutines.launch

object AddDiscussionTestTags {
  const val ADD_TITLE = "Add Title"
  const val ADD_DESCRIPTION = "Add Description"
  const val ADD_MEMBERS = "Add Members"
  const val CREATE_DISCUSSION_BUTTON = "Create Discussion"
  const val ADD_MEMBERS_ELEMENT = "Add Member Element"
}

/**
 * Screen for creating a new discussion with title, description, and selected members.
 *
 * Navigation is now decoupled using callbacks: [onBack] and [onCreate].
 *
 * @param onBack Lambda called when back/discard is pressed
 * @param onCreate Lambda called when creation is successful
 * @param viewModel FirestoreViewModel for creating discussions
 * @param account The currently logged-in user
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDiscussionScreen(
    account: Account,
    viewModel: FirestoreViewModel = viewModel(),
    onBack: () -> Unit = {},
    onCreate: () -> Unit = {},
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

  var isCreating by remember { mutableStateOf(false) }
  var creationError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(searchQuery) {
    if (searchQuery.isBlank()) {
      searchResults = emptyList()
      dropdownExpanded = false
      isSearching = false
      return@LaunchedEffect
    }
    isSearching = true
    viewModel.searchByHandle(searchQuery)
  }

  LaunchedEffect(viewModel.handleSuggestions) {
    viewModel.handleSuggestions.collect { list ->
      searchResults = list.filter { it.uid != account.uid && it !in selectedMembers }
      dropdownExpanded = searchResults.isNotEmpty() && searchQuery.isNotBlank()
      isSearching = false
    }
  }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(creationError) {
    creationError?.let {
      snackbarHostState.showSnackbar(it)
      creationError = null
    }
  }

  Scaffold(
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      containerColor = AppColors.primary,
      topBar = {
        Column {
          CenterAlignedTopAppBar(
              colors =
                  TopAppBarDefaults.centerAlignedTopAppBarColors(
                      containerColor = AppColors.primary,
                      titleContentColor = AppColors.textIcons,
                      navigationIconContentColor = AppColors.textIcons),
              title = {
                Text(
                    text = MeepleMeetScreen.AddDiscussion.title,
                    modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
              },
              navigationIcon = {
                IconButton(onClick = onBack) {
                  Icon(
                      Icons.AutoMirrored.Filled.ArrowBack,
                      contentDescription = "Back",
                      modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON))
                }
              })
          HorizontalDivider(
              modifier =
                  Modifier.fillMaxWidth(0.7f)
                      .padding(horizontal = 0.dp)
                      .align(Alignment.CenterHorizontally),
              thickness = 1.dp,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
      }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Top) {
              /** Title input field */
              OutlinedTextField(
                  value = title,
                  colors =
                      TextFieldDefaults.colors()
                          .copy(
                              focusedTextColor = AppColors.textIcons,
                              unfocusedTextColor = AppColors.textIcons,
                              unfocusedIndicatorColor = AppColors.textIcons,
                              focusedIndicatorColor = AppColors.textIcons,
                              unfocusedLabelColor = AppColors.textIconsFade,
                              focusedLabelColor = AppColors.textIconsFade,
                              unfocusedContainerColor = Color.Transparent,
                              focusedContainerColor = Color.Transparent),
                  textStyle = MaterialTheme.typography.bodySmall,
                  onValueChange = { title = it },
                  label = { Text("Title") },
                  modifier = Modifier.testTag(AddDiscussionTestTags.ADD_TITLE).fillMaxWidth())

              Spacer(modifier = Modifier.height(12.dp))

              /** Description input field */
              OutlinedTextField(
                  value = description,
                  colors =
                      TextFieldDefaults.colors()
                          .copy(
                              focusedTextColor = AppColors.textIcons,
                              unfocusedTextColor = AppColors.textIcons,
                              unfocusedIndicatorColor = AppColors.textIcons,
                              focusedIndicatorColor = AppColors.textIcons,
                              unfocusedLabelColor = AppColors.textIconsFade,
                              focusedLabelColor = AppColors.textIconsFade,
                              unfocusedContainerColor = Color.Transparent,
                              focusedContainerColor = Color.Transparent),
                  textStyle = MaterialTheme.typography.bodySmall,
                  onValueChange = { description = it },
                  label = { Text("Description (optional)") },
                  modifier =
                      Modifier.testTag(AddDiscussionTestTags.ADD_DESCRIPTION)
                          .fillMaxWidth()
                          .height(150.dp))

              Spacer(modifier = Modifier.height(16.dp))

              /** Row for search and member selection */
              MemberSearchField(
                  searchQuery = searchQuery,
                  onQueryChange = { searchQuery = it },
                  searchResults = searchResults,
                  isSearching = isSearching,
                  dropdownExpanded = dropdownExpanded,
                  onDismiss = { dropdownExpanded = false },
                  onSelect = { account ->
                    selectedMembers.add(account)
                    searchQuery = ""
                    dropdownExpanded = false
                  })

              Spacer(modifier = Modifier.height(20.dp))

              /** Divider between search and selected members */
              HorizontalDivider(
                  modifier =
                      Modifier.fillMaxWidth(0.9f)
                          .padding(horizontal = 0.dp)
                          .align(Alignment.CenterHorizontally),
                  thickness = 1.dp,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))

              /** Display list of selected members */
              if (selectedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                  items(selectedMembers) { member ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                          /** Member avatar */
                          Box(
                              modifier =
                                  Modifier.size(36.dp)
                                      .clip(CircleShape)
                                      .background(Color.LightGray),
                              contentAlignment = Alignment.Center) {
                                Text(
                                    text = member.name.firstOrNull()?.toString() ?: "A",
                                    color = Color(0xFFFFA000),
                                    fontWeight = FontWeight.Bold)
                              }
                          Spacer(modifier = Modifier.width(12.dp))

                          /** Member name */
                          Text(
                              text = member.handle,
                              modifier = Modifier.weight(1f),
                              maxLines = 1,
                              color = AppColors.textIcons,
                              fontStyle = MaterialTheme.typography.bodySmall.fontStyle)

                          /** Remove member button */
                          Icon(
                              Icons.Default.Cancel,
                              contentDescription = "Remove",
                              tint = AppColors.negative,
                              modifier = Modifier.clickable { selectedMembers.remove(member) })
                        }
                  }
                }
              }

              /** Spacer to move buttons higher */
              Spacer(modifier = Modifier.height(24.dp))

              /** Buttons section */
              Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                          scope.launch {
                            try {
                              isCreating = true
                              val clean = selectedMembers.toList()
                              require(clean.size == selectedMembers.size) {
                                "Bug: null Account in selection"
                              }
                              viewModel.createDiscussion(
                                  title, description, account, *clean.toTypedArray())
                              isCreating = false
                              onCreate()
                            } catch (_: Exception) {
                              isCreating = false
                              creationError = "Failed to create discussion"
                            }
                          }
                        },
                        enabled = title.isNotBlank() && !isCreating,
                        modifier =
                            Modifier.testTag(AddDiscussionTestTags.CREATE_DISCUSSION_BUTTON)
                                .fillMaxWidth(0.5f),
                        shape = CircleShape,
                        colors =
                            ButtonDefaults.buttonColors(containerColor = AppColors.affirmative)) {
                          Text(
                              text = "Create Discussion",
                              style = MaterialTheme.typography.bodySmall)
                        }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(0.3f),
                        shape = CircleShape,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.negative)) {
                          Text(text = "Discard", style = MaterialTheme.typography.bodySmall)
                        }
                  }
            }
      }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSearchField(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<Account>,
    isSearching: Boolean,
    dropdownExpanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Account) -> Unit
) {
  ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = {}) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = { onQueryChange(it) },
        label = { Text("Add Members") },
        modifier =
            Modifier.menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth()
                .testTag(AddDiscussionTestTags.ADD_MEMBERS),
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear",
                modifier = Modifier.clickable { onQueryChange("") })
          }
        })

    ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = onDismiss) {
      when {
        isSearching -> DropdownMenuItem(text = { Text("Searching...") }, onClick = {})
        searchResults.isEmpty() -> DropdownMenuItem(text = { Text("No results") }, onClick = {})
        else ->
            searchResults.forEach { account ->
              DropdownMenuItem(
                  modifier = Modifier.testTag(AddDiscussionTestTags.ADD_MEMBERS_ELEMENT),
                  text = { Text(account.handle) },
                  onClick = {
                    onSelect(account)
                    onDismiss()
                  })
            }
      }
    }
  }
}
