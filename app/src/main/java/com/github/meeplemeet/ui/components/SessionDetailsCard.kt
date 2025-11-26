package com.github.meeplemeet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.ui.sessions.LABEL_UNKNOWN_GAME
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import java.text.SimpleDateFormat
import java.util.Locale

private val CUSTOM_IMAGE_HEIGHT = 180.dp

/**
 * A detailed card displaying information about a [Session].
 *
 * Shows the session title, an image, info chips for location and date, and participants.
 *
 * @param session The [Session] object containing all information to display.
 * @param viewModel The [SessionOverviewViewModel] to resolve game and participant names.
 * @param modifier Optional [Modifier] for the card container.
 * @param onClose Lambda invoked when the close button is pressed.
 */
@Composable
fun SessionDetailsCard(
    session: Session,
    viewModel: SessionOverviewViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {}
) {
  val date =
      remember(session.date) {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(session.date.toDate())
      }

  /* ---------  resolve names via the callback  --------- */
  val names = remember { mutableStateListOf<String>() }

  LaunchedEffect(session.participants) {
    names.clear()
    session.participants.forEach { id ->
      if (id.isBlank()) {
        names += "Unknown"
      } else {
        viewModel.getOtherAccount(id) { acc ->
          names += acc.name // re-composition happens on each addition
        }
      }
    }
  }

  val gameName by
      produceState(
          key1 = session.gameId, initialValue = session.gameId // fallback: show id while loading
          ) {
            val name =
                if (session.gameId == LABEL_UNKNOWN_GAME) null
                else viewModel.getGameNameByGameId(session.gameId)
            value = name ?: "No game selected" // suspend call
      }

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
              .background(AppColors.primary) // card background
              .clickable(
                  interactionSource =
                      remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                      },
                  indication =
                      null) {}, // Consume clicks to prevent closing when clicking on the card
      contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier.widthIn(max = Dimensions.ContainerSize.maxListHeight)
                    .padding(Dimensions.Padding.extraLarge)) {

              // TITLE + CLOSE
              Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier.align(Alignment.CenterStart)
                            .padding(end = Dimensions.Padding.huge)) {
                      Text(
                          text = session.name,
                          style = MaterialTheme.typography.headlineMedium,
                          fontWeight = FontWeight.Bold,
                          maxLines = 2,
                          overflow = TextOverflow.Ellipsis)

                      Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

                      // Game with Dice Icon
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.IconSize.medium),
                            tint = AppColors.textIconsFade)
                        Spacer(modifier = Modifier.width(Dimensions.Spacing.small))
                        Text(
                            text = gameName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppColors.textIconsFade,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                      }

                      Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

                      // Participants with People Icon
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(Dimensions.IconSize.medium),
                                tint = AppColors.textIconsFade)
                            Spacer(modifier = Modifier.width(Dimensions.Spacing.small))

                            when {
                              names.size < session.participants.size -> {
                                Text(
                                    text = "Loading…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.textIconsFade)
                              }
                              names.isEmpty() -> {
                                Text(
                                    text = "No participants",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.textIconsFade)
                              }
                              names.size == 1 -> {
                                Text(
                                    text = names.first(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.textIconsFade,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                              }
                              names.size == 2 -> {
                                Text(
                                    text = names.joinToString(", "),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.textIconsFade,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                              }
                              else -> {
                                val firstTwo = names.take(2).joinToString(", ")
                                val remaining = names.size - 2

                                Text(
                                    text = firstTwo,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.textIconsFade,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                Text(
                                    text =
                                        " and $remaining more participant${if (remaining > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AppColors.textIconsFade,
                                    maxLines = 1)
                              }
                            }
                          }

                      Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

                      // Location and Date
                      Row(
                          verticalAlignment = Alignment.CenterVertically,
                          modifier = Modifier.fillMaxWidth()) {
                            if (session.location.name.isNotBlank()) {
                              Icon(
                                  imageVector = Icons.Default.Place,
                                  contentDescription = null,
                                  modifier = Modifier.size(Dimensions.IconSize.medium),
                                  tint = AppColors.textIconsFade)
                              Spacer(modifier = Modifier.width(Dimensions.Spacing.small))
                              Text(
                                  text = session.location.name,
                                  style = MaterialTheme.typography.bodyLarge,
                                  color = AppColors.textIconsFade,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis,
                                  modifier = Modifier.weight(1f, fill = false))
                              Text(
                                  text = " • $date",
                                  style = MaterialTheme.typography.bodyLarge,
                                  color = AppColors.textIconsFade,
                                  maxLines = 1)
                            } else {
                              Text(
                                  text = date,
                                  style = MaterialTheme.typography.bodyLarge,
                                  color = AppColors.textIconsFade)
                            }
                          }
                    }

                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                  Icon(Icons.Default.Close, contentDescription = "Close")
                }
              }

              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

              // IMAGE
              // TODO: Use real session image when available
              val imageUrl =
                  "https://npr.brightspotcdn.com/dims4/default/389845d/2147483647/strip/true/crop/4373x3279+0+0/resize/880x660!/quality/90/?url=http%3A%2F%2Fnpr-brightspot.s3.amazonaws.com%2Flegacy%2Fimages%2Fnews%2Fnpr%2F2020%2F07%2F887305543_2064699070.jpg"

              Image(
                  painter = rememberAsyncImagePainter(imageUrl),
                  contentDescription = session.name,
                  contentScale = ContentScale.Crop, // preserves natural aspect ratio
                  modifier =
                      Modifier.fillMaxWidth() // expand to available width
                          .heightIn(max = CUSTOM_IMAGE_HEIGHT) // limit height
                          .clip(RoundedCornerShape(Dimensions.CornerRadius.large)))
            }
      }
}
