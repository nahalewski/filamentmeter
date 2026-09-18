package com.ben.filamentmeter.bambu

import java.util.zip.ZipFile

object PrintPreviewArchive {
    fun paths(reportedFile: String): List<String> {
        val value=reportedFile.trim().removePrefix("file:///sdcard/").removePrefix("/sdcard/")
        if(value.any { it=='\r' || it=='\n' || it=='\u0000' } || value.split('/').any { it==".." } ||
            !value.endsWith(".3mf",true) || value.contains(":") || value.contains('\\')) return emptyList()
        val file=value.substringAfterLast('/')
        return listOf("/"+value.trimStart('/'),"/cache/$file","/$file").distinct()
    }
    fun imageEntry(names: List<String>, plate: Int?): String? {
        // Never guess another plate in a multi-plate archive.
        val plates=names.filter { Regex("Metadata/plate_[0-9]+\\.png").matches(it) }
        if(plate!=null) return "Metadata/plate_$plate.png".takeIf { it in names }
        return plates.singleOrNull()
    }
    fun extract(zip: ZipFile, plate: Int?): ByteArray {
        val name=imageEntry(zip.entries().asSequence().map { it.name }.toList(),plate)
            ?: error("No unambiguous preview for the active plate")
        val entry=zip.getEntry(name)
        require(entry.size in 1..4_194_304) { "Preview image is too large" }
        return zip.getInputStream(entry).use { input ->
            val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(true) { val count=input.read(buffer); if(count<0) break
                require(out.size()+count<=4_194_304) { "Preview image is too large" };out.write(buffer,0,count) }
            out.toByteArray()
        }
    }
}
