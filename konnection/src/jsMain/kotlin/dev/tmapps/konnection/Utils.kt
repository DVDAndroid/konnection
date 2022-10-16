package dev.tmapps.konnection

import kotlinx.browser.window
import kotlinx.coroutines.await

internal actual suspend fun getUrlContent(url: String): String? {
  val abortController = AbortController()
  val handle = window.setTimeout({ abortController.abort() }, 5_000)

  return try {
    window.fetch(url)
      .await()
      .text()
      .await()
  } catch (e: Exception) {
    null
  } finally {
    window.clearTimeout(handle)
  }
}

internal actual fun logError(tag: String, message: String, error: Throwable) {
  console.error("$tag: $message", error)
}

internal external class AbortController {
  var signal: AbortController
  fun abort()
}

internal inline fun jsObject(init: dynamic.() -> Unit): dynamic {
  val o = js("{}")
  init(o)
  return o
}