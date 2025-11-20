package com.github.meeplemeet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.shared.game.Game
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import com.github.meeplemeet.ui.theme.MessagingColors

private val CUSTOM_PADDING_START = 7.dp
private val CUSTOM_PADDING_END = 13.dp
private val CUSTOM_IMAGE_HEIGHT = 180.dp

/**
 * A detailed card displaying information about a [Game].
 *
 * Shows the game title, an image, info chips for players, time, and age, genres, and a description
 * that can be expanded/collapsed.
 *
 * @param game The [Game] object containing all information to display.
 * @param modifier Optional [Modifier] for the card container.
 * @param onClose Lambda invoked when the close button is pressed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailsCard(game: Game, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {

  Box(
      modifier =
          modifier
              .fillMaxWidth() // optional width limitation
              .border(
                  width = Dimensions.Padding.tiny,
                  color = AppColors.secondary, // border color
                  shape = RoundedCornerShape(Dimensions.CornerRadius.large))
              .clip(
                  RoundedCornerShape(
                      Dimensions.CornerRadius.large)) // rounded corners for the whole card
              .background(AppColors.primary), // card background
      contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier.widthIn(max = Dimensions.ContainerSize.maxListHeight)
                    .padding(Dimensions.Padding.extraLarge)) {

              // TITLE + CLOSE
              Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center))

                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterEnd)) {
                  Icon(Icons.Default.Close, contentDescription = "Close")
                }
              }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))
              // IMAGE
              Image(
                  painter = rememberAsyncImagePainter(game.imageURL),
                  contentDescription = game.name,
                  contentScale = ContentScale.Fit, // preserves natural aspect ratio
                  modifier =
                      Modifier.fillMaxWidth() // expand to available width
                          .heightIn(max = CUSTOM_IMAGE_HEIGHT) // limit height
                          .clip(RoundedCornerShape(Dimensions.CornerRadius.large)))

              Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))

              HorizontalDivider(
                  modifier =
                      Modifier.fillMaxWidth(Dimensions.Fractions.topBarDivider)
                          .align(Alignment.CenterHorizontally),
                  thickness = Dimensions.DividerThickness.standard,
                  color = AppColors.divider)

              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

              // INFO CHIPS
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly,
                  verticalAlignment = Alignment.CenterVertically) {
                    InfoChip("${game.minPlayers}-${game.maxPlayers} Players", Icons.Default.People)
                    game.averagePlayTime?.let { InfoChip("$it min", Icons.Default.AccessTime) }
                    game.minAge?.let { InfoChip("Age: $it+") }
                  }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.large))

              // GENRES
              if (game.genres.isNotEmpty()) {
                FlowRow(
                    maxLines = 2,
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.medium),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                      game.genres.forEach { genre ->
                        AssistChip(
                            colors =
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MessagingColors.redditBlueBg,
                                    labelColor = MessagingColors.redditBlue,
                                ),
                            border = null,
                            onClick = {},
                            label = { Text("#$genre") })
                      }
                    }
              }

              Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))

              // DESCRIPTION
              GameDescription(game.description)
            }
      }
}

/**
 * A composable showing a collapsible/expandable description text.
 *
 * @param description The text to display in the description box.
 */
@Composable
fun GameDescription(description: String) {
  var expanded by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Overview:",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))

    Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

    if (!expanded) {
      // COLLAPSED VERSION (ellipsis)
      Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis)
    } else {
      // EXPANDED VERSION (scrollable)
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .heightIn(max = 300.dp) // You can adjust height
                  .verticalScroll(rememberScrollState())) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
    }

    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.align(Alignment.End),
        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.textIcons)) {
          Text(if (expanded) "Read less" else "Read more…")
        }
  }
}

/**
 * A small chip displaying a short text with an optional leading icon.
 *
 * @param text The text to display inside the chip.
 * @param iconVector Optional leading icon to show before the text.
 */
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
                      modifier = Modifier.padding(start = Dimensions.Padding.mediumSmall))

              Text(
                  text = text,
                  modifier =
                      if (iconVector != null)
                          Modifier.padding(vertical = Dimensions.Padding.large)
                              .padding(start = CUSTOM_PADDING_START, end = CUSTOM_PADDING_END)
                      else
                          Modifier.padding(
                              vertical = Dimensions.Padding.large, horizontal = CUSTOM_PADDING_END),
                  style = MaterialTheme.typography.labelLarge,
                  fontSize = MaterialTheme.typography.titleMedium.fontSize,
              )
            }
      }
}
