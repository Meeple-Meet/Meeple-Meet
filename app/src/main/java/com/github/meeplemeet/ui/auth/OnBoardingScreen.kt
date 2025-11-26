// AI helped generate some of this code
package com.github.meeplemeet.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.github.meeplemeet.ui.discussions.ChatBubble
import com.github.meeplemeet.ui.discussions.PollBubble
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.AppTheme
import java.util.Date
import kotlinx.coroutines.launch

// Test tags used for UI testing
object OnBoardingTestTags {
  const val SkipButton = "OnBoarding_SkipButton"
  const val PageTitle = "OnBoarding_PageTitle"
  const val BackButton = "OnBoarding_BackButton"
  const val NextButton = "OnBoarding_NextButton"
  const val PagerDot = "OnBoarding_PagerDot"
  const val Pager = "OnBoarding_Pager"
  const val DiscussionPreviewCard = "DiscussionPreviewCard"
  const val CloseDialog = "OnBoarding_CloseDialog"
}

data class OnBoardPage(val image: Int, val title: String, val description: String)

@Composable
fun OnBoardingScreen(pages: List<OnBoardPage>, onSkip: () -> Unit, onFinished: () -> Unit) {
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val hasInteractedWithDiscussion = remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  Box(
      modifier =
          Modifier.fillMaxSize().background(AppColors.primary).padding(24.dp).pointerInput(
              pagerState.currentPage) {
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
            }
      }
}

@Composable
private fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
  Button(
      onClick = onSkip,
      colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
      contentPadding = PaddingValues(8.dp),
      modifier = modifier.zIndex(1f).testTag(OnBoardingTestTags.SkipButton)) {
        Text(text = "Skip", color = Color.Gray)
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
      modifier = modifier.fillMaxWidth().testTag(OnBoardingTestTags.Pager),
      userScrollEnabled = pagerState.currentPage != 1 || hasInteractedWithDiscussion.value) { page
        ->
        when (page) {
          0 -> MeepleMeetIntroPage(pageData = pages[page])
          1 ->
              DiscussionPreviewPage(
                  pageData = pages[page], hasInteractedWithDiscussion = hasInteractedWithDiscussion)
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
            text =
                "Meeple Meet helps you connect with new friends and join fun discussions around your favorite games.",
            fontSize = 16.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 24.dp))

        Spacer(modifier = Modifier.height(32.dp))

        DiscussionPreviewCard(
            onClick = {
              showDialog.value = true
              hasInteractedWithDiscussion.value = true
            })

        Spacer(modifier = Modifier.height(10.dp))

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
      modifier = Modifier.size(400.dp).padding(bottom = 24.dp))
}

@Composable
private fun PageTitle(title: String, pageIndex: Int) {
  Text(
      text = title,
      fontSize = 32.sp,
      fontWeight = FontWeight.Bold,
      modifier =
          Modifier.padding(bottom = 12.dp).testTag("${OnBoardingTestTags.PageTitle}_$pageIndex"))
}

