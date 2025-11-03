package com.github.meeplemeet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.meeplemeet.model.auth.Account
import com.github.meeplemeet.model.sessions.Game
import com.github.meeplemeet.model.shared.Location
import com.github.meeplemeet.model.shops.OpeningHours
import com.github.meeplemeet.model.shops.Shop
import com.github.meeplemeet.model.shops.ShopViewModel
import com.github.meeplemeet.ui.components.TopBarWithDivider
import com.github.meeplemeet.ui.theme.AppColors
import com.github.meeplemeet.ui.theme.AppTheme
import com.github.meeplemeet.ui.theme.ThemeMode
import com.google.firebase.Timestamp
import java.text.DateFormatSymbols
import java.util.Calendar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast


object TestTags {
    // Contact section tags
    const val SHOP_PHONE_TEXT = "SHOP_PHONE_TEXT"
    const val SHOP_PHONE_BUTTON = "SHOP_PHONE_BUTTON"
    const val SHOP_EMAIL_TEXT = "SHOP_EMAIL_TEXT"
    const val SHOP_EMAIL_BUTTON = "SHOP_EMAIL_BUTTON"
    const val SHOP_ADDRESS_TEXT = "SHOP_ADDRESS_TEXT"
    const val SHOP_ADDRESS_BUTTON = "SHOP_ADDRESS_BUTTON"
    const val SHOP_WEBSITE_TEXT = "SHOP_WEBSITE_TEXT"
    const val SHOP_WEBSITE_BUTTON = "SHOP_WEBSITE_BUTTON"

    // Availability section tags
    const val SHOP_DAY_PREFIX = "SHOP_DAY_"

    // Game list tags
    const val SHOP_GAME_PREFIX = "SHOP_GAME_"
}

@Composable
fun ShopScreen(
    shopId: String,
    account: Account,
    viewModel: ShopViewModel,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {}
) {
    val shopState by viewModel.shop.collectAsStateWithLifecycle()
    LaunchedEffect(shopId) { viewModel.getShop(shopId) }

    Scaffold(
        topBar = {
            TopBarWithDivider(
                text = shopState?.name ?: "Shop",
                 onReturn = {onBack},
                trailingIcons = {
                    if (account != (shopState?.owner ?: false)) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        shopState?.let { shop ->
            ShopDetails(
                shop = shop,
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize()
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun ShopDetails(shop: Shop, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ContactSection(shop)
        HorizontalDivider(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 100.dp))
        AvailabilitySection(shop.openingHours)
        HorizontalDivider(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 100.dp))
        GameListSection(shop.gameCollection)
    }
}

// -------------------- CONTACT SECTION --------------------

@Composable
fun ContactSection(shop: Shop) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 25.dp)) {
        Text("Contact:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)

        ContactRow(Icons.Default.Phone, "- Phone: ${shop.phone}", TestTags.SHOP_PHONE_TEXT, TestTags.SHOP_PHONE_BUTTON)
        ContactRow(Icons.Default.Email, "- Email: ${shop.email}", TestTags.SHOP_EMAIL_TEXT, TestTags.SHOP_EMAIL_BUTTON)
        ContactRow(Icons.Default.Place, "- Address: ${shop.address.name}", TestTags.SHOP_ADDRESS_TEXT, TestTags.SHOP_ADDRESS_BUTTON)
        ContactRow(Icons.Default.Language, "- Website: ${shop.website}", TestTags.SHOP_WEBSITE_TEXT, TestTags.SHOP_WEBSITE_BUTTON)
    }
}

@Composable
fun ContactRow(icon: ImageVector, text: String, textTag: String, buttonTag: String) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text,
            style = LocalTextStyle.current.copy(textIndent = TextIndent(restLine = 8.sp)),
            modifier = Modifier
                .weight(1f)
                .testTag(textTag)
        )
        IconButton(
            onClick = {
                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            content = {
                Icon(icon, contentDescription = null, tint = AppColors.neutral)
            },
            modifier = Modifier
                .size(24.dp)
                .testTag(buttonTag)
        )
    }
}

// -------------------- AVAILABILITY SECTION --------------------

