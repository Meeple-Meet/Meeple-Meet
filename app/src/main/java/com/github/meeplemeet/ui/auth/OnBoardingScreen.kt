// AI helped generate some of this code
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.github.meeplemeet.ui.theme.AppTheme
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

// Local dimensions for OnBoarding screen
private object OnBoardingDimensions {
  val pageImageSize = 400.dp
  val discussionCardMinHeight = 68.dp
  val discussionCardMaxHeight = 84.dp
  val discussionAvatarSize = Dimensions.IconSize.giant
  val navigationButtonSize = 42.dp
  val pageIndicatorSelected = Dimensions.Padding.large
  val pageIndicatorUnselected = Dimensions.Padding.medium
  val mapHeight = 300.dp
  val mediumPadding = Dimensions.Padding.medium
  val bigPadding = Dimensions.Padding.extraLarge
  val largeWidth = Dimensions.ButtonSize.medium
  val mapMarkerSize = Dimensions.IconSize.huge
  val mapMarkerRadius = 14.dp
  val mapMarkerPulseRadius = Dimensions.CornerRadius.round
  val spacerSize = Dimensions.Padding.extraMedium
  val IconSize = Dimensions.IconSize.medium
  val mapLegendInternalIconSize = Dimensions.TextSize.tiny
  val borderWidth = Dimensions.DividerThickness.standard
  val strokeWidth = Dimensions.Padding.small
  val dividerHorizontalPadding = 30.dp
  val offsetX268 = 268.dp
  val offsetY208 = 208.dp
  val offsetX90 = 90.dp
  val offsetY80 = 80.dp
  val offsetX240 = 240.dp
  val offsetY240 = 240.dp
  val offsetX180 = 180.dp
  val offsetY140 = 140.dp
  val offsetX70 = 70.dp
  val offsetY260 = 260.dp
  val offsetX280 = 280.dp
  val offsetY180 = 180.dp
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
              .padding(OnBoardingDimensions.mapMarkerPulseRadius)
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
                  modifier = Modifier.weight(1f))

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
                                horizontal = OnBoardingDimensions.mapMarkerPulseRadius,
                                vertical = OnBoardingDimensions.bigPadding)
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
      contentPadding = PaddingValues(OnBoardingDimensions.pageIndicatorUnselected),
      modifier = modifier.zIndex(1f).testTag(OnBoardingTestTags.SKIP_BUTTON)) {
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
            fontSize = 16.sp,
            color = AppColors.secondary,
            modifier = Modifier.padding(horizontal = OnBoardingDimensions.mapMarkerPulseRadius))

        Spacer(modifier = Modifier.height(32.dp))

        DiscussionPreviewCard(
            onClick = {
              showDialog.value = true
              hasInteractedWithDiscussion.value = true
            })

        Spacer(modifier = Modifier.height(OnBoardingDimensions.spacerSize))

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
          Modifier.size(OnBoardingDimensions.pageImageSize)
              .padding(bottom = OnBoardingDimensions.mapMarkerPulseRadius))
}

@Composable
private fun PageTitle(title: String, pageIndex: Int) {
  Text(
      text = title,
      fontSize = 32.sp,
      fontWeight = FontWeight.Bold,
      modifier =
          Modifier.padding(bottom = OnBoardingDimensions.pageIndicatorSelected)
              .testTag("${OnBoardingTestTags.PAGE_TITLE}_$pageIndex"))
}

@Composable
private fun PageDescription(description: String) {
  Text(
      text = description,
      fontSize = 16.sp,
      color = AppColors.secondary,
      modifier = Modifier.padding(horizontal = OnBoardingDimensions.mapMarkerPulseRadius))
}

