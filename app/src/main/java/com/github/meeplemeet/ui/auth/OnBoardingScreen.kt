// AI helped generate some of this code especially for magic numbers
package com.github.meeplemeet.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.github.meeplemeet.R
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.shared.location.Location
import com.github.meeplemeet.ui.posts.FeedCard
import com.github.meeplemeet.ui.sessions.SessionOverCard
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch

/** Test tags used for UI testing in the onboarding flow. */
object OnBoardingTestTags {
  const val SKIP_BUTTON = "OnBoarding_SkipButton"
  const val PAGE_TITLE = "OnBoarding_PageTitle"
  const val BACK_BUTTON = "OnBoarding_BackButton"
  const val NEXT_BUTTON = "OnBoarding_NextButton"
  const val PAGER_DOT = "OnBoarding_PagerDot"
  const val PAGER = "OnBoarding_Pager"
  const val DISCUSSION_PREVIEW_CARD = "DiscussionPreviewCard"
  const val CLOSE_DIALOG = "OnBoarding_CloseDialog"
  const val SESSION_CREATION_PAGE = "OnBoarding_SessionCreationPage"
  const val SESSION_CREATION_DATETIME = "OnBoarding_SessionCreationDateTime"
  const val SESSION_CREATION_PARTICIPANTS = "OnBoarding_SessionCreationParticipants"

  fun sessionCreationParticipant(name: String) = "OnBoarding_SessionCreationParticipant:$name"
}

/** String constants for onboarding screen content and labels. */
object OnBoardingStrings {
  const val SKIP = "Skip"
  const val MEEPLE_MEET_INTRO_DESCRIPTION =
      "Meeple Meet helps you organize game sessions, join discussions, explore shops, check prices, and find local gaming spaces."
  const val SESSION_PAGE_SUBTITLE = "Organize and join gaming meetups with friends"
  const val POST_PAGE_SUBTITLE = "Share your gaming experiences with the world"
  const val MAP_EXPLORATION_SUBTITLE = "Find board game shops and rental spaces near you"
  const val MAP_EXPLORATION_END_TEXT =
      "Browse shops, compare prices, and discover venues for your next game night!"
  const val POSTS_PAGE_END_TEXT =
      "Connect with gamers worldwide! Share strategies, reviews, and join the conversation about your favorite games."
  const val BOARD_GAME_NIGHT = "Board Game Night"
  const val FIVE_NEW_MESSAGES = "5 new messages"
  const val PARTICIPANTS_SECTION_TEXT = "Participants"
  const val PARTICIPANT_1 = "Alexandre"
  const val PARTICIPANT_2 = "Thomas"
  const val DATE_PREVIEW = "Date"
  const val TIME_PREVIEW = "Time"
  const val SESSION_CREATION_CHOOSE_FRIENDS = "Choose a schedule and invite friends"
  const val SESSION_CREATION_CREATE_SESSION_BUTTON =
      "Use the button in the discussion's screen to create a new session"
  const val SESSION_CREATION_TITLE = "Create Sessions"
}

/** Numeric constants for onboarding screen layout and styling. */
object OnBoardingNumbers {
  val DATE_AND_TIME_PREVIEW_HEIGHT: Dp = 10.dp
  const val DATE_PREVIEW_WIDTH_FACTOR = 0.55f
  val DISCUSSION_CARD_BORDER_STROKE_WIDTH = 1.5.dp
  const val DISCUSSION_CARD_BORDER_STROKE_COLOR_ALPHA = 0.20f
  val DISCUSSION_CARD_ELEVATION = 12.dp
  const val PARTICIPANTS_AND_SCHEDULE_CARD_ALPHA = 0.1f
  val PARTICIPANTS_AND_SCHEDULE_CARD_WIDTH = 1.dp
  val SESSION_CREATION_IMAGE_HEIGHT = 260.dp
}

/**
 * Data class representing a single onboarding page's content.
 *
 * @param image Resource ID for the page image
 * @param title Title text for the onboarding page
 * @param description Description text for the onboarding page
 */
data class OnBoardPage(val image: Int, val title: String, val description: String)

