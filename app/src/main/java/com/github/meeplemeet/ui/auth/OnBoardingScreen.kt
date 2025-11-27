// AI helped generate some of this code especially for magic numbers
package com.github.meeplemeet.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.meeplemeet.R
import com.github.meeplemeet.model.discussions.Message
import com.github.meeplemeet.model.discussions.Poll
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.discussions.ChatBubble
import com.github.meeplemeet.ui.discussions.PollBubble
import com.github.meeplemeet.ui.posts.FeedCard
import com.github.meeplemeet.ui.sessions.SessionOverCard
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.launch

// Test tags used for UI testing
object OnBoardingTestTags {
  const val SKIP_BUTTON = "OnBoarding_SkipButton"
  const val PAGE_TITLE = "OnBoarding_PageTitle"
  const val BACK_BUTTON = "OnBoarding_BackButton"
  const val NEXT_BUTTON = "OnBoarding_NextButton"
  const val PAGER_DOT = "OnBoarding_PagerDot"
  const val PAGER = "OnBoarding_Pager"
  const val DISCUSSION_PREVIEW_CARD = "DiscussionPreviewCard"
  const val CLOSE_DIALOG = "OnBoarding_CloseDialog"
}

object OnBoardingStrings {
  const val SKIP = "Skip"
  const val MEEPLE_MEET_INTRO_DESCRIPTION =
      "Meeple Meet helps you organize game sessions, join discussions, explore shops, check prices, and find local gaming spaces."
  const val DISCUSSION_PREVIEW_DESCRIPTION =
      "Meeple Meet helps you connect with new friends and join fun discussions around your favorite games."
  const val DISCUSSION_PREVIEW_TAP = "⬆️ Tap the discussion above to continue"
  const val DISCUSSION_PREVIEW_JUMP = "Jump into the conversation and never miss a meetup!"
  const val SESSION_PAGE_SUBTITLE = "Organize and join gaming meetups with friends"
  const val POST_PAGE_SUBTITLE = "Share your gaming experiences with the world"
  const val MAP_EXPLORATION_SUBTITLE = "Find board game shops and rental spaces near you"
  const val MAP_EXPLORATION_END_TEXT =
      "Browse shops, compare prices, and discover venues for your next game night!"
  const val POSTS_PAGE_END_TEXT =
      "Connect with gamers worldwide! Share strategies, reviews, and join the conversation about your favorite games."
  const val BOARD_GAME_NIGHT = "Board Game Night"
  const val FIVE_NEW_MESSAGES = "5 new messages"
}

data class OnBoardPage(val image: Int, val title: String, val description: String)

@Composable
fun OnBoardingScreen(pages: List<OnBoardPage>, onSkip: () -> Unit, onFinished: () -> Unit) {
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val hasInteractedWithDiscussion = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(AppColors.primary)
              .padding(Dimensions.CornerRadius.round)
              .pointerInput(pagerState.currentPage) {
                // Block swipe gestures on page 1 if user hasn't interacted
                if (pagerState.currentPage == 1 && !hasInteractedWithDiscussion.value) {
                  detectHorizontalDragGestures { _, _ -> }
                }
              }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
              Box(modifier = Modifier.fillMaxWidth()) {
                SkipButton(onSkip = onSkip, modifier = Modifier.align(Alignment.TopEnd))
              }
              OnBoardingPager(
                  pagerState = pagerState,
                  pages = pages,
                  hasInteractedWithDiscussion = hasInteractedWithDiscussion,
                  modifier = Modifier.weight(Dimensions.Weight.full))

              NavigationControls(
                  pagerState = pagerState,
                  pages = pages,
                  hasInteractedWithDiscussion = hasInteractedWithDiscussion.value,
                  onNavigate = { page -> scope.launch { pagerState.animateScrollToPage(page) } })

              // Show end button if last page
              if (pagerState.currentPage == pages.lastIndex) {
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.focus),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = Dimensions.CornerRadius.round,
                                vertical = Dimensions.Padding.extraLarge)
                            .testTag("OnBoarding_EndButton")
                            .clickable(onClick = onFinished)) {
                      Text(text = OnBoardingStrings.SKIP)
                    }
              }
            }
      }
}

