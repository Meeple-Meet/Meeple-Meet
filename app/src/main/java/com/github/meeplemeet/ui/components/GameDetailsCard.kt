@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.github.meeplemeet.ui.components

import android.annotation.SuppressLint
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
private val CUSTOM_COLLAPSED_HEIGHT = 300.dp
private val COMPACT_LAYOUT_WIDTH_THRESHOLD = 360.dp
private val MAX_IMG_HEIGHT_COMPACT = 150.dp

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
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailsCard(game: Game, modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = Dimensions.Padding.tiny,
                  color = AppColors.secondary,
                  shape = RoundedCornerShape(Dimensions.CornerRadius.large))
              .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
              .background(AppColors.primary),
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
              BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactWidth = maxWidth < COMPACT_LAYOUT_WIDTH_THRESHOLD
                val imageMaxHeight =
                    if (compactWidth) MAX_IMG_HEIGHT_COMPACT else CUSTOM_IMAGE_HEIGHT

                Image(
                    painter = rememberAsyncImagePainter(game.imageURL),
                    contentDescription = game.name,
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = imageMaxHeight)
                            .clip(RoundedCornerShape(Dimensions.CornerRadius.large)))
              }

              Spacer(modifier = Modifier.height(Dimensions.Padding.extraLarge))

              HorizontalDivider(
                  modifier =
                      Modifier.fillMaxWidth(Dimensions.Fractions.topBarDivider)
                          .align(Alignment.CenterHorizontally),
                  thickness = Dimensions.DividerThickness.standard,
                  color = AppColors.divider)

              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

              BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val compactWidth = maxWidth < COMPACT_LAYOUT_WIDTH_THRESHOLD

                if (compactWidth) {
                  FlowRow(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small),
                      verticalArrangement = Arrangement.spacedBy(Dimensions.Spacing.small),
                  ) {
                    InfoChip(
                        text = "${game.minPlayers}-${game.maxPlayers} Players",
                        iconVector = Icons.Default.People,
                        compact = true)
                    game.averagePlayTime?.let {
                      InfoChip(
                          text = "$it min", iconVector = Icons.Default.AccessTime, compact = true)
                    }
                    game.minAge?.let { InfoChip(text = "Age: $it+", compact = true) }
                  }
                } else {
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceEvenly,
                      verticalAlignment = Alignment.CenterVertically) {
                        InfoChip(
                            "${game.minPlayers}-${game.maxPlayers} Players", Icons.Default.People)
                        game.averagePlayTime?.let { InfoChip("$it min", Icons.Default.AccessTime) }
                        game.minAge?.let { InfoChip("Age: $it+") }
                      }
                }
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
      Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis)
    } else {
      Box(
          modifier =
              Modifier.fillMaxWidth()
                  .heightIn(max = CUSTOM_COLLAPSED_HEIGHT)
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
 * @param compact When true, reduces padding and allows wrapping to avoid clipping on narrow widths.
 */
@Composable
fun InfoChip(text: String, iconVector: ImageVector? = null, compact: Boolean = false) {
  val vPad = if (compact) Dimensions.Padding.mediumSmall else Dimensions.Padding.large
  val startPad = if (compact) Dimensions.Padding.small else CUSTOM_PADDING_START
  val endPad = if (compact) Dimensions.Padding.mediumSmall else CUSTOM_PADDING_END
  val fontSize =
      if (compact) MaterialTheme.typography.labelLarge.fontSize
      else MaterialTheme.typography.titleMedium.fontSize

  Surface(
      shape = RoundedCornerShape(Dimensions.CornerRadius.medium),
      shadowElevation = Dimensions.Elevation.low,
      color = AppColors.secondary) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
              if (iconVector != null) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Dimensions.Padding.mediumSmall))
              }

              Text(
                  text = text,
                  modifier =
                      if (iconVector != null)
                          Modifier.padding(vertical = vPad).padding(start = startPad, end = endPad)
                      else Modifier.padding(vertical = vPad, horizontal = endPad),
                  style = MaterialTheme.typography.labelLarge,
                  fontSize = fontSize,
                  maxLines = if (compact) 2 else 1,
                  overflow = if (compact) TextOverflow.Clip else TextOverflow.Ellipsis)
            }
      }
}
