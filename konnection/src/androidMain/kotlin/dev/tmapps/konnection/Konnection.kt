package dev.tmapps.konnection

import android.annotation.TargetApi
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import dev.tmapps.konnection.resolvers.IPv6TestIpResolver
import dev.tmapps.konnection.resolvers.MyExternalIpResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

interface AndroidKonnectionCheck : KonnectionCheck {
    suspend fun init()
}

class AndroidDefaultConnectionCheck(
    context: Context,
    private val enableDebugLog: Boolean = false,
    private val ipResolvers: List<IpResolver> = listOf(
        MyExternalIpResolver(enableDebugLog),
        IPv6TestIpResolver(enableDebugLog)
    )
) : AndroidKonnectionCheck {
    @VisibleForTesting
    internal var connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val connectionPublisher = MutableStateFlow<NetworkConnection?>(null)
    private var networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            debugLog("NetworkCallback -> onAvailable: network=($network)")
            // need this only for Android API < 23
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                connectionPublisher.value = getNetworkConnection(network)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val connection = getNetworkConnection(networkCapabilities)
            debugLog("NetworkCallback -> onCapabilitiesChanged: connection=($connection)")
            connectionPublisher.value = connection
        }

        override fun onLost(network: Network) {
            debugLog("NetworkCallback -> onLost: network=($network)")
            connectionPublisher.value = null
        }
    }

    override suspend fun init() {
        connectionPublisher.value = getCurrentNetworkConnection()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }
    }

    override suspend fun isConnected(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            postAndroidMInternetCheck(connectivityManager)
        } else {
            preAndroidMInternetCheck(connectivityManager)
        }

    override fun observeHasConnection(): Flow<Boolean> = connectionPublisher.map { it != null }

    override suspend fun getCurrentNetworkConnection(): NetworkConnection? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            postAndroidMNetworkConnection(connectivityManager)
        } else {
            preAndroidMNetworkConnection(connectivityManager)
        }

    override fun observeNetworkConnection(): Flow<NetworkConnection?> = connectionPublisher

    override suspend fun getCurrentIpInfo(): IpInfo? = getIpInfo(getCurrentNetworkConnection())

    private fun getNetworkConnection(network: Network): NetworkConnection? {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return getNetworkConnection(capabilities)
    }

    // region post Android M
    @TargetApi(Build.VERSION_CODES.M)
    private fun postAndroidMInternetCheck(connectivityManager: ConnectivityManager): Boolean =
        postAndroidMNetworkConnection(connectivityManager) != null

    @TargetApi(Build.VERSION_CODES.M)
    private fun postAndroidMNetworkConnection(connectivityManager: ConnectivityManager): NetworkConnection? {
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return getNetworkConnection(capabilities)
    }
    // endregion

    // region pre Android M
    private fun preAndroidMInternetCheck(connectivityManager: ConnectivityManager): Boolean =
        preAndroidMNetworkConnection(connectivityManager) != null

    @Suppress("DEPRECATION")
    private fun preAndroidMNetworkConnection(connectivityManager: ConnectivityManager): NetworkConnection? =
        when (connectivityManager.activeNetworkInfo?.type) {
            null -> null
            ConnectivityManager.TYPE_WIFI -> NetworkConnection.WIFI
            else -> NetworkConnection.MOBILE
        }
    // endregion

    private fun getNetworkConnection(capabilities: NetworkCapabilities?): NetworkConnection? =
        when {
            capabilities == null -> null
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> null

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) -> null

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkConnection.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkConnection.MOBILE
            else -> null
        }

    private suspend fun getIpInfo(networkConnection: NetworkConnection?): IpInfo? {
        if (networkConnection == null) return null
        try {
            var ipv4: String? = null
            var ipv6: String? = null

            @Suppress("BlockingMethodInNonBlockingContext")
            val networks = NetworkInterface.getNetworkInterfaces()

            while (networks.hasMoreElements()) {
                val enumIpAddr = networks.nextElement().inetAddresses

                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    debugLog("getIpAddress inetAddress = $inetAddress")
                    if (!inetAddress.isLoopbackAddress) {
                        if (ipv4 == null && inetAddress is Inet4Address) ipv4 = inetAddress.hostAddress?.toString()
                        if (ipv6 == null && inetAddress is Inet6Address) ipv6 = inetAddress.hostAddress?.toString()
                    }
                }
            }

            return when (networkConnection) {
                NetworkConnection.WIFI -> IpInfo.WifiIpInfo(ipv4 = ipv4, ipv6 = ipv6, externalIp = getExternalIp())
                NetworkConnection.MOBILE -> IpInfo.MobileIpInfo(hostIpv4 = ipv4, externalIpV4 = getExternalIp())
                else -> null
            }
        } catch (ex: Exception) {
            debugLog("getIpInfo networkConnection = $networkConnection", ex)
            return null
        }
    }

    private suspend fun getExternalIp(): String? = withContext(Dispatchers.IO) {
        ipResolvers.firstNotNullOfOrNull { it.get() }
    }

    private fun debugLog(message: String, error: Throwable? = null) {
        if (enableDebugLog) {
            Log.d("Konnection", message, error)
        }
    }
}

actual class Konnection(private val check: AndroidKonnectionCheck) : KonnectionCheck by check {
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        scope.launch { check.init() }
    }
}