@Composable
fun DiscussionPreviewCard(onClick: () -> Unit) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = OnBoardingDimensions.bigPadding)
              .heightIn(
                  min = OnBoardingDimensions.discussionCardMinHeight,
                  max = OnBoardingDimensions.discussionCardMaxHeight)
              .focusRequester(focusRequester)
              .testTag(OnBoardingTestTags.DISCUSSION_PREVIEW_CARD),
      shape = RoundedCornerShape(OnBoardingDimensions.mapMarkerPulseRadius),
      tonalElevation = OnBoardingDimensions.mediumPadding,
      shadowElevation = OnBoardingDimensions.strokeWidth,
      color = AppColors.primary,
      onClick = onClick) {
        Row(
            modifier =
                Modifier.padding(
                        horizontal = OnBoardingDimensions.pageIndicatorSelected,
                        vertical = OnBoardingDimensions.mediumPadding)
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
  Box(modifier = Modifier.padding(end = OnBoardingDimensions.bigPadding)) {
    Surface(
        shape = CircleShape,
        color = AppColors.primary,
        modifier = Modifier.size(OnBoardingDimensions.discussionAvatarSize),
        tonalElevation = 0.dp) {
          Image(
              painter = painterResource(id = R.drawable.discussion_logo),
              contentDescription = "Avatar",
              modifier = Modifier.padding(OnBoardingDimensions.mediumPadding))
        }
    Badge(
        containerColor = AppColors.focus,
        contentColor = Color.White,
        modifier = Modifier.align(Alignment.TopEnd)) {
          Text(
              "2",
              fontSize = OnBoardingDimensions.mapLegendInternalIconSize,
              fontWeight = FontWeight.Bold)
        }
  }
}

@Composable
private fun RowScope.DiscussionInfo() {
  Column(modifier = Modifier.weight(1f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = OnBoardingStrings.BOARD_GAME_NIGHT,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp,
          color = AppColors.textIcons)
      Spacer(modifier = Modifier.width(6.dp))
      Text(
          text = "• 5 new",
          color = AppColors.primary,
          fontWeight = FontWeight.Medium,
          fontSize = 10.sp)
    }
    Spacer(modifier = Modifier.height(3.dp))
    Text(
        text = "\"Which Game should we play?\" – Alex",
        color = AppColors.textIconsFade,
        fontSize = OnBoardingDimensions.mapLegendInternalIconSize,
        maxLines = 1)
  }
}

@Composable
private fun DiscussionTimestamp() {
  Spacer(modifier = Modifier.width(OnBoardingDimensions.spacerSize))
  Column(horizontalAlignment = Alignment.End) {
    Text(text = "21:01", fontSize = 9.sp, color = AppColors.secondary)
    Spacer(modifier = Modifier.height(OnBoardingDimensions.strokeWidth))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
      Text(text = "Tap", fontSize = 10.sp, color = AppColors.focus, fontWeight = FontWeight.Medium)
      Spacer(modifier = Modifier.width(OnBoardingDimensions.strokeWidth))
      Icon(
          imageVector = Icons.Default.ArrowForward,
          contentDescription = "Tap to explore",
          tint = AppColors.focus,
          modifier = Modifier.size(OnBoardingDimensions.pageIndicatorSelected))
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
      fontSize = if (!hasInteracted) 14.sp else 15.sp,
      color = if (!hasInteracted) AppColors.focus else AppColors.textIconsFade,
      fontWeight = if (!hasInteracted) FontWeight.Bold else FontWeight.Normal,
      modifier =
          Modifier.padding(
              top = OnBoardingDimensions.mediumPadding,
              start = OnBoardingDimensions.mapMarkerPulseRadius,
              end = OnBoardingDimensions.mapMarkerPulseRadius))
}

@Composable
fun DiscussionDetailDialog(onDismiss: () -> Unit) {
  androidx.compose.animation.AnimatedVisibility(
      visible = true,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandIn(),
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkOut()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier =
                Modifier.fillMaxSize()
                    .padding(vertical = OnBoardingDimensions.mapMarkerPulseRadius),
            containerColor = AppColors.secondary,
            tonalElevation = 0.dp,
            shape = MaterialTheme.shapes.large,
            title = null,
            icon = null,
            text = {
              Column(
                  modifier = Modifier.fillMaxSize().padding(OnBoardingDimensions.bigPadding),
                  verticalArrangement = Arrangement.spacedBy(OnBoardingDimensions.mediumPadding)) {
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
      modifier = Modifier.fillMaxWidth().padding(bottom = OnBoardingDimensions.mediumPadding),
      verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onClose, modifier = Modifier.testTag(OnBoardingTestTags.CLOSE_DIALOG)) {
              Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Close",
                  tint = Color.Black)
            }
        Spacer(modifier = Modifier.width(OnBoardingDimensions.mediumPadding))
        Text(
            "Session Creation",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.textIcons,
            modifier = Modifier.weight(1f))
      }
}

@Composable
private fun DialogInstructions() {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .clickable { /* TODO */}
              .padding(vertical = OnBoardingDimensions.strokeWidth),
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Use the button next to the discussion's name to create or join a session",
            fontSize = 12.sp,
            color = AppColors.textIcons,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(top = 2.dp, bottom = OnBoardingDimensions.strokeWidth)
                    .align(Alignment.CenterHorizontally))
      }
}

