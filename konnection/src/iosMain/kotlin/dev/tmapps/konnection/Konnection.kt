package dev.tmapps.konnection

import dev.tmapps.konnection.resolvers.IPv6TestIpResolver
import dev.tmapps.konnection.resolvers.MyExternalIpResolver
import dev.tmapps.konnection.utils.*
import kotlinx.cinterop.*
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.SystemConfiguration.*
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_queue_attr_make_with_qos_class
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.posix.*

class DefaultConnectionCheck(
    private val enableDebugLog: Boolean = false,
    private val ipResolvers: List<IpResolver> = listOf(
        MyExternalIpResolver(enableDebugLog),
        IPv6TestIpResolver(enableDebugLog)
    ),
) : KonnectionCheck {
    private val zeroAddress: NativePointed
    private val reachabilityRef: SCNetworkReachabilityRef
    private val reachabilitySerialQueue: dispatch_queue_t
    private val context: SCNetworkReachabilityContext

    private val notificationObserver: NSObjectProtocol
    private val selfPtr: StableRef<KonnectionCheck>

    private val reachabilityBroadcaster = MutableStateFlow(TriggerEvent)

    // need to be an `internal var` to allow unit tests
    internal var reachabilityInteractor: ReachabilityInteractor = ReachabilityInteractorImpl()
    internal var ifaddrsInteractor: IfaddrsInteractor = IfaddrsInteractorImpl(enableDebugLog)

    init {
        val sizeSockaddr = sizeOf<sockaddr_in>()
        val alignSockaddr = alignOf<sockaddr_in>()
        zeroAddress = nativeHeap.alloc(sizeSockaddr, alignSockaddr).reinterpret<sockaddr_in>()
        zeroAddress.sin_len = sizeOf<sockaddr_in>().toUByte()
        zeroAddress.sin_family = AF_INET.convert()

        reachabilityRef = SCNetworkReachabilityCreateWithAddress(null, zeroAddress.ptr.reinterpret<sockaddr>())
            ?: throw IllegalStateException("Failed on SCNetworkReachabilityCreateWithAddress")

        val dispatchQueueAttr = dispatch_queue_attr_make_with_qos_class(null, QOS_CLASS_DEFAULT, 0)

        reachabilitySerialQueue = dispatch_queue_create("com.tmapps.konnection", dispatchQueueAttr)

        notificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = "ReachabilityChangedNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { reachabilityBroadcaster.value = TriggerEvent }
        )

        selfPtr = StableRef.create(this)

        val sizeSCNetReachCxt = sizeOf<SCNetworkReachabilityContext>()
        val alignSCNetReachCxt = alignOf<SCNetworkReachabilityContext>()
        context = nativeHeap.alloc(sizeSCNetReachCxt, alignSCNetReachCxt).reinterpret<SCNetworkReachabilityContext>()
        context.version = 0
        context.info = selfPtr.asCPointer()
        context.retain = null
        context.release = null
        context.copyDescription = null

        val callback: SCNetworkReachabilityCallBack = staticCFunction { _: SCNetworkReachabilityRef?, _: SCNetworkReachabilityFlags, info: COpaquePointer? ->
            // this block runs on "network_helper" thread, created few lines above
            if (info == null) {
                return@staticCFunction
            }

            // debugLog("SCNetworkReachabilityCallBack fired!")
            // reachabilityBroadcaster.value = TriggerEvent -> not working: EXC_BAD_ACCESS!

            try {
                NSNotificationCenter.defaultCenter.postNotificationName("ReachabilityChangedNotification", null)
            } catch (error: Throwable) {
                // can't send Reachability update, probably the instance of Konnection is dead!
                // or some unexpected error occurs ¯\_(ツ)_/¯
                // debugLog("SCNetworkReachabilityCallBack error!", error)
            }
        }

        if (!SCNetworkReachabilitySetCallback(reachabilityRef, callback, context.ptr)) {
            throw IllegalStateException("Failed on SCNetworkReachabilitySetCallback")
        }
        if (!SCNetworkReachabilitySetDispatchQueue(reachabilityRef, reachabilitySerialQueue)) {
            throw IllegalStateException("Failed on SCNetworkReachabilitySetDispatchQueue")
        }
    }

    override suspend fun isConnected(): Boolean {
        val flags = getReachabilityFlags()
        val isReachable = flags.contains(kSCNetworkReachabilityFlagsReachable)
        val needsConnection = flags.contains(kSCNetworkReachabilityFlagsConnectionRequired)
        return isReachable && !needsConnection
    }

    override fun observeHasConnection(): Flow<Boolean> = observeNetworkConnection().map { it != null }

    override suspend fun getCurrentNetworkConnection(): NetworkConnection? {
        val flags = getReachabilityFlags()
        val isReachable = flags.contains(kSCNetworkReachabilityFlagsReachable)
        val needsConnection = flags.contains(kSCNetworkReachabilityFlagsConnectionRequired)
        val isMobileConnection = flags.contains(kSCNetworkReachabilityFlagsIsWWAN)

        return when {
            !isReachable || needsConnection -> null
            isMobileConnection -> NetworkConnection.MOBILE
            else -> NetworkConnection.WIFI
        }
    }

    override fun observeNetworkConnection(): Flow<NetworkConnection?> =
        reachabilityBroadcaster.map { getCurrentNetworkConnection() }

    override suspend fun getCurrentIpInfo(): IpInfo? {
        val networkConnection = getCurrentNetworkConnection() ?: return null

        val networkInterface = networkConnection.networkInterface
        val ipv4 = ifaddrsInteractor.get(networkInterface, AF_INET)
        val ipv6 = ifaddrsInteractor.get(networkInterface, AF_INET6)

        return when (networkConnection) {
            NetworkConnection.WIFI -> IpInfo.WifiIpInfo(ipv4 = ipv4, ipv6 = ipv6, externalIp = getExternalIp())
            NetworkConnection.MOBILE -> IpInfo.MobileIpInfo(hostIpv4 = ipv4, externalIpV4 = getExternalIp())
            else -> null
        }
    }

    fun stop() {
        NSNotificationCenter.defaultCenter.removeObserver(notificationObserver)
        SCNetworkReachabilitySetCallback(reachabilityRef, null, null)
        SCNetworkReachabilitySetDispatchQueue(reachabilityRef, null)
        selfPtr.dispose()
        nativeHeap.free(context)
        nativeHeap.free(zeroAddress)
    }

    private fun getReachabilityFlags(): Array<SCNetworkReachabilityFlags> {
        val flags = reachabilityInteractor.getReachabilityFlags(reachabilityRef) ?: return emptyArray()

        val result = arrayOf<SCNetworkReachabilityFlags>(
            kSCNetworkReachabilityFlagsTransientConnection,
            kSCNetworkReachabilityFlagsReachable,
            kSCNetworkReachabilityFlagsConnectionRequired,
            kSCNetworkReachabilityFlagsConnectionOnTraffic,
            kSCNetworkReachabilityFlagsInterventionRequired,
            kSCNetworkReachabilityFlagsConnectionOnDemand,
            kSCNetworkReachabilityFlagsIsLocalAddress,
            kSCNetworkReachabilityFlagsIsDirect,
            kSCNetworkReachabilityFlagsIsWWAN,
            kSCNetworkReachabilityFlagsConnectionAutomatic
        ).filter {
            (flags and it) > 0u
        }.toTypedArray()
        debugLog("SCNetworkReachabilityFlags: ${result.contentDeepToString()}")
        return result
    }

    private val NetworkConnection.networkInterface: String
        get() = when (this) {
            NetworkConnection.WIFI -> "en0"
            NetworkConnection.MOBILE -> "pdp_ip0"
            else -> "unknown"
        }

    private suspend fun getExternalIp(): String? =
        ipResolvers.firstNotNullOfOrNull { it.get() }

    private fun debugLog(message: String, error: Throwable? = null) {
        if (enableDebugLog) {
            val logMessage = message + if (error != null) "error=($error)" else ""
            NSLog("Konnection -> $logMessage")
        }
    }
}

actual class Konnection(private val check: KonnectionCheck) : KonnectionCheck by check