/**
 * Composable for the onboarding screen with multiple pages.
 *
 * @param pages List of onboarding pages to display
 * @param onSkip Callback when user skips onboarding
 * @param onFinished Callback when onboarding is completed
 */
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
              Box(modifier = Modifier.fillMaxWidth().weight(Dimensions.Weight.full)) {
                OnBoardingPager(
                    pagerState = pagerState,
                    pages = pages,
                    hasInteractedWithDiscussion = hasInteractedWithDiscussion,
                    modifier = Modifier.fillMaxSize())

                SkipButton(onSkip = onSkip, modifier = Modifier.align(Alignment.TopEnd))
              }

              NavigationControls(
                  pagerState = pagerState,
                  pages = pages,
                  hasInteractedWithDiscussion = hasInteractedWithDiscussion.value,
                  onNavigate = { page -> scope.launch { pagerState.animateScrollToPage(page) } })

              // Show end button if last page
              if (pagerState.currentPage == pages.lastIndex) {
                Button(
                    onClick = onFinished,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.focus),
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(
                                horizontal = Dimensions.CornerRadius.round,
                                vertical = Dimensions.Padding.extraLarge)
                            .testTag("OnBoarding_EndButton")) {
                      Text(text = "Get Started!")
                    }
              }
            }
      }
}

/**
 * Composable for the skip button in onboarding.
 *
 * @param onSkip Callback when skip is pressed
 * @param modifier Modifier for styling
 */
@Composable
private fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
  Button(
      onClick = onSkip,
      colors = ButtonDefaults.buttonColors(containerColor = AppColors.primary),
      contentPadding = PaddingValues(Dimensions.Padding.medium),
      modifier = modifier.zIndex(Dimensions.Weight.full).testTag(OnBoardingTestTags.SKIP_BUTTON)) {
        Text(text = OnBoardingStrings.SKIP, color = AppColors.divider)
      }
}

/**
 * Pager composable for displaying onboarding pages.
 *
 * @param pagerState Pager state to control the current page
 * @param pages List of onboarding pages
 * @param hasInteractedWithDiscussion State tracking interaction on discussion page
 * @param modifier Modifier for styling
 */
@Composable
private fun OnBoardingPager(
    pagerState: PagerState,
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
              SessionCreationPreviewPage(
                  pageData = pages[page], hasInteractedWithDiscussion = hasInteractedWithDiscussion)
          2 -> SessionsPage()
          3 -> PostsPage()
          4 -> MapExplorationPage()
          5 -> LetsGoPage()
          else -> StandardOnBoardingPage(pageData = pages[page], pageIndex = page)
        }
      }
}

/**
 * Composable for a standard onboarding page with image, title, and description.
 *
 * @param pageData Data for the current onboarding page
 * @param pageIndex Index of the current page
 */
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

/**
 * Composable for the session creation preview onboarding page.
 *
 * @param pageData Data for the page
 * @param hasInteractedWithDiscussion Mutable state tracking user interaction
 */
@Composable
fun SessionCreationPreviewPage(
    pageData: OnBoardPage,
    hasInteractedWithDiscussion: MutableState<Boolean>
) {
  LaunchedEffect(Unit) {
    if (!hasInteractedWithDiscussion.value) {
      hasInteractedWithDiscussion.value = true
    }
  }

  val isDarkTheme = isSystemInDarkTheme()
  val logoRes = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier =
          Modifier.fillMaxSize()
              .padding(top = Dimensions.Padding.medium)
              .testTag(OnBoardingTestTags.SESSION_CREATION_PAGE)) {
        PageTitle(title = OnBoardingStrings.SESSION_CREATION_TITLE, pageIndex = 1)

        Image(
            painter = painterResource(id = R.drawable.onboarding_session_discussion),
            contentDescription = null,
            modifier =
                Modifier.fillMaxWidth().height(OnBoardingNumbers.SESSION_CREATION_IMAGE_HEIGHT),
            contentScale = ContentScale.Fit)

        Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

        Text(
            text = OnBoardingStrings.SESSION_CREATION_CREATE_SESSION_BUTTON,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.medium))

        Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

        CreateSessionDiscussionPreviewCard(
            logoRes = logoRes,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.medium)
                    .testTag(OnBoardingTestTags.DISCUSSION_PREVIEW_CARD))

        Spacer(modifier = Modifier.height(Dimensions.Spacing.xLarge))

        Text(
            text = OnBoardingStrings.SESSION_CREATION_CHOOSE_FRIENDS,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.medium))

        Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimensions.Padding.medium),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background,
            shadowElevation = OnBoardingNumbers.DISCUSSION_CARD_ELEVATION,
            border =
                BorderStroke(
                    width = OnBoardingNumbers.PARTICIPANTS_AND_SCHEDULE_CARD_WIDTH,
                    color =
                        MaterialTheme.colorScheme.outline.copy(
                            alpha = OnBoardingNumbers.PARTICIPANTS_AND_SCHEDULE_CARD_ALPHA)),
        ) {
          Column(
              modifier = Modifier.padding(Dimensions.Padding.medium),
              verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.large)) {
                OnBoardingDateTimePreview(
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(OnBoardingTestTags.SESSION_CREATION_DATETIME))
                OnBoardingParticipantsPreview(
                    modifier =
                        Modifier.fillMaxWidth()
                            .testTag(OnBoardingTestTags.SESSION_CREATION_PARTICIPANTS))
              }
        }

        Spacer(modifier = Modifier.height(Dimensions.Spacing.large))
      }
}