@Composable
private fun DiscussionHeader() {
  Surface(
      color = AppColors.secondary,
      shape = RoundedCornerShape(OnBoardingDimensions.bigPadding),
      modifier = Modifier.fillMaxWidth().padding(bottom = OnBoardingDimensions.mediumPadding)) {
        Column {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                  Modifier.padding(
                          horizontal = OnBoardingDimensions.pageIndicatorSelected,
                          vertical = OnBoardingDimensions.mediumPadding)
                      .fillMaxWidth()) {
                Surface(
                    shape = CircleShape,
                    color = AppColors.primary,
                    modifier = Modifier.size(OnBoardingDimensions.mapMarkerSize),
                ) {
                  Image(
                      painter = painterResource(id = R.drawable.google_logo),
                      contentDescription = "Discussion Avatar",
                      modifier = Modifier.padding(OnBoardingDimensions.mediumPadding))
                }
                Spacer(Modifier.width(OnBoardingDimensions.pageIndicatorSelected))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      OnBoardingStrings.BOARD_GAME_NIGHT,
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 14.sp,
                      color = AppColors.textIcons,
                      modifier = Modifier.testTag("DiscussionHeader_Title"))
                  Text(
                      OnBoardingStrings.FIVE_NEW_MESSAGES,
                      fontSize = 10.sp,
                      color = AppColors.textIconsFade)
                }
                Spacer(modifier = Modifier.width(OnBoardingDimensions.spacerSize))
                Icon(
                    imageVector = Icons.Default.LibraryAdd,
                    contentDescription = "Session",
                    tint = AppColors.textIcons)
              }
          HorizontalDivider(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = OnBoardingDimensions.dividerHorizontalPadding,
                          vertical = OnBoardingDimensions.strokeWidth))
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
  Spacer(Modifier.height(OnBoardingDimensions.mediumPadding))

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
      fontSize = 14.sp,
      color = AppColors.textIcons,
      modifier =
          Modifier.padding(bottom = OnBoardingDimensions.pageIndicatorSelected).fillMaxWidth())
}

@Composable
fun NavigationControls(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<OnBoardPage>,
    hasInteractedWithDiscussion: Boolean,
    onNavigate: (Int) -> Unit
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = OnBoardingDimensions.bigPadding),
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
          Modifier.size(OnBoardingDimensions.navigationButtonSize)
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
      val targetSize =
          if (isSelected) OnBoardingDimensions.pageIndicatorSelected
          else OnBoardingDimensions.mediumPadding
      val animatedSize by animateFloatAsState(targetValue = targetSize.value, label = "dot_size")

      Box(
          modifier =
              Modifier.padding(horizontal = 3.dp)
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
            Modifier.size(OnBoardingDimensions.navigationButtonSize)
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
            Modifier.size(OnBoardingDimensions.navigationButtonSize)
                .testTag(OnBoardingTestTags.NEXT_BUTTON))
  }
}

@Composable
@Preview(showBackground = true)
fun OnBoardPagePreview() {
  AppTheme {
    val sample =
        OnBoardPage(
            image = R.drawable.discussion_logo,
            title = "Welcome",
            description = "Discover events and meet new people.")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(OnBoardingDimensions.mapMarkerPulseRadius)) {
          Image(
              painter = painterResource(id = R.drawable.discussion_logo),
              contentDescription = null,
              modifier = Modifier.size(300.dp))
          Text(sample.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
          Text(sample.description, color = AppColors.secondary)
        }
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
        modifier = Modifier.fillMaxSize().padding(OnBoardingDimensions.mapMarkerPulseRadius)) {
          PageImage(imageRes = logoRes)
          PageTitle(title = "Meeple Meet", pageIndex = 0)
          Spacer(modifier = Modifier.height(OnBoardingDimensions.bigPadding))
          Column(
              horizontalAlignment = Alignment.Start,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp)) {
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
          Spacer(modifier = Modifier.height(OnBoardingDimensions.mapMarkerPulseRadius))
          Text(
              text = OnBoardingStrings.MEEPLE_MEET_INTRO_DESCRIPTION,
              fontSize = 16.sp,
              color = AppColors.secondary,
              textAlign = TextAlign.Center,
              modifier =
                  Modifier.fillMaxWidth().padding(horizontal = OnBoardingDimensions.bigPadding))
        }
  }
}