@Composable
private fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
  Button(
      onClick = onSkip,
      colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary),
      contentPadding = PaddingValues(Dimensions.Padding.medium),
      modifier = modifier.zIndex(Dimensions.Weight.full).testTag(OnBoardingTestTags.SKIP_BUTTON)) {
        Text(text = OnBoardingStrings.SKIP, color = AppColors.secondary)
      }
}

@Composable
private fun OnBoardingPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<OnBoardPage>,
    hasInteractedWithDiscussion: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
  HorizontalPager(
      state = pagerState,
      modifier = modifier.fillMaxWidth().testTag(OnBoardingTestTags.PAGER),
      userScrollEnabled = pagerState.currentPage != 1 || hasInteractedWithDiscussion.value) { page
        ->
        when (page) {
          0 -> MeepleMeetIntroPage()
          1 ->
              DiscussionPreviewPage(
                  pageData = pages[page], hasInteractedWithDiscussion = hasInteractedWithDiscussion)
          2 -> MapExplorationPage()
          3 -> SessionsPage()
          4 -> PostsPage()
          else -> StandardOnBoardingPage(pageData = pages[page], pageIndex = page)
        }
      }
}

@Composable
fun StandardOnBoardingPage(pageData: OnBoardPage, pageIndex: Int) {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize()) {
        PageImage(imageRes = pageData.image)
        PageTitle(title = pageData.title, pageIndex = pageIndex)
        PageDescription(description = pageData.description)
      }
}

@Composable
fun DiscussionPreviewPage(
    pageData: OnBoardPage,
    hasInteractedWithDiscussion: MutableState<Boolean>
) {
  val showDialog = remember { mutableStateOf(false) }

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize()) {
        PageImage(imageRes = pageData.image)
        PageTitle(title = pageData.title, pageIndex = 1)

        Text(
            text = OnBoardingStrings.DISCUSSION_PREVIEW_DESCRIPTION,
            fontSize = Dimensions.TextSize.subtitle,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            modifier = Modifier.padding(horizontal = Dimensions.CornerRadius.round))

        Spacer(modifier = Modifier.height(Dimensions.Spacing.xxxLarge))

        DiscussionPreviewCard(
            onClick = {
              showDialog.value = true
              hasInteractedWithDiscussion.value = true
            })

        Spacer(modifier = Modifier.height(Dimensions.Padding.extraMedium))

        InteractionPrompt(hasInteracted = hasInteractedWithDiscussion.value)

        if (showDialog.value) {
          DiscussionDetailDialog(onDismiss = { showDialog.value = false })
        }
      }
}

@Composable
private fun PageImage(imageRes: Int) {
  Image(
      painter = painterResource(id = imageRes),
      contentDescription = null,
      modifier =
          Modifier.size(Dimensions.ContainerSize.pageImageSize)
              .padding(bottom = Dimensions.CornerRadius.round))
}

@Composable
private fun PageTitle(title: String, pageIndex: Int) {
  Text(
      text = title,
      fontSize = Dimensions.TextSize.xLarge,
      fontWeight = FontWeight.Bold,
      modifier =
          Modifier.padding(bottom = Dimensions.Padding.large)
              .testTag("${OnBoardingTestTags.PAGE_TITLE}_$pageIndex"))
}

@Composable
private fun PageDescription(description: String) {
  Text(
      text = description,
      fontSize = Dimensions.TextSize.subtitle,
      color = AppColors.secondary,
      modifier = Modifier.padding(horizontal = Dimensions.CornerRadius.round))
}

