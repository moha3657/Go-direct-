package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ChatScreen
import com.example.ui.HomeScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var permissionsGranted by remember { mutableStateOf(false) }

                // Define modern offline direct connection permission strings
                val permissionsToRequest = remember {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.NEARBY_WIFI_DEVICES
                        )
                    } else {
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    val allGranted = result.values.all { it }
                    permissionsGranted = allGranted
                }

                // Check permissions on startup
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(permissionsToRequest)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!permissionsGranted) {
                        PermissionGate(
                            onGrantClick = { permissionLauncher.launch(permissionsToRequest) }
                        )
                    } else {
                        var currentScreen by remember { mutableStateOf("home") }

                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn().togetherWith(fadeOut())
                            },
                            label = "ScreenTransition"
                        ) { screen ->
                            when (screen) {
                                "home" -> HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToChat = { currentScreen = "chat" },
                                    modifier = Modifier.fillMaxSize()
                                )
                                "chat" -> ChatScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { currentScreen = "home" },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionGate(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "DirectChat Secure permissions required",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To discover nearby peers and establish direct P2P connections offline, Android requires standard Location and Nearby Devices authorization.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("grant_permissions_button")
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Authorize Security Channels")
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