@Composable
private fun IntroFeatureCard(icon: ImageVector, text: String, tint: Color = AppColors.textIcons) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(vertical = OnBoardingDimensions.strokeWidth)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(OnBoardingDimensions.pageIndicatorSelected))
        Text(
            text = text,
            fontSize = 16.sp,
            color = AppColors.textIcons,
            modifier = Modifier.weight(1f))
      }
}

@Composable
fun MapExplorationPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = OnBoardingDimensions.mediumPadding)) {
        PageTitle(title = "Explore Nearby", pageIndex = 2)

        Text(
            text = OnBoardingStrings.MAP_EXPLORATION_SUBTITLE,
            fontSize = 14.sp,
            color = AppColors.secondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = OnBoardingDimensions.mapMarkerPulseRadius,
                    vertical = OnBoardingDimensions.pageIndicatorSelected))

        // Map visualization with real map background
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(OnBoardingDimensions.mapHeight)
                    .padding(horizontal = OnBoardingDimensions.bigPadding)
                    .clip(RoundedCornerShape(OnBoardingDimensions.bigPadding))
                    .border(
                        width = OnBoardingDimensions.borderWidth,
                        color = AppColors.primary,
                        shape = RoundedCornerShape(OnBoardingDimensions.bigPadding))) {
              // Real map background image
              Image(
                  painter = painterResource(id = R.drawable.maps_logo),
                  contentDescription = "Map background",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize())

              // Session marker #1 (Red - Gaming Sessions)
              MapMarkerWithDrawable(
                  offsetX = OnBoardingDimensions.offsetX90,
                  offsetY = OnBoardingDimensions.offsetY80,
                  color = AppColors.focus,
                  iconResId = R.drawable.ic_dice,
                  label = "Game Session")

              // Shop location marker #1 (Purple - Buy Games)
              MapMarkerWithDrawable(
                  offsetX = OnBoardingDimensions.offsetX240,
                  offsetY = OnBoardingDimensions.offsetY240,
                  color = AppColors.neutral,
                  iconResId = R.drawable.ic_storefront,
                  label = "Board Game Shop")

              // Rental space marker #1 (Orange - Rent Space/Games)
              MapMarkerWithDrawable(
                  offsetX = OnBoardingDimensions.offsetX180,
                  offsetY = OnBoardingDimensions.offsetY140,
                  color = AppColors.focus,
                  iconResId = R.drawable.ic_table,
                  label = "Gaming Cafe")

              // Rental space marker #2 (Orange - Rent Space/Games)
              MapMarkerWithDrawable(
                  offsetX = OnBoardingDimensions.offsetX70,
                  offsetY = OnBoardingDimensions.offsetY260,
                  color = AppColors.negative,
                  iconResId = R.drawable.ic_table,
                  label = "Event Space")

              // Your location marker (Blue)
              Box(
                  modifier =
                      Modifier.offset(
                              x = OnBoardingDimensions.offsetX280,
                              y = OnBoardingDimensions.offsetY180)
                          .size(OnBoardingDimensions.mapMarkerPulseRadius)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                      drawCircle(
                          color = Color(0xFF3B82F6),
                          radius = OnBoardingDimensions.spacerSize.toPx(),
                      )
                      drawCircle(
                          color = Color.White, // need white border even in dark mode
                          radius = OnBoardingDimensions.spacerSize.toPx(),
                          style = Stroke(width = 3.dp.toPx()))
                    }
                  }

              // "You" label
              Text(
                  text = "You",
                  fontSize = OnBoardingDimensions.mapLegendInternalIconSize,
                  fontWeight = FontWeight.Bold,
                  color = AppColors.neutral,
                  modifier =
                      Modifier.offset(
                          x = OnBoardingDimensions.offsetX268, y = OnBoardingDimensions.offsetY208))
            }

        Spacer(modifier = Modifier.height(OnBoardingDimensions.pageIndicatorSelected))

        // Legend
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = OnBoardingDimensions.mapMarkerPulseRadius),
            verticalArrangement = Arrangement.spacedBy(OnBoardingDimensions.spacerSize)) {
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

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = OnBoardingStrings.MAP_EXPLORATION_END_TEXT,
            fontSize = 13.sp,
            color = AppColors.secondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(horizontal = 32.dp, vertical = OnBoardingDimensions.bigPadding))
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
          Modifier.offset(x = offsetX, y = offsetY).size(OnBoardingDimensions.mapMarkerSize)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          // Pulse effect
          drawCircle(
              color = color.copy(alpha = 0.2f),
              radius = OnBoardingDimensions.mapMarkerPulseRadius.toPx())
          // Main dot
          drawCircle(color = color, radius = OnBoardingDimensions.mapMarkerRadius.toPx())
        }
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(OnBoardingDimensions.IconSize).align(Alignment.Center))
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
        modifier = Modifier.size(OnBoardingDimensions.IconSize).background(color, CircleShape),
        contentAlignment = Alignment.Center) {
          Icon(
              painter = painterResource(id = iconResId),
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(11.dp))
        }
    Spacer(modifier = Modifier.width(OnBoardingDimensions.mapMarkerRadius))
    Column {
      Text(
          text = title,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = AppColors.textIcons)
      Text(text = description, fontSize = 12.sp, color = AppColors.secondary, lineHeight = 16.sp)
    }
  }
}

