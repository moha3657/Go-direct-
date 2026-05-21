package com.example.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class WifiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirectManager"
    }

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    // Real device state flows
    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private val _thisDevice = MutableStateFlow<WifiP2pDevice?>(null)
    val thisDevice: StateFlow<WifiP2pDevice?> = _thisDevice.asStateFlow()

    // Flag for simulation / demo mode to enable testing on a single emulator
    private val _isSimulationMode = MutableStateFlow(false)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    init {
        try {
            wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            wifiP2pManager?.let { p2p ->
                channel = p2p.initialize(context, context.mainLooper, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WifiP2pManager: ${e.message}")
        }
    }

    // Set simulator mode
    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        if (enabled) {
            setupSimulatedPeers()
        } else {
            _peers.value = emptyList()
            _connectionInfo.value = null
        }
    }

    private fun setupSimulatedPeers() {
        val device1 = WifiP2pDevice().apply {
            deviceName = "DirectChat_Secure_01"
            deviceAddress = "02:00:00:00:00:01"
            status = WifiP2pDevice.AVAILABLE
        }
        val device2 = WifiP2pDevice().apply {
            deviceName = "DirectChat_Secure_02"
            deviceAddress = "02:00:00:00:00:02"
            status = WifiP2pDevice.AVAILABLE
        }
        _peers.value = listOf(device1, device2)
    }

    fun registerReceiver() {
        if (_isSimulationMode.value) return
        if (wifiP2pManager == null || channel == null) return

        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (_isSimulationMode.value) return
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                        Log.d(TAG, "P2P state changed: ${_isWifiP2pEnabled.value}")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        requestPeers()
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val wifiP2pInfo = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        Log.d(TAG, "P2P connection changed. Is connected: ${networkInfo?.isConnected}")
                        
                        _connectionInfo.value = wifiP2pInfo
                        if (networkInfo?.isConnected == false) {
                            _connectionInfo.value = null
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                        _thisDevice.value = device
                        Log.d(TAG, "Our device info updated: ${device?.deviceName}")
                    }
                }
            }
        }
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver: ${e.message}")
            }
        }
        receiver = null
    }

    fun discoverPeers(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        _isDiscovering.value = true
        if (_isSimulationMode.value) {
            setupSimulatedPeers()
            onSuccess()
            return
        }

        val manager = wifiP2pManager
        val chan = channel
        if (manager == null || chan == null) {
            _isDiscovering.value = false
            onFailure(WifiP2pManager.P2P_UNSUPPORTED)
            return
        }

        manager.discoverPeers(chan, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Peer discovery started successfully.")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Peer discovery failed. Reason: $reason")
                _isDiscovering.value = false
                onFailure(reason)
            }
        })
    }

    private fun requestPeers() {
        if (wifiP2pManager == null || channel == null) return
        wifiP2pManager?.requestPeers(channel, WifiP2pManager.PeerListListener { peerList ->
            _peers.value = peerList.deviceList.toList()
            Log.d(TAG, "Peers discovered: ${_peers.value.size}")
        })
    }

    fun connect(device: WifiP2pDevice, onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        if (_isSimulationMode.value) {
            // Simulated connection setup
            val simInfo = WifiP2pInfo().apply {
                groupFormed = true
                isGroupOwner = true // We act as server in simulation, or client
                groupOwnerAddress = java.net.InetAddress.getByName("127.0.0.1")
            }
            _connectionInfo.value = simInfo
            onSuccess()
            return
        }

        val manager = wifiP2pManager
        val chan = channel
        if (manager == null || chan == null) {
            onFailure(WifiP2pManager.P2P_UNSUPPORTED)
            return
        }

        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
        }

        manager.connect(chan, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connecting success trigger.")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connecting failed. Reason: $reason")
                onFailure(reason)
            }
        })
    }

    fun disconnect(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        if (_isSimulationMode.value) {
            _connectionInfo.value = null
            onSuccess()
            return
        }

        val manager = wifiP2pManager
        val chan = channel
        if (manager == null || chan == null) {
            onFailure(WifiP2pManager.P2P_UNSUPPORTED)
            return
        }

        // Cancel connect or remove group
        manager.removeGroup(chan, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _connectionInfo.value = null
                _peers.value = emptyList()
                Log.d(TAG, "Disconnected from P2P group.")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Disconnect failed: $reason. Trying cancelConnect...")
                manager.cancelConnect(chan, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        _connectionInfo.value = null
                        onSuccess()
                    }

                    override fun onFailure(reason: Int) {
                        onFailure(reason)
                    }
                })
            }
        })
    }

    fun createGroup(onSuccess: () -> Unit = {}, onFailure: (Int) -> Unit = {}) {
        if (wifiP2pManager == null || channel == null) {
            onFailure(WifiP2pManager.P2P_UNSUPPORTED)
            return
        }
        wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "P2P group created successfully.")
                onSuccess()
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "P2P group creation failed: $reason")
                onFailure(reason)
            }
        })
    }
}
