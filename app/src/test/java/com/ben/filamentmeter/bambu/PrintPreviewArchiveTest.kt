package com.ben.filamentmeter.bambu

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.zip.*

class PrintPreviewArchiveTest {
    @Test fun onlyExactReportedFileIsRequested() {
        assertEquals(listOf("/part.3mf","/cache/part.3mf"),PrintPreviewArchive.paths("part.3mf"))
        assertEquals(listOf("/cache/part.3mf","/part.3mf"),PrintPreviewArchive.paths("file:///sdcard/cache/part.3mf"))
        assertTrue(PrintPreviewArchive.paths("part.gcode").isEmpty())
    }
    @Test fun unsafeAndExternalPathsAreRejected() {
        listOf("../part.3mf","part\r\nDELE x.3mf","https://other/file.3mf","..\\part.3mf").forEach {
            assertTrue(PrintPreviewArchive.paths(it).isEmpty())
        }
    }
    @Test fun neverSelectsWrongPlate() {
        val names=listOf("Metadata/plate_1.png","Metadata/plate_2.png")
        assertNull(PrintPreviewArchive.imageEntry(names,null))
        assertEquals(names[1],PrintPreviewArchive.imageEntry(names,2))
        assertNull(PrintPreviewArchive.imageEntry(names,3))
        assertEquals(names[0],PrintPreviewArchive.imageEntry(names.take(1),null))
    }
    @Test fun extractsOnlyMatchingPreview() {
        val file=File.createTempFile("preview-test", ".3mf")
        try {
            ZipOutputStream(file.outputStream()).use { out ->
                listOf(1,2).forEach { n -> out.putNextEntry(ZipEntry("Metadata/plate_$n.png"));out.write(byteArrayOf(n.toByte()));out.closeEntry() }
            }
            ZipFile(file).use { assertArrayEquals(byteArrayOf(2),PrintPreviewArchive.extract(it,2)) }
        } finally {file.delete()}
    }
}
