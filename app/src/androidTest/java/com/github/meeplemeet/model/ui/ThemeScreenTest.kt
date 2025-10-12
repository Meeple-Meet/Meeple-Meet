package com.github.meeplemeet.model.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.R
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.Elevation
import com.github.meeplemeet.ui.theme.ThemeMode

@Composable
fun ThemeTestScreen(themeMode: ThemeMode = ThemeMode.SYSTEM_DEFAULT) {
  val resolvedMode =
      when (themeMode) {
        ThemeMode.DARK -> ThemeMode.DARK
        ThemeMode.LIGHT -> ThemeMode.LIGHT
        else -> if (isSystemInDarkTheme()) ThemeMode.DARK else ThemeMode.LIGHT
      }

  Box(Modifier.testTag("themeMode_${resolvedMode.name}")) {}

  AppTheme(themeMode = resolvedMode) {
    Surface(color = AppColors.primary, modifier = Modifier.fillMaxSize()) {
      Column(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(24.dp)) {
            ButtonsSection()
            PaletteSection()
            TypographySection()
            SessionCardSection()
            MessagesSection()
            MessageInputBar()
            BottomNavBar()
            WelcomeMessage()
          }
    }
  }
}

@Composable
private fun ButtonsSection() {
  Column {
    Text("Buttons", style = MaterialTheme.typography.bodyMedium, color = AppColors.textIcons)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Button(
          onClick = {},
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = AppColors.neutral, contentColor = AppColors.textIcons)) {
            Text("Label")
          }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("FAB", color = AppColors.textIconsFade, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        FloatingActionButton(
            onClick = {}, containerColor = AppColors.neutral, contentColor = AppColors.textIcons) {
              Icon(Icons.Default.Add, contentDescription = "FAB")
            }
      }
    }
  }
}

@Composable
private fun PaletteSection() {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
    val colors =
        listOf(
            AppColors.neutral,
            AppColors.affirmative,
            AppColors.negative,
            AppColors.focus,
            AppColors.secondary,
            AppColors.divider,
            Color.Black,
            Color.White)
    colors.forEach { color ->
      Box(modifier = Modifier.size(40.dp).background(color, shape = RoundedCornerShape(4.dp)))
    }
  }
}

@Composable
private fun TypographySection() {
  Column {
    Text(
        "Text-Font-Size: 18",
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.textIcons)
    Text(
        "Middle-Font-Size: 15",
        style = MaterialTheme.typography.bodySmall,
        color = AppColors.textIconsFade)
    Text(
        "SubText-Font-Size: 13",
        style = MaterialTheme.typography.labelSmall,
        color = AppColors.textIconsFade)
  }
}

@Composable
private fun SessionCardSection() {
  Card(
      colors = CardDefaults.cardColors(containerColor = AppColors.divider),
      shape = MaterialTheme.shapes.medium,
      elevation = CardDefaults.cardElevation(defaultElevation = Elevation.raised)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
              Column {
                Text(
                    "Session name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.textIcons)
                Text(
                    "0/10",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textIconsFade)
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                    "dd/mm/yy",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textIconsFade)
                Text(
                    "Host name??",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.neutral)
                Text(
                    "location",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textIconsFade)
              }
            }
      }
}

@Composable
private fun MessagesSection() {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Image(
          painter = painterResource(id = R.drawable.app_logo),
          contentDescription = "Avatar",
          modifier = Modifier.size(36.dp).clip(CircleShape))

      Spacer(Modifier.width(8.dp))

      Column(
          modifier =
              Modifier.weight(1f)
                  .shadow(Elevation.subtle, MaterialTheme.shapes.medium)
                  .background(AppColors.divider, MaterialTheme.shapes.medium)
                  .padding(10.dp)) {
            Text(
                "user",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textIconsFade)
            Text("Message", style = MaterialTheme.typography.bodySmall, color = AppColors.textIcons)
            Spacer(Modifier.height(4.dp))
            Text(
                "Sent at 17:55",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textIconsFade)
          }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top) {
          Column(
              modifier =
                  Modifier.widthIn(max = 250.dp)
                      .shadow(Elevation.subtle, MaterialTheme.shapes.medium)
                      .background(AppColors.divider, MaterialTheme.shapes.medium)
                      .padding(10.dp)) {
                Text(
                    "user",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textIconsFade)
                Text(
                    "Message",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.textIcons)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sent at 17:55",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textIconsFade)
              }

          Spacer(Modifier.width(8.dp))

          Image(
              painter = painterResource(id = R.drawable.app_logo),
              contentDescription = "Avatar",
              modifier = Modifier.size(36.dp).clip(CircleShape))
        }
  }
}

@Composable
private fun MessageInputBar() {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .shadow(Elevation.subtle, MaterialTheme.shapes.medium)
              .background(AppColors.secondary, MaterialTheme.shapes.medium)
              .padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
              Icons.Default.AttachFile,
              contentDescription = "Attach",
              tint = AppColors.textIconsFade)
          Spacer(Modifier.width(8.dp))
          Text(
              "Type something...",
              color = AppColors.textIconsFade,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f))
          Icon(
              Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send",
              tint = AppColors.textIcons)
        }
      }
}

@Composable
private fun BottomNavBar() {
  Card(
      modifier = Modifier.fillMaxWidth().shadow(Elevation.subtle, MaterialTheme.shapes.large),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = AppColors.divider)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
              val tabs =
                  listOf(
                      "Sessions" to Icons.Filled.Groups,
                      "Discussions" to Icons.Filled.ChatBubble,
                      "Discover" to Icons.Filled.Public,
                      "Account" to Icons.Filled.AccountCircle)
              val selectedTab = "Discussions"

              tabs.forEach { (label, icon) ->
                val selected = label == selectedTab

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Box(
                      modifier =
                          Modifier.height(36.dp)
                              .width(50.dp)
                              .background(
                                  color = if (selected) AppColors.focus else Color.Transparent,
                                  shape = RoundedCornerShape(50)),
                      contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = label, tint = AppColors.textIcons)
                      }

                  Spacer(Modifier.height(4.dp))

                  Text(
                      label,
                      color = if (selected) AppColors.textIcons else AppColors.textIconsFade,
                      style = MaterialTheme.typography.labelSmall)
                }
              }
            }
      }
}

@Composable
private fun WelcomeMessage() {
  Text("MeepleMeet!", style = MaterialTheme.typography.displayLarge, color = AppColors.textIcons)
}