/**
 * Composable for displaying the page image.
 *
 * @param imageRes Resource ID of the image to display
 */
@Composable
private fun PageImage(imageRes: Int) {
  Image(
      painter = painterResource(id = imageRes),
      contentDescription = null,
      modifier =
          Modifier.size(Dimensions.ContainerSize.pageImageSize)
              .padding(bottom = Dimensions.CornerRadius.round))
}

/**
 * Composable for displaying the page title.
 *
 * @param title Title text to display
 * @param pageIndex Index of the page
 */
@Composable
private fun PageTitle(title: String, pageIndex: Int) {
  Text(
      text = title,
      fontSize = Dimensions.TextSize.large,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.tertiary,
      modifier =
          Modifier.padding(bottom = Dimensions.Padding.large)
              .testTag("${OnBoardingTestTags.PAGE_TITLE}_$pageIndex"))
}

/**
 * Composable for displaying the page description.
 *
 * @param description Description text to display
 */
@Composable
private fun PageDescription(description: String) {
  Text(
      text = description,
      fontSize = Dimensions.TextSize.subtitle,
      color = AppColors.secondary,
      modifier = Modifier.padding(horizontal = Dimensions.CornerRadius.round))
}

/**
 * Composable for navigation controls (back, page indicators, next) in onboarding.
 *
 * @param pagerState Pager state for current page
 * @param pages List of onboarding pages
 * @param hasInteractedWithDiscussion Whether discussion interaction has occurred
 * @param onNavigate Callback for navigation between pages
 */
@Composable
fun NavigationControls(
    pagerState: PagerState,
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

/**
 * Composable for the back button in onboarding navigation.
 *
 * @param canGoBack Whether the back button is enabled
 * @param onClick Callback when the back button is pressed
 */
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
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = AppColors.textIcons)
        }
      }
}

/**
 * Composable for displaying page indicators (dots) in onboarding.
 *
 * @param pageCount Total number of pages
 * @param currentPage Index of the current page
 */
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
                      color =
                          if (isSelected) MaterialTheme.colorScheme.inversePrimary
                          else MaterialTheme.colorScheme.surface,
                      shape = CircleShape)
                  .testTag(OnBoardingTestTags.PAGER_DOT))
    }
  }
}

/**
 * Composable for the next button in onboarding navigation.
 *
 * @param currentPage Index of the current page
 * @param lastIndex Index of the last page
 * @param hasInteractedWithDiscussion Whether discussion interaction has occurred
 * @param onClick Callback when the next button is pressed
 */
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
              imageVector = Icons.AutoMirrored.Filled.ArrowForward,
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

/** Composable for the Meeple Meet introduction onboarding page. */
@Composable
fun MeepleMeetIntroPage() {
  val isDarkTheme = isSystemInDarkTheme()
  val logoRes = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear
  Box(modifier = Modifier.fillMaxSize().background(AppColors.primary)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier.fillMaxSize().padding(Dimensions.Spacing.xxLarge)) {
          PageImage(imageRes = logoRes)
          PageTitle(title = "Meeple Meet", pageIndex = 0)
          Spacer(modifier = Modifier.height(Dimensions.Spacing.small))
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

/**
 * Composable for displaying a feature card in the intro page.
 *
 * @param icon Icon to display
 * @param text Description of the feature
 * @param tint Tint color for the icon
 */
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

/** Composable for the map exploration onboarding page. */
@Composable
fun MapExplorationPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = Dimensions.Padding.medium)) {
        PageTitle(title = "Explore Nearby", pageIndex = 4)

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
                  color = AppColors.negative,
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
                  color = AppColors.focus,
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
                  color = AppColors.focus,
                  iconResId = R.drawable.ic_table,
                  title = "Rental Spaces",
                  description = "Rent games or book venues for sessions")
              MapLegendItemWithDrawable(
                  color = AppColors.neutral,
                  iconResId = R.drawable.ic_storefront,
                  title = "Game Shops",
                  description = "Buy board games with prices and stock info")
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

