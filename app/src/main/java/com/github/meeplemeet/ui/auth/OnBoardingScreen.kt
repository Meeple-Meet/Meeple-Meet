package com.github.meeplemeet.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnBoardPage(
    val image: Int,
    val title: String,
    val description: String
)

@Composable
fun OnBoardingScreen(
    pages: List<OnBoardPage>,
    onSkip: () -> Unit,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = pages[page].image),
                        contentDescription = null,
                        modifier = Modifier
                            .size(240.dp)
                            .padding(bottom = 24.dp)
                    )
                    Text(
                        text = pages[page].title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = pages[page].description,
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Skip",
                    color = Color.Gray,
                    modifier = Modifier.clickable { onSkip() }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 12.dp else 8.dp)
                                .background(
                                    color = if (isSelected) Color.DarkGray else Color.LightGray,
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }

                Text(
                    text = "→",
                    fontSize = 28.sp,
                    modifier = Modifier.clickable {
                        scope.launch {
                            if (pagerState.currentPage == pages.lastIndex) {
                                onFinished()
                            } else {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun OnBoardPagePreview() {
    val sample = OnBoardPage(
        image = android.R.drawable.ic_menu_gallery,
        title = "Welcome",
        description = "Discover events and meet new people."
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Image(
            painter = painterResource(id = sample.image),
            contentDescription = null,
            modifier = Modifier.size(200.dp)
        )
        Text(sample.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(sample.description, color = Color.Gray)
    }
}

@Composable
@Preview(showBackground = true)
fun OnBoardingScreenPreview() {
    val pages = listOf(
        OnBoardPage(
            image = android.R.drawable.ic_menu_gallery,
            title = "Welcome",
            description = "Discover events and meet new people."
        ),
        OnBoardPage(
            image = android.R.drawable.ic_menu_camera,
            title = "Create",
            description = "Host your own gatherings easily."
        ),
        OnBoardPage(
            image = android.R.drawable.ic_menu_compass,
            title = "Explore",
            description = "Find activities near you."
        )
    )

    OnBoardingScreen(
        pages = pages,
        onSkip = {},
        onFinished = {}
    )
}