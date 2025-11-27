package com.github.meeplemeet.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.rememberAsyncImagePainter
import com.github.meeplemeet.model.sessions.Session
import com.github.meeplemeet.model.sessions.SessionOverviewViewModel
import com.github.meeplemeet.ui.sessions.LABEL_UNKNOWN_GAME
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.Dimensions
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Displays a detailed card for a session, including title, game, participants, location, date and
 * an image.
 *
 * @param session The session to display.
 * @param viewModel Used to resolve game and participant names.
 * @param modifier Optional modifier for the card.
 * @param onClose Called when the close button is pressed.
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

  val names = remember { mutableStateListOf<String>() }
  LaunchedEffect(session.participants) {
    names.clear()
    session.participants.forEach { id ->
      if (id.isBlank()) names += "Unknown"
      else viewModel.getOtherAccount(id) { acc -> names += acc.name }
    }
  }

  val gameName by
      produceState(key1 = session.gameId, initialValue = session.gameId) {
        val resolved =
            if (session.gameId == LABEL_UNKNOWN_GAME) null
            else viewModel.getGameNameByGameId(session.gameId)
        value = resolved ?: "No game selected"
      }

  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .border(
                  width = Dimensions.Padding.tiny,
                  color = AppColors.secondary,
                  shape = RoundedCornerShape(Dimensions.CornerRadius.large))
              .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
              .background(AppColors.primary)
              .clickable(
                  interactionSource =
                      remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                      },
                  indication = null,
                  onClick = {}),
      contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier.widthIn(max = Dimensions.ContainerSize.maxListHeight)
                    .padding(Dimensions.Padding.extraLarge)) {
              SessionHeader(
                  session = session,
                  gameName = gameName,
                  names = names,
                  date = date,
                  onClose = onClose)

              Spacer(modifier = Modifier.height(Dimensions.Spacing.medium))

              SessionImage(
                  session = session, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
      }
}

/**
 * Header section containing title, game info, participants, location/date, and a close button.
 *
 * @param session The session being displayed.
 * @param gameName Resolved game name.
 * @param names List of participant names.
 * @param date Formatted session date.
 * @param onClose Close button callback.
 */
@Composable
private fun SessionHeader(
    session: Session,
    gameName: String,
    names: List<String>,
    date: String,
    onClose: () -> Unit
) {
  Box(modifier = Modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier.align(Alignment.CenterStart).padding(end = Dimensions.Padding.huge)) {
          TitleAndGameInfo(session = session, gameName = gameName)
          Spacer(modifier = Modifier.height(Dimensions.Spacing.small))
          ParticipantsInfo(names = names, total = session.participants.size)
          Spacer(modifier = Modifier.height(Dimensions.Spacing.small))
          LocationAndDateInfo(location = session.location.name, date = date)
        }

    IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
      Icon(Icons.Default.Close, contentDescription = "Close")
    }
  }
}

/**
 * Displays the session title and resolved game name.
 *
 * @param session The session containing the title.
 * @param gameName Name of the associated game.
 */
@Composable
private fun TitleAndGameInfo(session: Session, gameName: String) {
  Text(
      text = session.name,
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis)

  Spacer(modifier = Modifier.height(Dimensions.Spacing.small))

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
}

/**
 * Displays a summary of participant names.
 *
 * @param names List of resolved participant names.
 * @param total Total expected participants.
 */
@Composable
private fun ParticipantsInfo(names: List<String>, total: Int) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
        imageVector = Icons.Default.People,
        contentDescription = null,
        modifier = Modifier.size(Dimensions.IconSize.medium),
        tint = AppColors.textIconsFade)
    Spacer(modifier = Modifier.width(Dimensions.Spacing.small))

    when {
      names.size < total -> Text("Loading…", color = AppColors.textIconsFade)
      names.isEmpty() -> Text("No participants", color = AppColors.textIconsFade)
      names.size <= 2 -> Text(names.joinToString(", "), color = AppColors.textIconsFade)
      else -> {
        val firstTwo = names.take(2).joinToString(", ")
        val remaining = names.size - 2

        Text(
            text = firstTwo,
            color = AppColors.textIconsFade,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false))
        Text(
            text = " and $remaining more participant${if (remaining > 1) "s" else ""}",
            color = AppColors.textIconsFade)
      }
    }
  }
}

/**
 * Displays the session location and date.
 *
 * @param location Session location label.
 * @param date Formatted date string.
 */
@Composable
private fun LocationAndDateInfo(location: String, date: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (location.isNotBlank()) {
      Icon(
          imageVector = Icons.Default.Place,
          contentDescription = null,
          modifier = Modifier.size(Dimensions.IconSize.medium),
          tint = AppColors.textIconsFade)
      Spacer(modifier = Modifier.width(Dimensions.Spacing.small))
      Text(
          text = location,
          color = AppColors.textIconsFade,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false))
      Text(text = " • $date", color = AppColors.textIconsFade)
    } else {
      Text(text = date, color = AppColors.textIconsFade)
    }
  }
}

/**
 * Displays the session image.
 *
 * @param session Source of the image label/description.
 */
@Composable
private fun SessionImage(session: Session, modifier: Modifier = Modifier) {
  val imageUrl = session.photoUrl?.takeIf { it.isNotBlank() }

  if (imageUrl != null) {
    Image(
        painter = rememberAsyncImagePainter(imageUrl),
        contentDescription = session.name,
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = Dimensions.ContainerSize.bottomSheetHeight)
                .clip(RoundedCornerShape(Dimensions.CornerRadius.large)))
  } else {
    Box(
        modifier =
            modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(Dimensions.Fractions.topBarDivider)
                .clip(RoundedCornerShape(Dimensions.CornerRadius.large))
                .background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center) {
          Text(
              text = "No Image Uploaded",
              color = AppColors.textIconsFade,
              style = MaterialTheme.typography.bodyLarge)
        }
  }
}
