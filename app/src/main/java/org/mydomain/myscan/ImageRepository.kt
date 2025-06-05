package org.mydomain.myscan

import java.io.File

const val SCAN_DIR_NAME = "scanned_pages"

class ImageRepository(appFilesDir: File) {

    private val scanDir: File = File(appFilesDir, SCAN_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    val fileNames = scanDir.listFiles()
        ?.map { f -> f.name }?.toMutableList()
        ?:mutableListOf()

    fun imageIds(): List<String> {
        return fileNames.toList()
    }

    fun add(bytes: ByteArray) {
        val fileName = "${System.currentTimeMillis()}.jpg"
        val file = File(scanDir, fileName)
        file.writeBytes(bytes)
        fileNames.add(fileName)
    }

    fun getContent(id: String): ByteArray {
        if (fileNames.contains(id)) {
            val file = File(scanDir, id)
            return file.readBytes()
        }
        throw IllegalArgumentException("No image for id: $id")
    }

    fun delete(id: String) {
        val file = File(scanDir, id)
        file.delete()
    }
}