@Composable
fun AvailabilitySection(openingHours: List<OpeningHours>) {
    val daysOfWeek = remember { DateFormatSymbols().weekdays }
    val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 30.dp)) {
        Text("Availability:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)

        openingHours.forEach { entry ->
            val dayName = daysOfWeek.getOrNull(entry.day + 1) ?: "Unknown"
            val isToday = (entry.day + 1) == currentDay
            if (entry.hours.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${TestTags.SHOP_DAY_PREFIX}${entry.day}"),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(dayName, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                    Text("Closed", fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.testTag("${TestTags.SHOP_DAY_PREFIX}${entry.day}_HOURS"))
                }
            } else {
                entry.hours.forEachIndexed { idx, (start, end) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${TestTags.SHOP_DAY_PREFIX}${entry.day}${if (idx > 0) "_$idx" else ""}_HOURS"),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (idx == 0) {
                            Text(dayName, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f).testTag("${TestTags.SHOP_DAY_PREFIX}${entry.day}"))
                        } else {
                            Text("", modifier = Modifier.weight(1f)) // empty to align under day name
                        }
                        val timeText = if (start != null && end != null) "$start - $end" else "Closed"
                        Text(timeText, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

// -------------------- GAMES SECTION --------------------

@Composable
fun GameListSection(games: List<Pair<Game, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 30.dp)) {
        Text("Games:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 600.dp)
        ) {
            items(games) { (game, count) ->
                GameItem(game, count)
            }
        }
    }
}

@Composable
fun GameItem(game: Game, count: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${TestTags.SHOP_GAME_PREFIX}${game.name}"),
        colors = CardDefaults.cardColors(containerColor = AppColors.secondary
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // You can load image from network or placeholder if game.imageUrl exists
            Box(
                modifier = Modifier
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                BadgedBox(
                    modifier = Modifier,
                    badge = {
                        if (count > 0) {
                            Badge(modifier = Modifier
                                .offset(x = 8.dp, y = (-6).dp)) {
                                Text(count.toString())
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.VideogameAsset, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(game.name, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
// ---------- PREVIEWS ----------

val account = Account(
    uid = "owner1",
    handle = "hello",
    name = "shopowner",
    email = "")
val address = Location(
    latitude = 0.0,
    longitude = 0.0,
    name = "123 Meeple St, Boardgame City"
)

val dummyOpeningHours = listOf(
    OpeningHours(day = 0, hours = listOf(Pair("09:00", "18:00"), Pair("19:00", "21:00"))),
    OpeningHours(day = 1, hours = listOf(Pair("09:00", "18:00"))),
    OpeningHours(day = 2, hours = listOf(Pair("09:00", "18:00"))),
    OpeningHours(day = 3, hours = listOf(Pair("09:00", "18:00"))),
    OpeningHours(day = 4, hours = listOf(Pair("09:00", "18:00"))),
    OpeningHours(day = 5, hours = listOf(Pair("10:00", "16:00"))),
    OpeningHours(day = 6, hours = emptyList())
)

val dummyGame = Game(uid = "g1", name = "Catan", imageURL = "test", description = "this game is cool", minPlayers = 1, maxPlayers = 4, recommendedPlayers = null, averagePlayTime = null, genres = emptyList())
val dummyGames = listOf(
    Pair(
        Game(uid = "g1", name = "Catan", imageURL = "test", description = "this game is cool", minPlayers = 1, maxPlayers = 4, recommendedPlayers = null, averagePlayTime = null, genres = emptyList()),
        2
    ),
    Pair(
        Game(uid = "g2", name = "Carcassone", imageURL = "test", description = "this game is cool", minPlayers = 1, maxPlayers = 4, recommendedPlayers = null, averagePlayTime = null, genres = emptyList()),
        1
    ))

val dummyShop = Shop(
    id = "1",
    name = "Meeple Meet",
    owner = account,
    phone = "123-456-7890",
    email = "info@meeplehaven.com",
    address = address,
    website = "www.meeplemeet.com",
    openingHours = dummyOpeningHours,
    gameCollection = dummyGames
    )

@Preview(showBackground = true)
@Composable
fun PreviewShopScreen() {
    AppTheme(themeMode = ThemeMode.LIGHT){
        ShopDetails(shop = dummyShop)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewShopDetails() {
    AppTheme(themeMode = ThemeMode.LIGHT){
        ShopDetails(shop = dummyShop)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewContactSection() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ContactSection(shop = dummyShop)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAvailabilitySection() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AvailabilitySection(openingHours = dummyOpeningHours)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewGameListSection() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        GameListSection(games = dummyGames)
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewScreen() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ShopScreen(
            shopId = "1",
            account = account,
            viewModel = ShopViewModel(),
            onBack = {},
            onEdit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewGameItem() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        GameItem(game = dummyGame, count = 2)
    }
}
@Preview(showBackground = true)
@Composable
fun PreviewFullShopScreen() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Scaffold(
            topBar = {
                TopBarWithDivider(
                    text = dummyShop.name,
                    onReturn = {},
                    trailingIcons = {
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ContactSection(dummyShop)
                HorizontalDivider(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 100.dp))
                AvailabilitySection(dummyShop.openingHours)
                HorizontalDivider(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 100.dp))
                GameListSection(dummyShop.gameCollection)
            }
        }
    }
}