@Composable
fun DiscussionPreviewCard(onClick: () -> Unit) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = Dimensions.Padding.extraLarge)
              .heightIn(
                  min = Dimensions.ContainerSize.discussionCardMinHeight,
                  max = Dimensions.ContainerSize.discussionCardMaxHeight)
              .focusRequester(focusRequester)
              .testTag(OnBoardingTestTags.DISCUSSION_PREVIEW_CARD),
      shape = RoundedCornerShape(Dimensions.CornerRadius.round),
      tonalElevation = Dimensions.Padding.medium,
      shadowElevation = Dimensions.Padding.small,
      color = AppColors.primary,
      onClick = onClick) {
        Row(
            modifier =
                Modifier.padding(
                        horizontal = Dimensions.Padding.large, vertical = Dimensions.Padding.medium)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
              DiscussionAvatar()
              DiscussionInfo()
              DiscussionTimestamp()
            }
      }
}

@Composable
private fun RowScope.DiscussionAvatar() {
  Box(modifier = Modifier.padding(end = Dimensions.Padding.extraLarge)) {
    Surface(
        shape = CircleShape,
        color = AppColors.primary,
        modifier = Modifier.size(Dimensions.IconSize.giant),
        tonalElevation = Dimensions.Elevation.none) {
          Image(
              painter = painterResource(id = R.drawable.session_logo),
              contentDescription = "Avatar",
              modifier = Modifier.padding(Dimensions.Padding.medium))
        }
    Badge(
        containerColor = AppColors.focus,
        contentColor = Color.White,
        modifier = Modifier.align(Alignment.TopEnd)) {
          Text("2", fontSize = Dimensions.TextSize.tiny, fontWeight = FontWeight.Bold)
        }
  }
}

@Composable
private fun RowScope.DiscussionInfo() {
  Column(modifier = Modifier.weight(Dimensions.Weight.full)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = OnBoardingStrings.BOARD_GAME_NIGHT,
          fontWeight = FontWeight.SemiBold,
          fontSize = Dimensions.TextSize.medium,
          color = AppColors.textIcons)
      Spacer(modifier = Modifier.width(Dimensions.Padding.mediumSmall))
      Text(
          text = "• 5 new",
          color = AppColors.primary,
          fontWeight = FontWeight.Medium,
          fontSize = Dimensions.TextSize.small)
    }
    Spacer(modifier = Modifier.height(Dimensions.Spacing.small))
    Text(
        text = "\"Which Game should we play?\" – Alex",
        color = AppColors.textIconsFade,
        fontSize = Dimensions.TextSize.tiny,
        maxLines = Dimensions.Numbers.singleLine)
  }
}

@Composable
private fun DiscussionTimestamp() {
  Spacer(modifier = Modifier.width(Dimensions.Padding.extraMedium))
  Column(horizontalAlignment = Alignment.End) {
    Text(text = "21:01", fontSize = Dimensions.TextSize.tiny, color = AppColors.secondary)
    Spacer(modifier = Modifier.height(Dimensions.Padding.small))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
      Text(
          text = "Tap",
          fontSize = Dimensions.TextSize.small,
          color = AppColors.focus,
          fontWeight = FontWeight.Medium)
      Spacer(modifier = Modifier.width(Dimensions.Padding.small))
      Icon(
          imageVector = Icons.Default.ArrowForward,
          contentDescription = "Tap to explore",
          tint = AppColors.focus,
          modifier = Modifier.size(Dimensions.Padding.large))
    }
  }
}

@Composable
private fun InteractionPrompt(hasInteracted: Boolean) {
  Text(
      text =
          if (!hasInteracted) {
            OnBoardingStrings.DISCUSSION_PREVIEW_TAP
          } else {
            OnBoardingStrings.DISCUSSION_PREVIEW_JUMP
          },
      fontSize = if (!hasInteracted) Dimensions.TextSize.standard else Dimensions.TextSize.body,
      color = if (!hasInteracted) AppColors.focus else AppColors.textIconsFade,
      fontWeight = if (!hasInteracted) FontWeight.Bold else FontWeight.Normal,
      modifier =
          Modifier.padding(
              top = Dimensions.Padding.medium,
              start = Dimensions.CornerRadius.round,
              end = Dimensions.CornerRadius.round))
}

