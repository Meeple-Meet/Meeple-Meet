// Ai helped generate some of this code
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
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import java.security.Timestamp
import java.util.Date
import kotlinx.coroutines.launch

data class OnBoardPage(val image: Int, val title: String, val description: String)

@Composable
fun OnBoardingScreen(pages: List<OnBoardPage>, onSkip: () -> Unit, onFinished: () -> Unit) {
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val configuration = LocalConfiguration.current
  val screenWidth = configuration.screenWidthDp.dp
  val scope = rememberCoroutineScope()

  // Track if user has interacted with the discussion on page 0
  val hasInteractedWithDiscussion = remember { mutableStateOf(false) }

  Box(
      modifier =
          Modifier.fillMaxSize().background(AppColors.secondary).padding(24.dp).pointerInput(
              pagerState.currentPage) {
                // Block swipe gestures on page 0 if user hasn't interacted
                if (pagerState.currentPage == 0 && !hasInteractedWithDiscussion.value) {
                  detectHorizontalDragGestures { _, _ ->
                    // Consume the gesture but do nothing
                  }
                }
              }) {
        // Top-right skip button
        Text(
            text = "Skip",
            color = Color.Gray,
            modifier = Modifier.align(Alignment.TopEnd).clickable { onSkip() }.padding(8.dp))

        if (pagerState.currentPage > 0) {
          Box(
              modifier =
                  Modifier.align(Alignment.TopStart)
                      .size(42.dp)
                      .zIndex(1f)
                      .background(color = AppColors.primary, shape = CircleShape)
                      .clickable {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                      },
              contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = AppColors.textIcons)
              }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
              HorizontalPager(
                  state = pagerState,
                  modifier = Modifier.weight(1f).fillMaxWidth(),
                  userScrollEnabled =
                      pagerState.currentPage != 0 || hasInteractedWithDiscussion.value) { page ->
                    if (page == 0) {
                      // First onboarding page: include a realistic discussion preview
                      // Add dialog state
                      val showDialog = remember { mutableStateOf(false) }
                      val focusRequester = remember { FocusRequester() }

                      // Auto-focus the discussion card when page loads
                      LaunchedEffect(Unit) { focusRequester.requestFocus() }

                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.Center,
                          modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_discussion),
                                contentDescription = null,
                                modifier = Modifier.size(400.dp).padding(bottom = 24.dp))
                            Text(
                                text = pages[page].title,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp))
                            Text(
                                text =
                                    "Meeple Meet helps you connect with new friends and join fun discussions around your favorite games.",
                                fontSize = 16.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(modifier = Modifier.height(32.dp))
                            // Miniature Discussion Preview (scaled up avatar & badge)
                            androidx.compose.material3.Surface(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .heightIn(min = 68.dp, max = 84.dp)
                                        .focusRequester(focusRequester),
                                shape = RoundedCornerShape(24.dp),
                                tonalElevation = 8.dp,
                                shadowElevation = 4.dp,
                                color = AppColors.primary,
                                onClick = {
                                  showDialog.value = true
                                  hasInteractedWithDiscussion.value = true // Mark as interacted
                                }) {
                                  Row(
                                      modifier =
                                          Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                              .fillMaxWidth(),
                                      verticalAlignment = Alignment.CenterVertically) {
                                        // Avatar with unread badge (larger)
                                        Box(modifier = Modifier.padding(end = 16.dp)) {
                                          androidx.compose.material3.Surface(
                                              shape = CircleShape,
                                              color = AppColors.primary,
                                              modifier = Modifier.size(64.dp),
                                              tonalElevation = 0.dp) {
                                                Image(
                                                    painter =
                                                        painterResource(
                                                            id = R.drawable.logo_discussion),
                                                    contentDescription = "Avatar",
                                                    modifier = Modifier.padding(8.dp))
                                              }
                                          // Unread badge using Material3 Badge
                                          Badge(
                                              containerColor = AppColors.focus,
                                              contentColor = Color.White,
                                              modifier = Modifier.align(Alignment.TopEnd)) {
                                                Text(
                                                    "2",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold)
                                              }
                                        }
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
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                          Text(text = "21:01", fontSize = 9.sp, color = Color.Gray)
                                          Spacer(modifier = Modifier.height(4.dp))
                                          // "Tap to explore" hint with icon
                                          Row(
                                              verticalAlignment = Alignment.CenterVertically,
                                              horizontalArrangement = Arrangement.End) {
                                                Text(
                                                    text = "Tap",
                                                    fontSize = 10.sp,
                                                    color = AppColors.focus,
                                                    fontWeight = FontWeight.Medium)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = "Tap to explore",
                                                    tint = AppColors.focus,
                                                    modifier = Modifier.size(12.dp))
                                              }
                                        }
                                      }
                                }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Show prompt if user hasn't interacted yet
                            if (!hasInteractedWithDiscussion.value) {
                              Text(
                                  text = "⬆️ Tap the discussion above to continue",
                                  fontSize = 14.sp,
                                  color = AppColors.focus,
                                  fontWeight = FontWeight.Bold,
                                  modifier =
                                      Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp))
                            } else {
                              Text(
                                  text = "Jump into the conversation and never miss a meetup!",
                                  fontSize = 15.sp,
                                  color = AppColors.textIconsFade,
                                  modifier =
                                      Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp))
                            }

                            // Full-screen dialog with animation
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showDialog.value,
                                enter =
                                    androidx.compose.animation.fadeIn() +
                                        androidx.compose.animation.expandIn(),
                                exit =
                                    androidx.compose.animation.fadeOut() +
                                        androidx.compose.animation.shrinkOut()) {
                                  // Use Material3 AlertDialog for full-screen overlay
                                  androidx.compose.material3.AlertDialog(
                                      onDismissRequest = { showDialog.value = false },
                                      modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
                                      containerColor = AppColors.secondary,
                                      tonalElevation = 0.dp,
                                      shape = MaterialTheme.shapes.large,
                                      title = null,
                                      icon = null,
                                      text = {
                                        // Main dialog content column
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                              // Row with close button and title
                                              Row(
                                                  modifier =
                                                      Modifier.fillMaxWidth()
                                                          .padding(bottom = 8.dp),
                                                  verticalAlignment = Alignment.CenterVertically) {
                                                    // Close button at far left
                                                    IconButton(
                                                        onClick = { showDialog.value = false }) {
                                                          Icon(
                                                              imageVector = Icons.Default.Close,
                                                              contentDescription = "Close",
                                                              tint = Color.Black)
                                                        }

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    // Title next to close button
                                                    Text(
                                                        "Session Creation",
                                                        fontSize = 20.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color.Black,
                                                        modifier = Modifier.weight(1f))
                                                  }

                                              // Session creation section as a Column
                                              Column(
                                                  modifier =
                                                      Modifier.fillMaxWidth()
                                                          .clickable { /* TODO: session creation action */}
                                                          .padding(vertical = 4.dp),
                                                  horizontalAlignment =
                                                      Alignment.CenterHorizontally) {
                                                    Text(
                                                        "Use the button next to the discussion's name to create or join a session",
                                                        fontSize = 12.sp,
                                                        color = Color.DarkGray,
                                                        textAlign =
                                                            androidx.compose.ui.text.style.TextAlign
                                                                .Center,
                                                        modifier =
                                                            Modifier.padding(
                                                                    top = 2.dp, bottom = 4.dp)
                                                                .align(
                                                                    Alignment.CenterHorizontally))
                                                  }

                                              Surface(
                                                  color = AppColors.secondary,
                                                  shape = RoundedCornerShape(16.dp),
                                                  modifier =
                                                      Modifier.fillMaxWidth()
                                                          .padding(bottom = 8.dp)) {
                                                    Column {
                                                      Row(
                                                          verticalAlignment =
                                                              Alignment.CenterVertically,
                                                          modifier =
                                                              Modifier.padding(
                                                                      horizontal = 12.dp,
                                                                      vertical = 8.dp)
                                                                  .fillMaxWidth()) {
                                                            // Avatar
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = AppColors.primary,
                                                                modifier = Modifier.size(48.dp)) {
                                                                  Image(
                                                                      painter =
                                                                          painterResource(
                                                                              id =
                                                                                  R.drawable
                                                                                      .google_logo),
                                                                      contentDescription =
                                                                          "Discussion Avatar",
                                                                      modifier =
                                                                          Modifier.padding(8.dp))
                                                                }
                                                            Spacer(Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                              Text(
                                                                  "Board Game Night",
                                                                  fontWeight = FontWeight.SemiBold,
                                                                  fontSize = 14.sp,
                                                                  color = AppColors.textIcons)
                                                              Text(
                                                                  "5 new messages",
                                                                  fontSize = 10.sp,
                                                                  color = AppColors.textIconsFade)
                                                            }
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Icon(
                                                                imageVector =
                                                                    Icons.Default.LibraryAdd,
                                                                contentDescription = "Session",
                                                                tint = AppColors.textIcons)
                                                          }
                                                      HorizontalDivider(
                                                          modifier =
                                                              Modifier.fillMaxWidth()
                                                                  .padding(
                                                                      horizontal = 30.dp,
                                                                      vertical = 4.dp))
                                                    }
                                                  }

                                              // Fake chat bubble
                                              ChatBubble(
                                                  message =
                                                      Message(
                                                          uid = "1",
                                                          senderId = "Alex",
                                                          content =
                                                              "Hey! Are we playing Catan tonight?",
                                                          createdAt =
                                                              com.google.firebase.Timestamp.now()),
                                                  isMine = false,
                                                  senderName = "Alex")
                                              Spacer(Modifier.height(8.dp))

                                              // Fake poll bubble
                                              PollBubble(
                                                  msgIndex = 0,
                                                  poll =
                                                      Poll(
                                                          question = "Which game should we play?",
                                                          options =
                                                              listOf(
                                                                  "Catan",
                                                                  "Wingspan",
                                                                  "Terraforming Mars"),
                                                          allowMultipleVotes = true,
                                                          votes = votes),
                                                  authorName = "Dany",
                                                  currentUserId = "currentUser",
                                                  createdAt = Date(),
                                                  onVote = { x, _ -> x + 1 } // No-op for preview
                                                  )

                                              // Move the "Tap on any discussion..." text to the top
                                              Text(
                                                  "Tap on any discussion to see full details and join in!",
                                                  fontSize = 14.sp,
                                                  color = Color.DarkGray,
                                                  modifier =
                                                      Modifier.padding(bottom = 12.dp)
                                                          .fillMaxWidth())
                                            }
                                      },
                                      confirmButton = {},
                                      dismissButton = {})
                                }
                          }
                    } else {
                      Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          verticalArrangement = Arrangement.Center,
                          modifier = Modifier.fillMaxSize()) {
                            Image(
                                painter = painterResource(id = pages[page].image),
                                contentDescription = null,
                                modifier = Modifier.size(400.dp).padding(bottom = 24.dp))
                            Text(
                                text = pages[page].title,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp))
                            Text(
                                text = pages[page].description,
                                fontSize = 16.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 24.dp))
                          }
                    }
                  }

              Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween) {
                    // Placeholder for left arrow (back) in layout (top back arrow handles actual
                    // navigation)
                    Box(modifier = Modifier.size(42.dp))

                    // Dots centered
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val targetSize = if (isSelected) 12.dp else 8.dp
                        val animatedSize by animateFloatAsState(targetValue = targetSize.value)
                        Box(
                            modifier =
                                Modifier.padding(horizontal = 3.dp)
                                    .size(animatedSize.dp)
                                    .background(
                                        color = if (isSelected) Color.DarkGray else Color.LightGray,
                                        shape = CircleShape))
                      }
                    }

                    // Forward arrow (next page), hidden on last page
                    if (pagerState.currentPage < pages.lastIndex) {
                      // On first page, only enable next button if user has interacted
                      val isEnabled =
                          pagerState.currentPage != 0 || hasInteractedWithDiscussion.value

                      Box(
                          modifier =
                              Modifier.size(42.dp)
                                  .background(
                                      color = if (isEnabled) AppColors.primary else Color.LightGray,
                                      shape = CircleShape)
                                  .clickable(enabled = isEnabled) {
                                    scope.launch {
                                      pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                  },
                          contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next",
                                tint = if (isEnabled) AppColors.textIcons else Color.Gray)
                          }
                    } else {
                      // Invisible placeholder to keep layout consistent
                      Box(modifier = Modifier.size(42.dp))
                    }
                  }
            }
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
              modifier = Modifier.size(300.dp) // adjust size for preview
              )
          Text(sample.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
          Text(sample.description, color = Color.Gray)
        }
  }
}

@Composable
@Preview(showBackground = true)
fun OnBoardingScreenPreview() {
  AppTheme {
    // Add all onboarding pages here (image, title, description)
    val pages =
        listOf(
            OnBoardPage(
                image = R.drawable.logo_discussion, // new Meeple image
                title = "Welcome",
                description = "Discover events and meet new people."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_camera,
                title = "Create",
                description = "Host your own gatherings easily."),
            OnBoardPage(
                image = android.R.drawable.ic_menu_compass,
                title = "Explore",
                description = "Find activities near you."))

    OnBoardingScreen(pages = pages, onSkip = {}, onFinished = {})
  }
}
// Fake votes map for onboarding poll preview
public val votes: Map<String, List<Int>> = mapOf("1" to listOf(0), "2" to emptyList())
