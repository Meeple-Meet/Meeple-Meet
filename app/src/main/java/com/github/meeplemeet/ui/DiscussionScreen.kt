package com.github.meeplemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.meeplemeet.model.structures.Account
import com.github.meeplemeet.model.structures.Message
import com.github.meeplemeet.model.viewmodels.FirestoreViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.text.format.DateFormat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import java.util.Calendar
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscussionScreen(
    viewModel: FirestoreViewModel,
    discussionId: String,
    currentUser: Account
) {
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }
    val listState = rememberLazyListState()
    var isSending by remember { mutableStateOf(false) }
    var discussionName by remember { mutableStateOf("Loading...") }
    val userCache = remember { mutableStateMapOf<String, Account>() }

    LaunchedEffect(discussionId) {
        viewModel.discussionFlow(discussionId).collectLatest { discussion ->
            discussion?.messages?.let { msgs ->
                messages.clear()
                messages.addAll(msgs)
                scope.launch {
                    listState.animateScrollToItem(messages.size)
                }
                msgs.forEach { msg ->
                    if (!userCache.containsKey(msg.senderId) && msg.senderId != currentUser.uid) {
                        try {
                            val account = viewModel.getAccount(msg.senderId)
                            userCache[msg.senderId] = account
                        } catch (_: Exception) { }
                    }
                }
                discussionName = discussion?.name ?: "Loading..."
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Gray, shape = CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = discussionName, style = MaterialTheme.typography.titleMedium)
                }
            },
            navigationIcon = {
                IconButton(onClick = { /* TODO GO BACK */ }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(messages) { index, message ->
                val isMine = message.senderId == currentUser.uid
                val sender = if (!isMine) userCache[message.senderId]?.name ?: "Unknown" else "You"

                val showDateHeader = shouldShowDateHeader(
                    current = message.createdAt.toDate(),
                    previous = messages.getOrNull(index - 1)?.createdAt?.toDate()
                )
                if (showDateHeader) {
                    DateSeparator(date = message.createdAt.toDate())
                }

                ChatBubble(message, isMine, sender)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .background(Color(0xFFF0F0F0), shape = CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
            }
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (messageText.isEmpty()) {
                        Text("Type something...", color = Color.Gray)
                    }
                    innerTextField()
                }
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank() && !isSending) {
                        scope.launch {
                            isSending = true
                            try {
                                viewModel.sendMessageToDiscussion(
                                    viewModel.getDiscussion(discussionId),
                                    currentUser,
                                    messageText
                                )
                                messageText = ""
                            } finally {
                                isSending = false
                            }
                        }
                    }
                },
                enabled = !isSending
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
fun ChatBubble(message: Message, isMine: Boolean, senderName: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.Gray, shape = CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false
                    )
                    .background(
                        color = Color(0xFFe0e0e0),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp)
                    .widthIn(max = 250.dp)
            ) {
                Column {
                    if (senderName != null) {
                        Text(
                            senderName,
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.Black)
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Black)
                    )
                }
            }

            Text(
                text = DateFormat.format("HH:mm", message.createdAt.toDate()).toString(),
                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF888888)),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .align(if (isMine) Alignment.End else Alignment.Start)
            )
        }

        if (isMine) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.Gray, shape = CircleShape)
            )
        }
    }
}

@Composable
fun DateSeparator(date: Date) {
    Box(
        modifier = Modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = formatDateBubble(date),
            color = Color.White,
            modifier = Modifier
                .background(Color(0xFF9E9E9E), shape = RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

fun formatDateBubble(date: Date): String {
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()
    cal.time = date

    return when {
        isSameDay(cal, today) -> "Today"
        isSameDay(cal, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
        else -> DateFormat.format("MMM dd, yyyy", date).toString()
    }
}

fun shouldShowDateHeader(current: Date, previous: Date?): Boolean {
    if (previous == null) return true
    val calCurrent = Calendar.getInstance().apply { time = current }
    val calPrev = Calendar.getInstance().apply { time = previous }
    return !(calCurrent.get(Calendar.YEAR) == calPrev.get(Calendar.YEAR)
            && calCurrent.get(Calendar.DAY_OF_YEAR) == calPrev.get(Calendar.DAY_OF_YEAR))
}

fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