@Composable
fun DiscussionDetailDialog(onDismiss: () -> Unit) {
  androidx.compose.animation.AnimatedVisibility(
      visible = true,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandIn(),
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkOut()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxSize().padding(vertical = Dimensions.CornerRadius.round),
            containerColor = AppColors.secondary,
            tonalElevation = Dimensions.Elevation.none,
            shape = MaterialTheme.shapes.large,
            title = null,
            icon = null,
            text = {
              Column(
                  modifier = Modifier.fillMaxSize().padding(Dimensions.Padding.extraLarge),
                  verticalArrangement = Arrangement.spacedBy(Dimensions.Padding.medium)) {
                    DialogHeader(onClose = onDismiss)
                    DialogInstructions()
                    DiscussionHeader()
                    DialogMessages()
                    DialogFooter()
                  }
            },
            confirmButton = {},
            dismissButton = {})
      }
}

@Composable
private fun DialogHeader(onClose: () -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = Dimensions.Padding.medium),
      verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onClose, modifier = Modifier.testTag(OnBoardingTestTags.CLOSE_DIALOG)) {
              Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close",
                  tint = Color.Black)
            }
        Spacer(modifier = Modifier.width(Dimensions.Padding.medium))
        Text(
            "Session Creation",
            fontSize = Dimensions.TextSize.largeHeading,
            fontWeight = FontWeight.Medium,
            color = AppColors.textIcons,
            modifier = Modifier.weight(Dimensions.Weight.full))
      }
}

@Composable
private fun DialogInstructions() {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clickable { /* TODO */}
              .padding(vertical = Dimensions.Padding.small),
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Use the button next to the discussion's name to create or join a session",
            fontSize = Dimensions.TextSize.small,
            color = AppColors.textIcons,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(top = Dimensions.Padding.tiny, bottom = Dimensions.Padding.small)
                    .align(Alignment.CenterHorizontally))
      }
}

@Composable
private fun DiscussionHeader() {
  Surface(
      color = AppColors.secondary,
      shape = RoundedCornerShape(Dimensions.Padding.extraLarge),
      modifier = Modifier.fillMaxWidth().padding(bottom = Dimensions.Padding.medium)) {
        Column {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                  Modifier.padding(
                          horizontal = Dimensions.Padding.large,
                          vertical = Dimensions.Padding.medium)
                      .fillMaxWidth()) {
                Surface(
                    shape = CircleShape,
                    color = AppColors.primary,
                    modifier = Modifier.size(Dimensions.IconSize.huge),
                ) {
                  Image(
                      painter = painterResource(id = R.drawable.google_logo),
                      contentDescription = "Discussion Avatar",
                      modifier = Modifier.padding(Dimensions.Padding.medium))
                }
                Spacer(Modifier.width(Dimensions.Padding.large))
                Column(modifier = Modifier.weight(Dimensions.Weight.full)) {
                  Text(
                      OnBoardingStrings.BOARD_GAME_NIGHT,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = Dimensions.TextSize.standard,
                      color = AppColors.textIcons,
                      modifier = Modifier.testTag("DiscussionHeader_Title"))
                  Text(
                      OnBoardingStrings.FIVE_NEW_MESSAGES,
                      fontSize = Dimensions.TextSize.small,
                      color = AppColors.textIconsFade)
                }
                Spacer(modifier = Modifier.width(Dimensions.Padding.extraMedium))
                Icon(
                    imageVector = Icons.Default.LibraryAdd,
                    contentDescription = "Session",
                    tint = AppColors.textIcons)
              }
          HorizontalDivider(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = Dimensions.ContainerSize.dividerHorizontalPadding,
                          vertical = Dimensions.Padding.small))
        }
      }
}

@Composable
private fun DialogMessages() {
  ChatBubble(
      message =
          Message(
              uid = "1",
              senderId = "Alex",
              content = "Hey! Are we playing Catan tonight?",
              createdAt = com.google.firebase.Timestamp.now()),
      isMine = false,
      senderName = "Alex")
  Spacer(Modifier.height(Dimensions.Padding.medium))

  PollBubble(
      msgIndex = 0,
      poll =
          Poll(
              question = "Which game should we play?",
              options = listOf("Catan", "Wingspan", "Terraforming Mars"),
              allowMultipleVotes = true,
              votes = votes),
      authorName = "Dany",
      currentUserId = "currentUser",
      createdAt = Date(),
      onVote = { x, _ -> x + 1 })
}