/**
 * Composable for displaying a map marker with an icon.
 *
 * @param offsetX X offset for marker placement
 * @param offsetY Y offset for marker placement
 * @param color Color of the marker
 * @param iconResId Resource ID of the icon
 * @param label Label for the marker
 */
@Composable
private fun MapMarkerWithDrawable(
    offsetX: Dp,
    offsetY: Dp,
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

/**
 * Composable for displaying a legend item with an icon and description.
 *
 * @param color Color of the icon background
 * @param iconResId Resource ID for the icon
 * @param title Title of the legend item
 * @param description Description of the legend item
 */
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

/** Composable for the sessions onboarding page. */
@Composable
fun SessionsPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = Dimensions.Padding.medium)) {
        PageTitle(title = "Game Sessions", pageIndex = 2)

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

/**
 * Composable for displaying a feature item on the sessions page.
 *
 * @param icon Icon to display
 * @param text Description of the feature
 */
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

/** Composable for the posts onboarding page. */
@Composable
fun PostsPage() {
  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top,
      modifier = Modifier.fillMaxSize().padding(top = Dimensions.Padding.medium)) {
        PageTitle(title = "Community Posts", pageIndex = 3)

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
                  icon = Icons.AutoMirrored.Filled.Article,
                  text = "Create threads about any board game topic")
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

/**
 * Composable for displaying a feature item on the posts page.
 *
 * @param icon Icon to display
 * @param text Description of the feature
 */
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

/** Composable for the final onboarding page encouraging the user to get started. */
@Composable
fun LetsGoPage() {
  val isDarkTheme = isSystemInDarkTheme()
  val logoRes = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear

  Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize().padding(Dimensions.Spacing.xxxLarge)) {
        Spacer(modifier = Modifier.weight(0.3f))

        PageImage(imageRes = logoRes)

        PageTitle(title = "Let's Go!", pageIndex = 5)

        Text(
            text =
                "You're all set! Start connecting with gamers, joining sessions, and discovering your next favorite game.",
            fontSize = Dimensions.TextSize.subtitle,
            color = AppColors.textIcons.copy(alpha = Dimensions.Alpha.opaque),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimensions.CornerRadius.round))

        Spacer(modifier = Modifier.weight(0.7f))
      }
}

// ==================== SESSION CREATION PAGE ====================

/**
 * Composable for displaying a static discussion preview card in the onboarding session creation
 * page.
 *
 * @param logoRes Resource ID for the logo image
 * @param modifier Modifier for styling
 */
@Composable
private fun CreateSessionDiscussionPreviewCard(
    logoRes: Int,
    modifier: Modifier = Modifier,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
      elevation =
          CardDefaults.cardElevation(
              defaultElevation = OnBoardingNumbers.DISCUSSION_CARD_ELEVATION),
      border =
          BorderStroke(
              OnBoardingNumbers.DISCUSSION_CARD_BORDER_STROKE_WIDTH,
              color =
                  MaterialTheme.colorScheme.outline.copy(
                      alpha = OnBoardingNumbers.DISCUSSION_CARD_BORDER_STROKE_COLOR_ALPHA))) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(
                        horizontal = Dimensions.Padding.large,
                        vertical = Dimensions.Padding.large,
                    ),
            verticalAlignment = Alignment.CenterVertically) {
              Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier.size(Dimensions.IconSize.giant)) {
                    Image(
                        painter = painterResource(id = logoRes),
                        contentDescription = null,
                        modifier = Modifier.padding(Dimensions.Padding.medium),
                        contentScale = ContentScale.Fit)
                  }

              Spacer(modifier = Modifier.width(Dimensions.Spacing.large))

              Column(modifier = Modifier.weight(weight = 1f)) {
                Text(
                    text = OnBoardingStrings.BOARD_GAME_NIGHT,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = OnBoardingStrings.FIVE_NEW_MESSAGES,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
              }

              Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))

              Icon(
                  imageVector = Icons.Default.LibraryAdd,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                  modifier = Modifier.size(Dimensions.IconSize.extraLarge))
            }
      }
}

