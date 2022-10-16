package dev.tmapps.konnection

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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

expect class Konnection : KonnectionCheck