@Composable
private fun DialogFooter() {
  Text(
      "Tap on any discussion to see full details and join in!",
      fontSize = Dimensions.TextSize.standard,
      color = AppColors.textIcons,
      modifier = Modifier.padding(bottom = Dimensions.Padding.large).fillMaxWidth())
}

@Composable
fun NavigationControls(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<OnBoardPage>,
    hasInteractedWithDiscussion: Boolean,
    onNavigate: (Int) -> Unit
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.Padding.extraLarge),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween) {
        BackButton(
            canGoBack =
                pagerState.currentPage > 0 &&
                    (pagerState.currentPage != 1 || hasInteractedWithDiscussion),
            onClick = { onNavigate(pagerState.currentPage - 1) })

        PageIndicators(pageCount = pages.size, currentPage = pagerState.currentPage)

        NextButton(
            currentPage = pagerState.currentPage,
            lastIndex = pages.lastIndex,
            hasInteractedWithDiscussion = hasInteractedWithDiscussion,
            onClick = { onNavigate(pagerState.currentPage + 1) })
      }
}

@Composable
fun BackButton(canGoBack: Boolean, onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.size(Dimensions.ButtonSize.navigation)
              .then(
                  if (canGoBack) {
                    Modifier.background(color = AppColors.primary, shape = CircleShape)
                        .clickable(onClick = onClick)
                  } else {
                    Modifier
                  })
              .testTag(OnBoardingTestTags.BACK_BUTTON),
      contentAlignment = Alignment.Center) {
        if (canGoBack) {
          Icon(
              imageVector = Icons.Default.ArrowBack,
              contentDescription = "Back",
              tint = AppColors.textIcons)
        }
      }
}

@Composable
private fun PageIndicators(pageCount: Int, currentPage: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    repeat(pageCount) { index ->
      val isSelected = currentPage == index
      val targetSize = if (isSelected) Dimensions.CornerRadius.large else Dimensions.Padding.medium
      val animatedSize by animateFloatAsState(targetValue = targetSize.value, label = "dot_size")

      Box(
          modifier =
              Modifier.padding(horizontal = Dimensions.Spacing.small)
                  .size(animatedSize.dp)
                  .background(
                      color = if (isSelected) AppColors.textIconsFade else AppColors.secondary,
                      shape = CircleShape)
                  .testTag(OnBoardingTestTags.PAGER_DOT))
    }
  }
}

@Composable
fun NextButton(
    currentPage: Int,
    lastIndex: Int,
    hasInteractedWithDiscussion: Boolean,
    onClick: () -> Unit
) {
  if (currentPage < lastIndex) {
    val isEnabled = currentPage != 1 || hasInteractedWithDiscussion

    Box(
        modifier =
            Modifier.size(Dimensions.ButtonSize.navigation)
                .background(
                    color = if (isEnabled) AppColors.primary else AppColors.secondary,
                    shape = CircleShape)
                .clickable(enabled = isEnabled, onClick = onClick)
                .testTag(OnBoardingTestTags.NEXT_BUTTON),
        contentAlignment = Alignment.Center) {
          Icon(
              imageVector = Icons.Default.ArrowForward,
              contentDescription = "Next",
              tint = if (isEnabled) AppColors.textIcons else Color.Gray)
        }
  } else {
    Box(
        modifier =
            Modifier.size(Dimensions.ButtonSize.navigation).testTag(OnBoardingTestTags.NEXT_BUTTON))
  }
}

val votes: Map<String, List<Int>> = mapOf("1" to listOf(0), "2" to emptyList())

