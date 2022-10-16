package dev.tmapps.konnection

/** The ip info data */
sealed interface IpInfo {
    data class WifiIpInfo(
        val ipv4: String?,
        val ipv6: String?,
        val externalIp: String?,
    ): IpInfo

    data class MobileIpInfo(
        val hostIpv4: String?,
        val externalIpV4: String?
    ): IpInfo

    data class GenericIpInfo(
        val externalIp: String?,
    ): IpInfo
}
