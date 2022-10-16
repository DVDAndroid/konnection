package dev.tmapps.konnection

import dev.tmapps.konnection.resolvers.MyExternalIpResolver
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.w3c.fetch.NO_CORS
import org.w3c.fetch.RequestInit
import org.w3c.fetch.RequestMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class UrlPingKonnectionCheck(
  private val pingUrl: String,
  private val pingInterval: Duration,
) : AbstractKonnectionCheck() {

  private val jsScope = MainScope()
  private val requestTimeout = (pingInterval.inWholeMilliseconds - (pingInterval.inWholeMilliseconds / 10)).toInt()

  init {
    jsScope.launch(Dispatchers.Default) {
      while (true) {
        connectionPublisher.value = if (isConnected()) NetworkConnection.UNKNOWN else null
        delay(pingInterval)
      }
    }
  }

  /** Returns true if has some Network Connection otherwise false. */
  override suspend fun isConnected(): Boolean {
    val abortController = AbortController()
    val handle = window.setTimeout({ abortController.abort() }, requestTimeout)

    return window.fetch(pingUrl, jsObject {
      mode = RequestMode.NO_CORS
      signal = abortController.signal
    }.unsafeCast<RequestInit>())
      .then { return@then true }
      .catch { return@catch false }
      .finally { window.clearTimeout(handle) }
      .await()
  }

  /** Returns the current Network Connection. */
  override suspend fun getCurrentNetworkConnection(): NetworkConnection? {
    return if (isConnected()) NetworkConnection.UNKNOWN else null
  }

  /** Returns the ip info from the current Network Connection. */
  override suspend fun getCurrentIpInfo(): IpInfo? {
    if (getCurrentNetworkConnection() == null) return null

    val externalIpResolver = MyExternalIpResolver().get()
    return IpInfo.GenericIpInfo(externalIpResolver)
  }

}

actual class Konnection(
  private val pingUrl: String = "https://www.google.com",
  private val pingInterval: Duration = 5.seconds,
) : KonnectionCheck by UrlPingKonnectionCheck(pingUrl, pingInterval)