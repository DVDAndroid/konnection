package dev.tmapps.konnection

import kotlinx.browser.document
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

suspend fun main() {
  val konnection = Konnection()

  combine(
    konnection.observeHasConnection(),
    konnection.observeNetworkConnection()
  ) { hasConnection, networkConnection ->
    document.getElementById("hasConnection")?.textContent = "Has connection: $hasConnection"
    document.getElementById("network")?.textContent = "Network: $networkConnection"
    document.getElementById("info")?.textContent = "IP info: ${konnection.getCurrentIpInfo()}"
  }.collect()
}