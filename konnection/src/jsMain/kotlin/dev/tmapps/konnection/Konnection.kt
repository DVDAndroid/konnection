package dev.tmapps.konnection

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import org.w3c.fetch.NO_CORS
import org.w3c.fetch.RequestInit
import org.w3c.fetch.RequestMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class UrlPingKonnectionCheck(
  private val pingUrl: String,
  pingInterval: Duration,
) : AbstractUrlPingKonnectionCheck(pingInterval, MainScope()) {

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

}

actual class Konnection(
  private val pingUrl: String = "https://www.google.com",
  private val pingInterval: Duration = 5.seconds,
) : KonnectionCheck by UrlPingKonnectionCheck(pingUrl, pingInterval)