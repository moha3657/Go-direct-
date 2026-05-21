package com.example.ui

import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.ConnectionState
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ChatViewModel,
    onNavigateToChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peers by viewModel.peers.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val isWifiP2pEnabled by viewModel.isWifiP2pEnabled.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val thisDevice by viewModel.thisDevice.collectAsState()

    // Keep dynamic track of navigation on active connection
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.SECURE_ACTIVE || connectionState == ConnectionState.HANDSHAKING) {
            onNavigateToChat()
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            "DirectChat Secure",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "E2EE Offline peer-to-peer chat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startPeerDiscovery() },
                        modifier = Modifier.testTag("refresh_scanner_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Scanner")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            
            // 1. Simulation Toggle Banner
            item {
                SimulationBanner(
                    isSimulationMode = isSimulationMode,
                    onToggleSimulation = { viewModel.toggleSimulationMode(it) }
                )
            }

            // 2. Local Device Info Section
            item {
                LocalDeviceInfoCard(
                    thisDevice = thisDevice,
                    isWifiP2pEnabled = isWifiP2pEnabled,
                    isSimulationMode = isSimulationMode
                )
            }

            // 3. Scan & Radar State
            item {
                ScanRadarSection(
                    isDiscovering = isDiscovering,
                    onStartDiscovery = { viewModel.startPeerDiscovery() }
                )
            }

            // 4. Discovered Peer List Title
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Discovered Devices (${peers.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 5. Peer items
            if (peers.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WifiTethering,
                                contentDescription = "Scanning",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(bottom = 8.dp)
                            )
                            Text(
                                text = "No nearby secure peers visible yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Press 'Scan Nearby' below or enable 'Demo Simulation Mode' above to check messaging logic instantly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(peers) { peer ->
                    PeerDeviceCard(
                        device = peer,
                        onClick = { viewModel.connectToDevice(peer) }
                    )
                }
            }

            // 6. Secure Offline Pairing QR Mock Code (Awesome Bonus Visual Feature!)
            item {
                SecureVerificationFingerprint()
            }
        }
    }
}

@Composable
fun SimulationBanner(
    isSimulationMode: Boolean,
    onToggleSimulation: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSimulationMode) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag("simulation_banner")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Demo Simulation Mode",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSimulationMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Simulates offline P2P handshakes & E2EE files. Ideal for single-emulator checks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSimulationMode) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = isSimulationMode,
                    onCheckedChange = onToggleSimulation,
                    modifier = Modifier.testTag("simulation_toggle_switch")
                )
            }
        }
    }
}

@Composable
fun LocalDeviceInfoCard(
    thisDevice: WifiP2pDevice?,
    isWifiP2pEnabled: Boolean,
    isSimulationMode: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isWifiP2pEnabled || isSimulationMode) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Status",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Local Device: " + (thisDevice?.deviceName ?: Build.MODEL),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (isSimulationMode) "State: Simulation Loop Active" 
                           else if (isWifiP2pEnabled) "State: Wi-Fi P2P Hardware Enabled" 
                           else "State: Wi-Fi Direct Disabled (Using local loopback)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScanRadarSection(
    isDiscovering: Boolean,
    onStartDiscovery: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isDiscovering) {
                // Sonar pulse scanner wave animation using a canvas!
                val transition = rememberInfiniteTransition(label = "RadarWave")
                val pulseRadius by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 100f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Pulse"
                )
                val pulseAlpha by transition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "Alpha"
                )

                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 12.dp)
                ) {
                    val midX = size.width / 2
                    val midY = size.height / 2
                    // Core radar pulse
                    drawCircle(
                        color = primaryColor.copy(alpha = pulseAlpha),
                        radius = pulseRadius * 1.5f,
                        center = androidx.compose.ui.geometry.Offset(midX, midY),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    // Core stationary circle
                    drawCircle(
                        color = primaryColor,
                        radius = 20.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(midX, midY)
                    )
                }
                
                Text(
                    text = "Broadcasting cryptographic beacons...",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "Analyzing nearby direct peers on standard channel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Search offline",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onStartDiscovery,
                    modifier = Modifier.fillMaxWidth().testTag("scan_peers_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Nearby Devices")
                }
            }
        }
    }
}

@Composable
fun PeerDeviceCard(
    device: WifiP2pDevice,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("peer_card_${device.deviceAddress}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        device.deviceName.contains("Secure", ignoreCase = true) -> Icons.Default.VerifiedUser
                        else -> Icons.Default.Smartphone
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.deviceName.ifEmpty { "P2P Device" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Hardware MAC: ${device.deviceAddress}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Peer P2P status badge
            val badgeText = when (device.status) {
                WifiP2pDevice.CONNECTED -> "CONNECTED"
                WifiP2pDevice.INVITED -> "INVITED"
                WifiP2pDevice.FAILED -> "FAILED"
                WifiP2pDevice.AVAILABLE -> "TAP TO CHAT"
                else -> "AVAILABLE"
            }
            val badgeColor = when (device.status) {
                WifiP2pDevice.CONNECTED -> MaterialTheme.colorScheme.primary
                WifiP2pDevice.INVITED -> Color(0xFFFF9800)
                WifiP2pDevice.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
fun SecureVerificationFingerprint() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Integrity Verification",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Verify secure connection fingerprint using QR code pairing to bypass MITM risks on local Wi-Fi channels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Visual simulation of a secure pairing QR Code using Simple Icons on canvas
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.QrCode2,
                        contentDescription = "Pairing Code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
