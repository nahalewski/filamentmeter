package com.ben.filamentmeter.bambu

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException

data class DiscoveredPrinter(
    val name: String,
    val model: String,
    val ip: String,
    val serialNumber: String
)

object BambuDiscovery {
    private const val MULTICAST_GROUP = "239.255.255.250"
    private val PORTS = listOf(2021, 1990)

    suspend fun discover(context: Context, timeoutMs: Long = 5000): DiscoveredPrinter? = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("BambuDiscoveryLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        try {
            val endTime = System.currentTimeMillis() + timeoutMs
            for ((index, port) in PORTS.withIndex()) {
                val remainingTime = endTime - System.currentTimeMillis()
                if (remainingTime <= 0) break
                val printer = listenOnPort(port, (remainingTime / (PORTS.size - index)).toInt())
                if (printer != null) return@withContext printer
            }
            null
        } finally {
            try {
                if (lock != null && lock.isHeld) {
                    lock.release()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun listenOnPort(port: Int, timeoutMs: Int): DiscoveredPrinter? {
        var socket: MulticastSocket? = null
        return try {
            socket = MulticastSocket(port).apply {
                soTimeout = timeoutMs.coerceAtLeast(1000)
                reuseAddress = true
                val group = InetAddress.getByName(MULTICAST_GROUP)
                joinGroup(group)
            }

            val buffer = ByteArray(4096)
            val packet = DatagramPacket(buffer, buffer.size)

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    packet.length = buffer.size
                    socket.soTimeout = (timeoutMs - (System.currentTimeMillis() - startTime)).toInt().coerceAtLeast(1)
                    socket.receive(packet)
                    val raw = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val printer = parsePacket(raw, packet.address?.hostAddress)
                    if (printer != null) {
                        return printer
                    }
                } catch (_: SocketTimeoutException) {
                    break
                }
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            try {
                socket?.leaveGroup(InetAddress.getByName(MULTICAST_GROUP))
            } catch (_: Throwable) {}
            socket?.close()
        }
    }

    fun parsePacket(raw: String, packetSenderIp: String?): DiscoveredPrinter? {
        if (!raw.contains("urn:bambulab-com:device:3dprinter:1", ignoreCase = true) &&
            !raw.contains("DevModel.bambu.com", ignoreCase = true)
        ) {
            return null
        }

        var ip = packetSenderIp ?: ""
        var serial = ""
        var name = ""
        var model = ""

        raw.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex != -1) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                when {
                    key.equals("Location", ignoreCase = true) -> {
                        if (value.isNotBlank()) ip = value
                    }
                    key.equals("USN", ignoreCase = true) -> {
                        serial = value
                    }
                    key.equals("DevName.bambu.com", ignoreCase = true) -> {
                        name = value
                    }
                    key.equals("DevModel.bambu.com", ignoreCase = true) -> {
                        model = value
                    }
                }
            }
        }

        val identified = com.ben.filamentmeter.model.PrinterModel.identify(model, serial)
        val friendlyModel = if (identified == com.ben.filamentmeter.model.PrinterModel.UNKNOWN)
            model.ifBlank { "Bambu 3D Printer" } else identified.label

        val finalName = when {
            name.isNotBlank() -> "$name ($friendlyModel)"
            else -> friendlyModel
        }

        return if (ip.isNotBlank() && serial.isNotBlank()) {
            DiscoveredPrinter(
                name = finalName,
                model = friendlyModel,
                ip = ip,
                serialNumber = serial
            )
        } else null
    }
}