@Composable
private fun MapLegendItem(color: Color, icon: ImageVector?, title: String, description: String) {
  Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier =
            Modifier.size(OnBoardingDimensions.IconSize)
                .background(
                    color = if (icon == null) color else Color.Transparent, shape = CircleShape)
                .border(
                    width = if (icon == null) 2.dp else 0.dp,
                    color = if (icon == null) Color.White else Color.Transparent,
                    shape = CircleShape),
        contentAlignment = Alignment.Center) {
          if (icon != null) {
            Box(
                modifier =
                    Modifier.size(OnBoardingDimensions.IconSize).background(color, CircleShape),
                contentAlignment = Alignment.Center) {
                  Icon(
                      imageVector = icon,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(11.dp))
                }
          }
        }
    Spacer(modifier = Modifier.width(OnBoardingDimensions.mapMarkerRadius))
    Column {
      Text(
          text = title,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = AppColors.textIcons)
      Text(text = description, fontSize = 12.sp, color = AppColors.secondary, lineHeight = 16.sp)
    }
  }
}

// ==================== SESSIONS PAGE ====================

@Composable
fun SessionsPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = OnBoardingDimensions.mediumPadding)) {
        PageTitle(title = "Game Sessions", pageIndex = 3)

        Text(
            text = OnBoardingStrings.SESSION_PAGE_SUBTITLE,
            fontSize = 14.sp,
            color = AppColors.secondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = OnBoardingDimensions.mapMarkerPulseRadius,
                    vertical = OnBoardingDimensions.bigPadding))

        // Session cards visualization
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = OnBoardingDimensions.bigPadding),
            verticalArrangement =
                Arrangement.spacedBy(OnBoardingDimensions.pageIndicatorSelected)) {
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

        Spacer(modifier = Modifier.height(OnBoardingDimensions.bigPadding))

        // Features grid
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = OnBoardingDimensions.mapMarkerPulseRadius),
            verticalArrangement = Arrangement.spacedBy(OnBoardingDimensions.spacerSize)) {
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
        modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(OnBoardingDimensions.spacerSize))
    Text(text = text, fontSize = 13.sp, color = AppColors.textIcons)
  }
}

// ==================== POSTS PAGE ====================

@Composable
fun PostsPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier =
          Modifier.fillMaxSize().padding(top = OnBoardingDimensions.pageIndicatorUnselected)) {
        PageTitle(title = "Community Posts", pageIndex = 4)

        Text(
            text = OnBoardingStrings.POST_PAGE_SUBTITLE,
            fontSize = 14.sp,
            color = AppColors.secondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(
                    horizontal = OnBoardingDimensions.mapMarkerPulseRadius,
                    vertical = OnBoardingDimensions.bigPadding))

        // Post cards visualization
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = OnBoardingDimensions.bigPadding),
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

        Spacer(modifier = Modifier.height(OnBoardingDimensions.bigPadding))

        // Features grid
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = OnBoardingDimensions.mapMarkerPulseRadius),
            verticalArrangement = Arrangement.spacedBy(OnBoardingDimensions.spacerSize)) {
              PostFeatureItem(
                  icon = Icons.Default.Article, text = "Create threads about any board game topic")
              PostFeatureItem(
                  icon = Icons.Default.ThumbUp, text = "Like and interact with community posts")
            }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = OnBoardingStrings.POSTS_PAGE_END_TEXT,
            fontSize = 13.sp,
            color = AppColors.secondary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(horizontal = 32.dp, vertical = OnBoardingDimensions.bigPadding))
      }
}

@Composable
private fun PostFeatureItem(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = AppColors.focus,
        modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(OnBoardingDimensions.spacerSize))
    Text(text = text, fontSize = 13.sp, color = AppColors.textIcons)
  }
}