@Composable
private fun PageDescription(description: String) {
  Text(
      text = description,
      fontSize = 16.sp,
      color = Color.Gray,
      modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
fun DiscussionPreviewCard(onClick: () -> Unit) {
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  Surface(
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .heightIn(min = 68.dp, max = 84.dp)
              .focusRequester(focusRequester)
              .testTag(OnBoardingTestTags.DiscussionPreviewCard),
      shape = RoundedCornerShape(24.dp),
      tonalElevation = 8.dp,
      shadowElevation = 4.dp,
      color = AppColors.primary,
      onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
              DiscussionAvatar()
              DiscussionInfo()
              DiscussionTimestamp()
            }
      }
}

@Composable
private fun RowScope.DiscussionAvatar() {
  Box(modifier = Modifier.padding(end = 16.dp)) {
    Surface(
        shape = CircleShape,
        color = AppColors.primary,
        modifier = Modifier.size(64.dp),
        tonalElevation = 0.dp) {
          Image(
              painter = painterResource(id = R.drawable.logo_discussion),
              contentDescription = "Avatar",
              modifier = Modifier.padding(8.dp))
        }
    Badge(
        containerColor = AppColors.focus,
        contentColor = Color.White,
        modifier = Modifier.align(Alignment.TopEnd)) {
          Text("2", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
  }
}

@Composable
private fun RowScope.DiscussionInfo() {
  Column(modifier = Modifier.weight(1f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
          text = "Board Game Night",
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
        fontSize = 11.sp,
        maxLines = 1)
  }
}

@Composable
private fun DiscussionTimestamp() {
  Spacer(modifier = Modifier.width(10.dp))
  Column(horizontalAlignment = Alignment.End) {
    Text(text = "21:01", fontSize = 9.sp, color = Color.Gray)
    Spacer(modifier = Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
      Text(text = "Tap", fontSize = 10.sp, color = AppColors.focus, fontWeight = FontWeight.Medium)
      Spacer(modifier = Modifier.width(4.dp))
      Icon(
          imageVector = Icons.Default.ArrowForward,
          contentDescription = "Tap to explore",
          tint = AppColors.focus,
          modifier = Modifier.size(12.dp))
    }
  }
}

@Composable
private fun InteractionPrompt(hasInteracted: Boolean) {
  Text(
      text =
          if (!hasInteracted) {
            "⬆️ Tap the discussion above to continue"
          } else {
            "Jump into the conversation and never miss a meetup!"
          },
      fontSize = if (!hasInteracted) 14.sp else 15.sp,
      color = if (!hasInteracted) AppColors.focus else AppColors.textIconsFade,
      fontWeight = if (!hasInteracted) FontWeight.Bold else FontWeight.Normal,
      modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp))
}

@Composable
fun DiscussionDetailDialog(onDismiss: () -> Unit) {
  androidx.compose.animation.AnimatedVisibility(
      visible = true,
      enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandIn(),
      exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkOut()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
            containerColor = AppColors.secondary,
            tonalElevation = 0.dp,
            shape = MaterialTheme.shapes.large,
            title = null,
            icon = null,
            text = {
              Column(
                  modifier = Modifier.fillMaxSize().padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.testTag(OnBoardingTestTags.CloseDialog)) {
          Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Session Creation",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(1f))
      }
}

@Composable
private fun DialogInstructions() {
  Column(
      modifier = Modifier.fillMaxWidth().clickable { /* TODO */}.padding(vertical = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Use the button next to the discussion's name to create or join a session",
            fontSize = 12.sp,
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(top = 2.dp, bottom = 4.dp).align(Alignment.CenterHorizontally))
      }
}

@Composable
private fun DiscussionHeader() {
  Surface(
      color = AppColors.secondary,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column {
          Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
                Surface(
                    shape = CircleShape,
                    color = AppColors.primary,
                    modifier = Modifier.size(48.dp)) {
                      Image(
                          painter = painterResource(id = R.drawable.google_logo),
                          contentDescription = "Discussion Avatar",
                          modifier = Modifier.padding(8.dp))
                    }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                      "Board Game Night",
                      fontWeight = FontWeight.SemiBold,
                      fontSize = 14.sp,
                      color = AppColors.textIcons,
                      modifier = Modifier.testTag("DiscussionHeader_Title"))
                  Text("5 new messages", fontSize = 10.sp, color = AppColors.textIconsFade)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.LibraryAdd,
                    contentDescription = "Session",
                    tint = AppColors.textIcons)
              }
          HorizontalDivider(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 4.dp))
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
  Spacer(Modifier.height(8.dp))

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
      color = Color.DarkGray,
      modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth())
}

@Composable
fun NavigationControls(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<OnBoardPage>,
    hasInteractedWithDiscussion: Boolean,
    onNavigate: (Int) -> Unit
) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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
          Modifier.size(42.dp)
              .then(
                  if (canGoBack) {
                    Modifier.background(color = AppColors.primary, shape = CircleShape)
                        .clickable(onClick = onClick)
                  } else {
                    Modifier
                  })
              .testTag(OnBoardingTestTags.BackButton),
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
      val targetSize = if (isSelected) 12.dp else 8.dp
      val animatedSize by animateFloatAsState(targetValue = targetSize.value, label = "dot_size")

      Box(
          modifier =
              Modifier.padding(horizontal = 3.dp)
                  .size(animatedSize.dp)
                  .background(
                      color = if (isSelected) Color.DarkGray else Color.LightGray,
                      shape = CircleShape)
                  .testTag(OnBoardingTestTags.PagerDot))
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
            Modifier.size(42.dp)
                .background(
                    color = if (isEnabled) AppColors.primary else Color.LightGray,
                    shape = CircleShape)
                .clickable(enabled = isEnabled, onClick = onClick)
                .testTag(OnBoardingTestTags.NextButton),
        contentAlignment = Alignment.Center) {
          Icon(
              imageVector = Icons.Default.ArrowForward,
              contentDescription = "Next",
              tint = if (isEnabled) AppColors.textIcons else Color.Gray)
        }
  } else {
    Box(modifier = Modifier.size(42.dp).testTag(OnBoardingTestTags.NextButton))
  }
}

@Composable
@Preview(showBackground = true)
fun OnBoardPagePreview() {
  AppTheme {
    val sample =
        OnBoardPage(
            image = R.drawable.logo_discussion,
            title = "Welcome",
            description = "Discover events and meet new people.")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(24.dp)) {
          Image(
              painter = painterResource(id = R.drawable.logo_discussion),
              contentDescription = null,
              modifier = Modifier.size(300.dp))
          Text(sample.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
          Text(sample.description, color = Color.Gray)
        }
  }
}

@Composable
@Preview(showBackground = true)
fun OnBoardingScreenPreview() {
  AppTheme {
    val pages =
        listOf(
            OnBoardPage(image = R.drawable.logo_dark, title = "Meeple Meet", description = ""),
            OnBoardPage(
                image = R.drawable.logo_discussion,
                title = "Discussions",
                description =
                    "Host your own gatherings and join discussions about your favorite games."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_compass,
                title = "Explore",
                description = "Find activities, shops, and game events near you."))

    OnBoardingScreen(pages = pages, onSkip = {}, onFinished = {})
  }
}

public val votes: Map<String, List<Int>> = mapOf("1" to listOf(0), "2" to emptyList())
// --- MeepleMeetIntroPage composable (if present, update it as requested) ---

@Composable
fun MeepleMeetIntroPage(pageData: OnBoardPage) {
  val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
  val logoRes = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo_clear
  Box(modifier = Modifier.fillMaxSize().background(AppColors.primary)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier.fillMaxSize().padding(24.dp)) {
          PageImage(imageRes = logoRes)
          PageTitle(title = "Meeple Meet", pageIndex = 0)
          Spacer(modifier = Modifier.height(16.dp))
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
          Spacer(modifier = Modifier.height(24.dp))
          Text(
              text =
                  "Meeple Meet helps you organize game sessions, join discussions, explore shops, check prices, and find local gaming spaces.",
              fontSize = 16.sp,
              color = Color.Gray,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }
  }
}

@Composable
private fun IntroFeatureCard(icon: ImageVector, text: String, tint: Color = AppColors.textIcons) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 0.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = AppColors.textIcons,
            modifier = Modifier.weight(1f))
      }
}