/**
 * Composable for displaying static date and time fields in the onboarding session creation page.
 *
 * @param modifier Modifier for styling
 */
@Composable
private fun OnBoardingDateTimePreview(modifier: Modifier = Modifier) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .background(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(Dimensions.CornerRadius.medium))
              .padding(Dimensions.Padding.medium),
      verticalAlignment = Alignment.CenterVertically) {
        StaticOnBoardingField(
            label = OnBoardingStrings.DATE_PREVIEW,
            leadingIcon = {
              Icon(
                  imageVector = Icons.Default.CalendarToday,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary)
            },
            modifier =
                Modifier.weight(OnBoardingNumbers.DATE_PREVIEW_WIDTH_FACTOR)
                    .heightIn(min = OnBoardingNumbers.DATE_AND_TIME_PREVIEW_HEIGHT))

        Spacer(modifier = Modifier.width(Dimensions.Spacing.medium))

        StaticOnBoardingField(
            label = OnBoardingStrings.TIME_PREVIEW,
            leadingIcon = {
              Icon(
                  imageVector = Icons.Default.Timer,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary)
            },
            modifier =
                Modifier.weight(weight = 1f - OnBoardingNumbers.DATE_PREVIEW_WIDTH_FACTOR)
                    .heightIn(min = OnBoardingNumbers.DATE_AND_TIME_PREVIEW_HEIGHT))
      }
}

/**
 * Composable for displaying a static onboarding field with label and leading icon.
 *
 * @param label Label text to display
 * @param modifier Modifier for styling
 * @param leadingIcon leading icon composable
 */
@Composable
private fun StaticOnBoardingField(
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit),
) {
  Row(
      modifier =
          modifier
              .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
              .padding(horizontal = Dimensions.Padding.medium, vertical = Dimensions.Padding.small),
      verticalAlignment = Alignment.CenterVertically) {
        leadingIcon()
        Spacer(modifier = Modifier.width(Dimensions.Spacing.large))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)

        Spacer(modifier = Modifier.weight(weight = 1f))

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
}

/**
 * Composable for displaying the participants section in the onboarding session creation page.
 *
 * @param modifier Modifier for styling
 */
@Composable
private fun OnBoardingParticipantsPreview(modifier: Modifier = Modifier) {

  Surface(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  Dimensions.DividerThickness.standard,
                  MaterialTheme.colorScheme.background,
                  MaterialTheme.shapes.large)
              .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.large),
      color = MaterialTheme.colorScheme.background,
      shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(Dimensions.Padding.tiny)) {
          Text(
              text = OnBoardingStrings.PARTICIPANTS_SECTION_TEXT,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface)

          Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

          Column(
              verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small),
              modifier = Modifier.fillMaxWidth()) {
                StaticParticipantCard(
                    name = OnBoardingStrings.PARTICIPANT_1,
                    avatarRes = R.drawable.onboarding_avatar1)
                StaticParticipantCard(
                    name = OnBoardingStrings.PARTICIPANT_2,
                    avatarRes = R.drawable.onboarding_avatar2)
              }
        }
      }
}

/**
 * Composable for displaying a static participant card with name, avatar, and checkbox.
 *
 * @param name Name of the participant
 * @param avatarRes Resource ID for the participant's avatar image
 * @param modifier Modifier for styling
 */
@Composable
private fun StaticParticipantCard(
    name: String,
    avatarRes: Int,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(Dimensions.CornerRadius.medium))
              .background(MaterialTheme.colorScheme.background)
              .padding(horizontal = Dimensions.Padding.medium, vertical = Dimensions.Padding.small)
              .testTag(OnBoardingTestTags.sessionCreationParticipant(name)),
      verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = avatarRes),
            contentDescription = name,
            modifier = Modifier.size(Dimensions.IconSize.xxLarge).clip(CircleShape),
            contentScale = ContentScale.Crop)

        Spacer(modifier = Modifier.width(Dimensions.Spacing.xLarge))

        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)

        Spacer(modifier = Modifier.weight(weight = 1f))

        Checkbox(
            checked = true,
            onCheckedChange = {},
            enabled = false,
            colors =
                CheckboxDefaults.colors(
                    disabledCheckedColor = MaterialTheme.colorScheme.tertiary,
                    checkmarkColor = MaterialTheme.colorScheme.onBackground,
                    disabledUncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant))
      }
}