// --- MeepleMeetIntroPage composable ---
@Composable
fun MeepleMeetIntroPage() {
  val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
  val logoRes = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear
  Box(modifier = Modifier.fillMaxSize().background(AppColors.primary)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier.fillMaxSize().padding(Dimensions.Spacing.xxxLarge)) {
          PageImage(imageRes = logoRes)
          PageTitle(title = "Meeple Meet", pageIndex = 0)
          Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))
          Column(
              horizontalAlignment = Alignment.Start,
              modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.none)) {
                IntroFeatureCard(
                    Icons.Default.Group,
                    "Connect with friends and chat about upcoming game sessions",
                    tint = AppColors.textIcons)
                IntroFeatureCard(
                    Icons.Default.Forum,
                    "Share posts and join discussions on your favorite games",
                    tint = AppColors.textIconsFade)
                IntroFeatureCard(
                    Icons.Default.ShoppingCart,
                    "Browse game stores with prices, stock, and photos",
                    tint = AppColors.textIcons)
                IntroFeatureCard(
                    Icons.Default.LocationOn,
                    "Discover rental spaces and venues for gaming events nearby",
                    tint = AppColors.textIconsFade)
              }
          Spacer(modifier = Modifier.height(Dimensions.Spacing.xxxLarge))
          Text(
              text = OnBoardingStrings.MEEPLE_MEET_INTRO_DESCRIPTION,
              fontSize = Dimensions.TextSize.subtitle,
              color = AppColors.secondary,
              textAlign = TextAlign.Center,
              modifier =
                  Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.extraLarge))
        }
  }
}

@Composable
private fun IntroFeatureCard(icon: ImageVector, text: String, tint: Color = AppColors.textIcons) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth().padding(vertical = Dimensions.DividerThickness.strokeWidth)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimensions.IconSize.extraLarge))
        Spacer(modifier = Modifier.width(Dimensions.CornerRadius.large))
        Text(
            text = text,
            fontSize = Dimensions.TextSize.subtitle,
            color = AppColors.textIcons,
            modifier = Modifier.weight(Dimensions.Weight.full))
      }
}

