package com.ben.filamentmeter.bambu

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ben.filamentmeter.model.AppSettings
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** P1 LAN camera: TLS on 6000, 80-byte authentication, length-prefixed JPEG frames. */
class BambuCamera {
    @Volatile private var closed = false
    @Volatile private var socket: Socket? = null

    fun close() {
        closed = true
        runCatching { socket?.close() }
    }

    fun stream(settings: AppSettings, onFrame: (Bitmap) -> Unit) {
        check(settings.printerIp.isNotBlank() && settings.accessCode.isNotBlank()) {
            "Enter the printer IP and LAN access code in Setup."
        }
        val ssl = SSLContext.getInstance("TLS").apply {
            // P1 printers use a self-signed certificate on the local network, as with MQTT.
            init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }), SecureRandom())
        }
        val tcp = Socket()
        socket = tcp
        try {
            check(!closed)
            tcp.connect(InetSocketAddress(settings.printerIp, 6000), 8000)
            val tls = ssl.socketFactory.createSocket(tcp, settings.printerIp, 6000, true) as SSLSocket
            socket = tls
            if (closed) return
            tls.soTimeout = 15000
            tls.startHandshake()
            val auth = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(64).putInt(0x3000).putInt(0).putInt(0).array()
            "bblp".toByteArray().copyInto(auth, 16)
            settings.accessCode.toByteArray(Charsets.US_ASCII).take(32).toByteArray().copyInto(auth, 48)
            tls.outputStream.write(auth)
            tls.outputStream.flush()
            val input = DataInputStream(tls.inputStream)
            while (!closed) {
                val header = ByteArray(16)
                input.readFully(header)
                val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
                check(length in 1..5_000_000) { "Invalid camera frame. Check LAN camera access." }
                val jpeg = ByteArray(length)
                input.readFully(jpeg)
                val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, length)
                    ?: error("Camera did not return a JPEG frame")
                if (!closed) onFrame(bitmap)
            }
        } finally { runCatching { socket?.close() }; tcp.close() }
    }
}
