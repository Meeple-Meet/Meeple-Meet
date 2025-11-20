package com.github.meeplemeet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.MessagingColors

@Composable
fun GameDetailsCard(game: Game, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    Box(modifier = Modifier.background(color = AppColors.primary)) {
        Column(modifier = modifier.fillMaxWidth()) {
            Image(
                painter = rememberAsyncImagePainter(game.imageURL),
                contentDescription = game.name,
                modifier =
                    Modifier.fillMaxWidth().height(180.dp).padding(0.dp)
                        .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    IconButton(
                        onClick = { onClose() },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(0.7F).align(Alignment.CenterHorizontally),
                    thickness = 1.dp,
                    color = AppColors.divider
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoChip(
                        text = "${game.minPlayers}-${game.maxPlayers} Players",
                        Icons.Default.People
                    )
                    game.averagePlayTime?.let {
                        InfoChip(
                            "$it min",
                            iconVector = Icons.Default.AccessTime
                        )
                    }
                    game.minAge?.let { InfoChip("Age: $it+") }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (game.genres.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.genres.forEach { genre ->
                            AssistChip(
                                colors =
                                    AssistChipDefaults.assistChipColors(
                                        containerColor = MessagingColors.redditBlueBg,
                                        labelColor = MessagingColors.redditBlue,
                                    ),
                                border = null,
                                onClick = {},
                                label = { Text(text = "#$genre") })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GameDescription(game.description)
            }
        }
    }
}

@Composable
fun GameDescription(description: String) {
  var expanded by remember { mutableStateOf(false) }
  var textHeight by remember { mutableStateOf(0) }
  val collapsedHeight = 70.dp

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Overview:",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(modifier = Modifier.height(4.dp))

    Box(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = if (expanded) Dp.Infinity else collapsedHeight)
                .clipToBounds()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)) {
          Text(
              text = description,
              style = MaterialTheme.typography.bodyMedium,
              modifier =
                  Modifier.onGloballyPositioned { coords -> textHeight = coords.size.height })
        }

    // If text is taller than collapsedHeight, show Read more button
    if (textHeight > with(LocalDensity.current) { collapsedHeight.toPx() } || expanded) {
      TextButton(
          onClick = { expanded = !expanded },
          colors = ButtonDefaults.textButtonColors(contentColor = AppColors.textIcons),
          modifier = Modifier.padding(start = 16.dp)) {
            Text(if (expanded) "Read less" else "Read more…")
          }
    }
  }
}


@Composable
fun InfoChip(text: String, iconVector: ImageVector? = null) {
  Surface(
      shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
      shadowElevation = Dimensions.Elevation.low,
      color = AppColors.secondary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
              if (iconVector != null)
                  Icon(
                      imageVector = iconVector,
                      contentDescription = null,
                      modifier = Modifier.padding(start = 6.dp))
              Text(
                  text = text,
                  modifier =
                      if (iconVector != null)
                          Modifier.padding(vertical = 12.dp).padding(start = 7.dp, end = 13.dp)
                      else Modifier.padding(vertical = 12.dp, horizontal = 13.dp),
                  style = MaterialTheme.typography.labelLarge,
                  fontSize = MaterialTheme.typography.titleMedium.fontSize,
              )
            }
      }
}
