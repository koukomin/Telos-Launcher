package com.telos.desktop.panel

import com.telos.desktop.linux.Sys

data class WifiAp(val ssid: String, val signal: Int, val connected: Boolean, val security: String)
data class BtDev(val mac: String, val name: String, val connected: Boolean, val battery: Int?)
data class ConnectivityState(
    val wifiOn: Boolean,
    val activeSsid: String?,
    val vpn: String?,
    val aps: List<WifiAp>,
    val btOn: Boolean,
    val btDevices: List<BtDev>,
)

object ConnectivityManager {
    fun snapshot(): ConnectivityState {
        val wifi = Sys.run("nmcli", "-t", "-f", "WIFI", "radio").stdout.contains("enabled")
        val active = Sys.run("nmcli", "-t", "-f", "active,ssid", "dev", "wifi").stdout.lineSequence()
            .firstOrNull { it.startsWith("yes:") }?.substringAfter(':')
        val aps = Sys.run("nmcli", "-t", "-f", "IN-USE,SSID,SIGNAL,SECURITY", "dev", "wifi").stdout.lines().mapNotNull { line ->
            val p = line.split(':')
            if (p.size < 4 || p[1].isBlank()) null
            else WifiAp(p[1], p[2].toIntOrNull() ?: 0, p[0] == "*", p[3])
        }.distinctBy { it.ssid }.take(20)
        val vpn = Sys.run("nmcli", "-t", "-f", "TYPE,NAME,DEVICE", "con", "show", "--active").stdout.lineSequence()
            .firstOrNull { it.startsWith("vpn:") || it.startsWith("wireguard:") }?.substringAfter(':')?.substringBefore(':')
        val btPower = Sys.run("bluetoothctl", "show").stdout.contains("Powered: yes")
        val bt = Sys.run("bluetoothctl", "devices").stdout.lines().mapNotNull { line ->
            val rest = line.removePrefix("Device ").trim()
            val mac = rest.substringBefore(' ')
            val name = rest.substringAfter(' ', "")
            if (mac.isBlank()) null else {
                val info = Sys.run("bluetoothctl", "info", mac).stdout
                val connected = info.contains("Connected: yes")
                val bat = Regex("Battery Percentage: .*\\((\\d+)\\)").find(info)?.groupValues?.get(1)?.toIntOrNull()
                BtDev(mac, name, connected, bat)
            }
        }
        return ConnectivityState(wifi, active, vpn, aps, btPower, bt)
    }

    fun wifiConnect(ssid: String) { Sys.run("nmcli", "dev", "wifi", "connect", ssid, timeoutSec = 20) }
    fun wifiDisconnect() { Sys.run("nmcli", "radio", "wifi", "off"); Sys.run("nmcli", "radio", "wifi", "on") }
    fun btPower(on: Boolean) { Sys.run("bluetoothctl", "power", if (on) "on" else "off") }
    fun btConnect(mac: String) { Sys.run("bluetoothctl", "connect", mac, timeoutSec = 12) }
}
