package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.DeliveryStatus
import com.example.data.model.FileStatus
import com.example.network.ConnectionState
import com.example.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.activeChatMessages.collectAsState()
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val peerName by viewModel.peerName.collectAsState()
    val peerAddress by viewModel.peerAddress.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.attachFile(it) }
    }

    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isPeerTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = peerName ?: "Secure Peer",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E7D32))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "SECURE HANDSHAKE ESTABLISHED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF44474E),
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            
                            // AES-256 Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFE8F5E9))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "AES-256",
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "AES-256",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF2E7D32),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                viewModel.disconnectPeer()
                                onNavigateBack()
                            },
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Disconnect & Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.clearChatHistory() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            
            // Connection Info Header Banner
            ConnectionStatusBar(connectionState = connectionState)

            // Message Stream Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "TODAY • DIRECT CONNECTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF74777F),
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    items(messages) { message ->
                        MessageBubble(message = message)
                    }

                    if (messages.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Zero data stored on external servers • Session-only storage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF74777F),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                
                // Typing Indicator Overlay at the bottom
                if (isPeerTyping) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Loop,
                                contentDescription = "Typing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Peer is typing...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Chat input Panel
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("attach_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File Payload",
                            tint = Color(0xFF44474E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = textInput,
                        onValueChange = {
                            textInput = it
                            viewModel.onUserTyping()
                        },
                        placeholder = { Text("Encrypted message...", color = Color(0xFF44474E)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("chat_input_textfield"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    viewModel.sendText(textInput)
                                    textInput = ""
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendText(textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_msg_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(connectionState: ConnectionState) {
    val phrase = when (connectionState) {
        ConnectionState.IDLE -> "P2P Sockets Idle"
        ConnectionState.CONNECTING -> "Initiating P2P TCP Channel..."
        ConnectionState.HANDSHAKING -> "Exchanging Cryptographic Certificates (ECDH)..."
        ConnectionState.SECURE_ACTIVE -> "Secure Session Configured - Encryption Enforced"
        ConnectionState.DISCONNECTED -> "Offline Peer Disconnected"
    }

    val color = when (connectionState) {
        ConnectionState.SECURE_ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        ConnectionState.CONNECTING, ConnectionState.HANDSHAKING -> Color(0xFFFFF3CD)
        else -> MaterialTheme.colorScheme.errorContainer
    }

    val textColor = when (connectionState) {
        ConnectionState.SECURE_ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        ConnectionState.CONNECTING, ConnectionState.HANDSHAKING -> Color(0xFF856404)
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(vertical = 6.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (connectionState) {
                    ConnectionState.SECURE_ACTIVE -> Icons.Default.EnhancedEncryption
                    ConnectionState.CONNECTING, ConnectionState.HANDSHAKING -> Icons.Default.SyncAlt
                    else -> Icons.Default.NoEncryption
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = phrase,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isOut = message.isOutgoing
    
    val bubbleShape = if (isOut) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(0.dp, 16.dp, 16.dp, 16.dp)
    }

    val alignment = if (isOut) Alignment.End else Alignment.Start

    val containerColor = if (isOut) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isOut) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val outlineModifier = if (isOut) {
        Modifier.widthIn(max = 290.dp)
    } else {
        Modifier
            .widthIn(max = 290.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, bubbleShape)
    }

    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedTime = sdf.format(Date(message.timestamp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        // Bubble Body Container
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = bubbleShape,
            tonalElevation = if (isOut) 0.dp else 1.dp,
            modifier = outlineModifier
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (message.isFile) {
                    // Custom Attachment Progress card
                    AttachmentMsgPayload(message = message, isOut = isOut)
                } else {
                    // Text
                    Text(
                        text = message.messageBody,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        color = contentColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                // Time + Integrity Status checks
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = contentColor.copy(alpha = 0.6f)
                    )
                    
                    if (isOut) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = when (message.deliveryStatus) {
                                DeliveryStatus.SENDING -> Icons.Default.HourglassEmpty
                                DeliveryStatus.SENT -> Icons.Default.Check
                                DeliveryStatus.DELIVERED -> Icons.Default.DoneAll
                                DeliveryStatus.FAILED -> Icons.Default.ErrorOutline
                            },
                            contentDescription = message.deliveryStatus.name,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentMsgPayload(message: ChatMessage, isOut: Boolean) {
    val fileIconColor = Color(0xFF311300)
    val fileIconBg = Color(0xFFFFDBCB)
    
    val isComplete = message.fileStatus == FileStatus.COMPLETED
    val progressPercent = (message.fileProgress * 100).toInt().coerceIn(0, 100)

    val textColor = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subTextColor = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else Color(0xFF44474E)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fileIconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        message.fileName?.endsWith(".pdf", ignoreCase = true) == true -> Icons.Default.PictureAsPdf
                        message.fileName?.endsWith(".png", ignoreCase = true) == true || message.fileName?.endsWith(".jpg", ignoreCase = true) == true || message.fileName?.endsWith(".jpeg", ignoreCase = true) == true -> Icons.Default.Image
                        message.fileName?.endsWith(".mp4", ignoreCase = true) == true -> Icons.Default.VideoFile
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = "Attachment Symbol",
                    tint = fileIconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.fileName ?: "Document.pdf",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
                Text(
                    text = if (isComplete) "P2P TRANSMISSION COMPLETED" else "${message.fileStatus.name} • $progressPercent%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = subTextColor,
                    letterSpacing = 0.4.sp
                )
            }
            
            if (!isComplete && message.fileStatus != FileStatus.FAILED) {
                Text(
                    text = "$progressPercent%",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        if (!isComplete && message.fileStatus != FileStatus.FAILED) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { message.fileProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                trackColor = if (isOut) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
            )
        }
        
        if (isComplete && !isOut && message.filePath != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "Stored Securely:",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = subTextColor,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = message.filePath.substringAfterLast("/"),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
