package dev.tmapps.konnection

import dev.tmapps.konnection.resolvers.MyExternalIpResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration

interface KonnectionCheck {
    /** Returns true if has some Network Connection otherwise false. */
    suspend fun isConnected(): Boolean
    /** Hot Flow that emits if has Network Connection or not. */
    fun observeHasConnection(): Flow<Boolean>

    /** Returns the current Network Connection. */
    suspend fun getCurrentNetworkConnection(): NetworkConnection?
    /** Hot Flow that emits the current Network Connection. */
    fun observeNetworkConnection(): Flow<NetworkConnection?>

    /** Returns the ip info from the current Network Connection. */
    suspend fun getCurrentIpInfo(): IpInfo?
}

abstract class AbstractKonnectionCheck : KonnectionCheck {
    protected val connectionPublisher = MutableStateFlow<NetworkConnection?>(null)

    /** Hot Flow that emits if has Network Connection or not. */
    override fun observeHasConnection(): Flow<Boolean> = connectionPublisher.map { it != null }

    /** Hot Flow that emits the current Network Connection. */
    override fun observeNetworkConnection(): Flow<NetworkConnection?> = connectionPublisher
}

abstract class AbstractUrlPingKonnectionCheck(
    private val pingInterval: Duration,
    scope: CoroutineScope,
) : AbstractKonnectionCheck() {
    protected val requestTimeout = (pingInterval.inWholeMilliseconds - (pingInterval.inWholeMilliseconds / 10)).toInt()

    init {
        scope.launch(Dispatchers.Default) {
            while (true) {
                connectionPublisher.value = if (isConnected()) NetworkConnection.UNKNOWN else null
                delay(pingInterval)
            }
        }
    }

    /** Returns the current Network Connection. */
    override suspend fun getCurrentNetworkConnection(): NetworkConnection? {
        return if (connectionPublisher.value != null) NetworkConnection.UNKNOWN else null
    }

    /** Returns the ip info from the current Network Connection. */
    override suspend fun getCurrentIpInfo(): IpInfo? {
        if (connectionPublisher.value == null) return null

        val externalIpResolver = MyExternalIpResolver().get()
        return IpInfo.GenericIpInfo(externalIpResolver)
    }
}

expect class Konnection : KonnectionCheck