@Composable
fun MapExplorationPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = Dimensions.Padding.medium)) {
        PageTitle(title = "Explore Nearby", pageIndex = 2)

        Text(
            text = OnBoardingStrings.MAP_EXPLORATION_SUBTITLE,
            fontSize = Dimensions.TextSize.standard,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.Spacing.xxxLarge,
                    vertical = Dimensions.CornerRadius.large))

        // Map visualization with real map background
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(Dimensions.ContainerSize.mapHeight)
                    .padding(horizontal = Dimensions.Padding.extraLarge)
                    .clip(RoundedCornerShape(Dimensions.Padding.extraLarge))
                    .border(
                        width = Dimensions.DividerThickness.medium,
                        color = AppColors.primary,
                        shape = RoundedCornerShape(Dimensions.Padding.extraLarge))) {
              // Real map background image
              Image(
                  painter = painterResource(id = R.drawable.maps_logo),
                  contentDescription = "Map background",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize())

              // Session marker #1 (Red - Gaming Sessions)
              MapMarkerWithDrawable(
                  offsetX = Dimensions.MapDimensions.offsetX90,
                  offsetY = Dimensions.MapDimensions.offsetY80,
                  color = AppColors.focus,
                  iconResId = R.drawable.ic_dice,
                  label = "Game Session")

              // Shop location marker #1 (Purple - Buy Games)
              MapMarkerWithDrawable(
                  offsetX = Dimensions.MapDimensions.offsetX240,
                  offsetY = Dimensions.MapDimensions.offsetY240,
                  color = AppColors.neutral,
                  iconResId = R.drawable.ic_storefront,
                  label = "Board Game Shop")

              // Rental space marker #1 (Orange - Rent Space/Games)
              MapMarkerWithDrawable(
                  offsetX = Dimensions.MapDimensions.offsetX180,
                  offsetY = Dimensions.MapDimensions.offsetY140,
                  color = AppColors.focus,
                  iconResId = R.drawable.ic_table,
                  label = "Gaming Cafe")

              // Rental space marker #2 (Orange - Rent Space/Games)
              MapMarkerWithDrawable(
                  offsetX = Dimensions.MapDimensions.offsetX70,
                  offsetY = Dimensions.MapDimensions.offsetY260,
                  color = AppColors.negative,
                  iconResId = R.drawable.ic_table,
                  label = "Event Space")

              // Your location marker (Blue)
              Box(
                  modifier =
                      Modifier.offset(
                              x = Dimensions.MapDimensions.offsetX280,
                              y = Dimensions.MapDimensions.offsetY180)
                          .size(Dimensions.Spacing.xxxLarge)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                      drawCircle(
                          color = Color(0xFF3B82F6),
                          radius = Dimensions.Padding.medium.toPx(),
                      )
                      drawCircle(
                          color = Color.White, // need white border even in dark mode
                          radius = Dimensions.Padding.medium.toPx(),
                          style = Stroke(width = Dimensions.DividerThickness.strokeWidth.toPx()))
                    }
                  }

              // "You" label
              Text(
                  text = "You",
                  fontSize = Dimensions.TextSize.small,
                  fontWeight = FontWeight.Bold,
                  color = AppColors.neutral,
                  modifier =
                      Modifier.offset(
                          x = Dimensions.MapDimensions.offsetX268,
                          y = Dimensions.MapDimensions.offsetY208))
            }

        Spacer(modifier = Modifier.height(Dimensions.CornerRadius.large))

        // Legend
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.xxxLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Padding.medium)) {
              MapLegendItemWithDrawable(
                  color = AppColors.negative,
                  iconResId = R.drawable.ic_dice,
                  title = "Gaming Sessions",
                  description = "Active locations where people are playing")
              MapLegendItemWithDrawable(
                  color = AppColors.neutral,
                  iconResId = R.drawable.ic_storefront,
                  title = "Game Shops",
                  description = "Buy board games with prices and stock info")
              MapLegendItemWithDrawable(
                  color = AppColors.focus,
                  iconResId = R.drawable.ic_table,
                  title = "Rental Spaces",
                  description = "Rent games or book venues for sessions")
            }

        Spacer(modifier = Modifier.weight(Dimensions.Weight.full))

        Text(
            text = OnBoardingStrings.MAP_EXPLORATION_END_TEXT,
            fontSize = Dimensions.TextSize.medium,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.Spacing.xxxLarge,
                    vertical = Dimensions.Padding.extraLarge))
      }
}

@Composable
private fun MapMarkerWithDrawable(
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    color: Color,
    iconResId: Int,
    label: String
) {
  Box(
      modifier =
          Modifier.offset(x = offsetX, y = offsetY)
              .size(
                  Dimensions.IconSize.medium * Dimensions.Multipliers.double +
                      Dimensions.Padding.medium)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          // Pulse effect
          drawCircle(
              color = color.copy(alpha = Dimensions.Alpha.pulseEffect),
              radius = Dimensions.Spacing.xxxLarge.toPx())
          // Main dot
          drawCircle(color = color, radius = Dimensions.MapDimensions.markerRadius.toPx())
        }
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(Dimensions.IconSize.small).align(Alignment.Center))
      }
}

@Composable
private fun MapLegendItemWithDrawable(
    color: Color,
    iconResId: Int,
    title: String,
    description: String
) {
  Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier.size(Dimensions.IconSize.standard).background(color, CircleShape),
        contentAlignment = Alignment.Center) {
          Icon(
              painter = painterResource(id = iconResId),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(Dimensions.IconSize.tiny))
        }
    Spacer(modifier = Modifier.width(Dimensions.MapDimensions.markerRadius))
    Column {
      Text(
          text = title,
          fontSize = Dimensions.TextSize.standard,
          fontWeight = FontWeight.SemiBold,
          color = AppColors.textIcons)
      Text(
          text = description,
          fontSize = Dimensions.TextSize.small,
          color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
          lineHeight = Dimensions.LineHeight.standard)
    }
  }
}

// ==================== SESSIONS PAGE ====================

