package github.leavesczy.wifip2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager

/**
 * Interface for handling Wi-Fi Direct actions.
 */
interface DirectActionListener : WifiP2pManager.ChannelListener {

    /**
     * Called when Wi-Fi P2P is enabled or disabled.
     */
    fun wifiP2pEnabled(enabled: Boolean)

    /**
     * Called when connection info is available.
     */
    fun onConnectionInfoAvailable(wifiP2pInfo: WifiP2pInfo)

    /**
     * Called when disconnected from a peer device.
     */
    fun onDisconnection()

    /**
     * Called when the device itself becomes available.
     */
    fun onSelfDeviceAvailable(device: WifiP2pDevice)

    /**
     * Called when a list of peers becomes available.
     */
    fun onPeersAvailable(devices: List<WifiP2pDevice>)
}

/**
 * BroadcastReceiver for Wi-Fi Direct events.
 */
class DirectBroadcastReceiver(
    private val wifiP2pManager: WifiP2pManager,
    private val wifiP2pChannel: WifiP2pManager.Channel,
    private val directActionListener: DirectActionListener
) : BroadcastReceiver() {

    companion object {

        /**
         * Returns an IntentFilter that listens for Wi-Fi Direct actions.
         */
        fun getIntentFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            return intentFilter
        }
    }

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val enabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                directActionListener.wifiP2pEnabled(enabled)
                if (!enabled) {
                    // If P2P is disabled, notify that no peers are available
                    directActionListener.onPeersAvailable(emptyList())
                }
                Logger.log("WIFI_P2P_STATE_CHANGED_ACTION: $enabled")
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Logger.log("WIFI_P2P_PEERS_CHANGED_ACTION")
                wifiP2pManager.requestPeers(wifiP2pChannel) { peers ->
                    directActionListener.onPeersAvailable(peers.deviceList.toList())
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                Logger.log("WIFI_P2P_CONNECTION_CHANGED_ACTION: ${networkInfo?.isConnected}")
                if (networkInfo?.isConnected == true) {
                    wifiP2pManager.requestConnectionInfo(wifiP2pChannel) { info ->
                        info?.let { directActionListener.onConnectionInfoAvailable(it) }
                    }
                    Logger.log("Connected to P2P device")
                } else {
                    directActionListener.onDisconnection()
                    Logger.log("Disconnected from P2P device")
                }
            }

            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val wifiP2pDevice = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                wifiP2pDevice?.let { directActionListener.onSelfDeviceAvailable(it) }
                Logger.log("WIFI_P2P_THIS_DEVICE_CHANGED_ACTION: $wifiP2pDevice")
            }
        }
    }
}
