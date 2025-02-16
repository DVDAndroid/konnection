package dev.tmapps.konnection

import dev.tmapps.konnection.utils.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class KonnectionWrapper {
    private val mainScope = MainScope()
    private val konnection = Konnection(enableDebugLog = true)

    fun hasNetworkConnection(): Boolean = konnection.isConnected()

    fun stop() {
        konnection.stop()
     // mainScope.cancel()
    }

    fun networkConnectionObservation(callback: (NetworkConnection?) -> Unit) {
        konnection.observeNetworkConnection()
            .onEach { callback(it) }
            .launchIn(mainScope)
    }

    suspend fun getCurrentIpInfo(): IpInfo? = konnection.getCurrentIpInfo()
}
