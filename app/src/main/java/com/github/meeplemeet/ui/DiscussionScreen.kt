package com.github.meeplemeet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        // Top bar with placeholder avatar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // TODO Placeholder discussion picture
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
                IconButton(onClick = { /* TODO search later */ }) {
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
            items(messages) { message ->
                val isMine = message.senderId == currentUser.uid
                val sender = if (!isMine) userCache[message.senderId]?.name ?: "Unknown" else  "You"
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
            IconButton(
                onClick = {/*TODO ATTACH FILE*/ }) {
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
            // The bubble with shadow
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 4.dp, // how “deep” the shadow is
                        shape = RoundedCornerShape(12.dp),
                        clip = false // allows shadow to appear outside bounds
                    )
                    .background(
                        color = Color(0xFFe0e0e0),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(10.dp)
                    .widthIn(max = 250.dp) // optional max width
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

            // Timestamp aligned to bubble end
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
