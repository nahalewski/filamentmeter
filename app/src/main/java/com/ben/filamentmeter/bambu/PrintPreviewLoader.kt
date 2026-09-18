package com.ben.filamentmeter.bambu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ben.filamentmeter.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.security.cert.X509Certificate
import java.util.zip.ZipFile
import javax.net.ssl.X509TrustManager

object PrintPreviewLoader {
    private val mutex=Mutex()
    private val cache=linkedMapOf<String,Bitmap>()
    suspend fun load(context: Context, config: AppSettings, state: PrinterState): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key="${config.serialNumber}|${state.jobId}|${state.jobName}|${state.jobFile}|${state.jobPlate}"
            cache[key]?.let { return@withLock it }
            val paths=PrintPreviewArchive.paths(state.jobFile)
            require(paths.isNotEmpty()) { "The printer has not supplied a 3MF filename" }
            val ftp=FTPSClient(true)
            // P1 firmware replies with bare status codes such as "331 ".
            ftp.isStrictReplyParsing=false
            val temp=File.createTempFile("print-preview-", ".3mf",context.cacheDir)
            try {
                // Bambu LAN endpoints use self-signed certificates, like the existing MQTT client.
                ftp.trustManager=object: X509TrustManager {
                    override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
                    override fun checkClientTrusted(chain: Array<X509Certificate>?,authType: String?)=Unit
                    override fun checkServerTrusted(chain: Array<X509Certificate>?,authType: String?)=Unit
                }
                ftp.connectTimeout=8000;ftp.defaultTimeout=8000;ftp.setDataTimeout(java.time.Duration.ofSeconds(10))
                ftp.controlEncoding="UTF-8"
                ftp.connect(config.printerIp,990)
                check(ftp.login("bblp",config.accessCode)) { "Printer file access was denied" }
                ftp.execPBSZ(0);ftp.execPROT("P");ftp.enterLocalPassiveMode();ftp.setFileType(FTP.BINARY_FILE_TYPE)
                var downloaded=false
                val deadline=android.os.SystemClock.elapsedRealtime()+60_000
                for(path in paths) {
                    currentCoroutineContext().ensureActive()
                    val input=ftp.retrieveFileStream(path) ?: continue
                    input.use { stream -> temp.outputStream().use { output ->
                        val buffer=ByteArray(32768);var count=0L
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            check(android.os.SystemClock.elapsedRealtime()<deadline) { "Preview download timed out" }
                            val read=stream.read(buffer);if(read<0) break
                            count+=read;check(count<=64L*1024*1024) { "Print archive exceeds the 64 MB preview limit" }
                            output.write(buffer,0,read)
                        }
                    } }
                    check(ftp.completePendingCommand()) { "Printer file transfer did not complete" }
                    downloaded=true;break
                }
                check(downloaded) { "The active print file is not available on the printer" }
                val bytes=ZipFile(temp).use { PrintPreviewArchive.extract(it,state.jobPlate) }
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192) { "Invalid preview dimensions" }
                val options=BitmapFactory.Options().apply {
                    inSampleSize=1
                    while(bounds.outWidth/inSampleSize>1024 || bounds.outHeight/inSampleSize>1024) inSampleSize*=2
                }
                val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: error("Preview image could not be read")
                currentCoroutineContext().ensureActive()
                cache[key]=bitmap
                while(cache.size>4) cache.remove(cache.keys.first())
                bitmap
            } finally {
                runCatching { ftp.disconnect() };temp.delete()
            }
        }
    }
}
