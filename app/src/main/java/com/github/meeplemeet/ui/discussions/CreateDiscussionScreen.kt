/** Documentation was written with the help of ChatGPT */
package com.github.meeplemeet.ui.discussions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.meeplemeet.model.account.Account
import com.github.meeplemeet.model.discussions.CreateDiscussionViewModel
import com.github.meeplemeet.ui.FocusableInputField
import com.github.meeplemeet.ui.UiBehaviorConfig
import com.github.meeplemeet.ui.navigation.MeepleMeetScreen
import com.github.meeplemeet.ui.navigation.NavigationTestTags
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import kotlinx.coroutines.launch

object AddDiscussionTestTags {
  const val ADD_TITLE = "Add Title"
  const val ADD_DESCRIPTION = "Add Description"
  const val ADD_MEMBERS = "Add Members"
  const val CREATE_DISCUSSION_BUTTON = "Create Discussion"
  const val ADD_MEMBERS_ELEMENT = "Add Member Element"

  const val DISCARD_BUTTON = "Discard Button"
}

const val DEFAULT_SEARCH_ALPHA = 0.2f

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
fun CreateDiscussionScreen(
    account: Account,
    viewModel: CreateDiscussionViewModel = viewModel(),
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
  var isInputFocused by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current

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
                    text = MeepleMeetScreen.CreateDiscussion.title,
                    modifier = Modifier.testTag(NavigationTestTags.SCREEN_TITLE))
              },
              navigationIcon = {
                IconButton(onClick = onBack) {
                  Icon(
                      Icons.AutoMirrored.Filled.ArrowBack,
                      modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
                      contentDescription = "Back")
                }
              })
          HorizontalDivider(
              modifier =
                  Modifier.fillMaxWidth(Dimensions.Fractions.topBarDivider)
                      .padding(horizontal = Dimensions.Spacing.none)
                      .align(Alignment.CenterHorizontally),
              thickness = Dimensions.DividerThickness.standard,
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = DEFAULT_SEARCH_ALPHA))
        }
      },
      bottomBar = {
        val shouldHide = UiBehaviorConfig.hideBottomBarWhenInputFocused
        if (!(shouldHide && isInputFocused)) {
          Row(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = Dimensions.Padding.xxxLarge,
                          vertical = Dimensions.Padding.xxLarge),
              horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.extraLarge)) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).testTag(AddDiscussionTestTags.DISCARD_BUTTON),
                    shape = RoundedCornerShape(percent = 50),
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = AppColors.negative)) {
                      Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null)
                      Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                      Text(text = "Discard", style = MaterialTheme.typography.bodySmall)
                    }

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
                        Modifier.weight(1f).testTag(AddDiscussionTestTags.CREATE_DISCUSSION_BUTTON),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.affirmative)) {
                      Icon(imageVector = Icons.Default.Check, contentDescription = null)
                      Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))
                      Text(text = "Create", style = MaterialTheme.typography.bodySmall)
                    }
              }
        }
      }) { padding ->
        Column(
            modifier =
                Modifier.padding(padding)
                    .padding(Dimensions.Padding.extraLarge)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                      detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
            verticalArrangement = Arrangement.Top) {
              /** Title input field */
              FocusableInputField(
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
                  modifier =
                      Modifier.onFocusChanged { isInputFocused = it.isFocused }
                          .testTag(AddDiscussionTestTags.ADD_TITLE)
                          .fillMaxWidth())

              Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

              /** Description input field */
              FocusableInputField(
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
                      Modifier.onFocusChanged { isInputFocused = it.isFocused }
                          .testTag(AddDiscussionTestTags.ADD_DESCRIPTION)
                          .fillMaxWidth()
                          .height(
                              Dimensions.ContainerSize.timeFieldHeight
                                  .times(2)
                                  .plus(Dimensions.Padding.xxxLarge)))

              Spacer(modifier = Modifier.height(Dimensions.Spacing.extraLarge))

              /** Row for search and member selection */
              MemberSearchField(
                  searchQuery = searchQuery,
                  onQueryChange = { searchQuery = it },
                  searchResults = searchResults,
                  isSearching = isSearching,
                  dropdownExpanded = dropdownExpanded,
                  onDismiss = { dropdownExpanded = false },
                  onFocusChanged = { isInputFocused = it },
                  onSelect = { account ->
                    selectedMembers.add(account)
                    searchQuery = ""
                    dropdownExpanded = false
                  })

              Spacer(modifier = Modifier.height(Dimensions.Spacing.xLarge))

              /** Divider between search and selected members */
              HorizontalDivider(
                  modifier =
                      Modifier.fillMaxWidth(0.9f)
                          .padding(horizontal = Dimensions.Spacing.none)
                          .align(Alignment.CenterHorizontally),
                  thickness = Dimensions.DividerThickness.standard,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = DEFAULT_SEARCH_ALPHA))

              /** Display list of selected members */
              if (selectedMembers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimensions.Spacing.large))
                LazyColumn(
                    modifier =
                        Modifier.heightIn(max = Dimensions.ContainerSize.bottomSpacer.times(2))) {
                      items(selectedMembers) { member ->
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .padding(vertical = Dimensions.Padding.small),
                            verticalAlignment = Alignment.CenterVertically) {
                              /** Member avatar */
                              Box(
                                  modifier =
                                      Modifier.size(Dimensions.Padding.huge)
                                          .clip(CircleShape)
                                          .background(Color.LightGray),
                                  contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name.firstOrNull()?.toString() ?: "A",
                                        color = Color(0xFFFFA000),
                                        fontWeight = FontWeight.Bold)
                                  }
                              Spacer(modifier = Modifier.width(Dimensions.Spacing.large))

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
            }
      }
}

/**
 * Composable for the member search field with dropdown results
 *
 * @param searchQuery Current text in the search field
 * @param onQueryChange Lambda called when the search query changes
 * @param searchResults List of Account results from the search
 * @param isSearching Whether a search is currently in progress
 * @param dropdownExpanded Whether the dropdown menu is expanded
 * @param onDismiss Lambda called to dismiss the dropdown
 * @param onFocusChanged Lambda called when the focus state changes
 * @param onSelect Lambda called when an Account is selected from the dropdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberSearchField(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<Account>,
    isSearching: Boolean,
    dropdownExpanded: Boolean,
    onDismiss: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSelect: (Account) -> Unit
) {
  ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = {}) {
    FocusableInputField(
        value = searchQuery,
        onValueChange = { onQueryChange(it) },
        label = { Text("Add Members") },
        modifier =
            Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                .menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                .fillMaxWidth()
                .testTag(AddDiscussionTestTags.ADD_MEMBERS),
        trailingIcon = {
          if (searchQuery.isNotBlank()) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Clear",
                modifier = Modifier.clickable { onQueryChange("") })
          } else Icon(imageVector = Icons.Default.Search, contentDescription = null)
        })

    ExposedDropdownMenu(
        expanded = dropdownExpanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(AppColors.primary)) {
          when {
            isSearching -> DropdownMenuItem(text = { Text("Searching...") }, onClick = {})
            searchResults.isEmpty() -> DropdownMenuItem(text = { Text("No results") }, onClick = {})
            else ->
                searchResults.forEach { account ->
                  DropdownMenuItem(
                      modifier =
                          Modifier.testTag(AddDiscussionTestTags.ADD_MEMBERS_ELEMENT)
                              .background(AppColors.primary),
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