@Composable
fun SessionsPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = Dimensions.Padding.medium)) {
        PageTitle(title = "Game Sessions", pageIndex = 3)

        Text(
            text = OnBoardingStrings.SESSION_PAGE_SUBTITLE,
            fontSize = Dimensions.TextSize.standard,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.Spacing.xxxLarge,
                    vertical = Dimensions.Padding.extraLarge))

        // Session cards visualization
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.extraLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.CornerRadius.large)) {
              SessionOverCard(
                  session =
                      Session(
                          name = "Catan Tournament",
                          gameId = "game_catan",
                          date = Timestamp.now(),
                          location = Location(name = "Coffee & Games Cafe"),
                          participants = listOf("alice_id", "bob_id", "charlie_id")),
                  gameName = "Catan Tournament",
                  participantText = "3 participants",
                  date = "Today",
                  modifier = Modifier.fillMaxWidth())

              SessionOverCard(
                  session =
                      Session(
                          name = "Wingspan Night",
                          gameId = "game_wingspan",
                          date = Timestamp.now(),
                          location = Location(name = "Board Game Hub"),
                          participants = listOf("user1", "user2", "user3", "user4")),
                  gameName = "Wingspan Night",
                  participantText = "4 participants",
                  date = "Today",
                  modifier = Modifier.fillMaxWidth())
            }

        Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))

        // Features grid
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.xxxLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Padding.medium)) {
              SessionFeatureItem(
                  icon = Icons.Default.CalendarToday,
                  text = "Schedule sessions with date, time, and location")
              SessionFeatureItem(
                  icon = Icons.Default.SportsEsports,
                  text = "Link sessions to specific games you want to play")
              SessionFeatureItem(
                  icon = Icons.Default.People, text = "Invite friends and manage member list")
              SessionFeatureItem(
                  icon = Icons.Default.History,
                  text = "View your session history and upcoming events")
            }
      }
}

@Composable
private fun SessionFeatureItem(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AppColors.focus,
        modifier = Modifier.size(Dimensions.IconSize.standard))
    Spacer(modifier = Modifier.width(Dimensions.Padding.medium))
    Text(text = text, fontSize = Dimensions.TextSize.medium, color = AppColors.textIcons)
  }
}

// ==================== POSTS PAGE ====================

@Composable
fun PostsPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = Dimensions.Padding.medium)) {
        PageTitle(title = "Community Posts", pageIndex = 4)

        Text(
            text = OnBoardingStrings.POST_PAGE_SUBTITLE,
            fontSize = Dimensions.TextSize.standard,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.Spacing.xxxLarge,
                    vertical = Dimensions.Padding.extraLarge))

        // Post cards visualization
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.extraLarge),
        ) {
          FeedCard(
              authorName = "GameLover",
              postTitle = "Anyone up for a game of Pandemic this weekend?",
              commentCount = 21,
              date = "12/05/2025",
              firstTag = "Pandemic")
          //
          FeedCard(
              authorName = "DiceRoller",
              postTitle = "Want to play monopoly and drink cola?",
              commentCount = 1,
              date = "23/06/2025",
              firstTag = "monopoly")
        }

        Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))

        // Features grid
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Spacing.xxxLarge),
            verticalArrangement = Arrangement.spacedBy(Dimensions.Padding.medium)) {
              PostFeatureItem(
                  icon = Icons.Default.Article, text = "Create threads about any board game topic")
              PostFeatureItem(
                  icon = Icons.Default.ThumbUp, text = "Like and interact with community posts")
            }

        Spacer(modifier = Modifier.weight(Dimensions.Weight.full))

        Text(
            text = OnBoardingStrings.POSTS_PAGE_END_TEXT,
            fontSize = Dimensions.TextSize.medium,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = Dimensions.Spacing.xxxLarge,
                    vertical = Dimensions.Padding.extraLarge))
      }
}

@Composable
private fun PostFeatureItem(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AppColors.focus,
        modifier = Modifier.size(Dimensions.IconSize.standard))
    Spacer(modifier = Modifier.width(Dimensions.Padding.medium))
    Text(text = text, fontSize = Dimensions.TextSize.medium, color = AppColors.textIcons)
  }
}
