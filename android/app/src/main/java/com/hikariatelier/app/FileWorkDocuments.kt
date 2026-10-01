package com.hikariatelier.app

import java.io.File
import java.io.FileOutputStream

/** Split store names are validated before reaching this adapter; require a single filename too. */
internal class FileWorkDocuments(private val root: File) : WorkDocuments {
    private fun file(name: String): File {
        require(name.isNotEmpty() && '/' !in name && '\\' !in name && name != "." && name != "..")
        return File(root, name)
    }
    override fun exists(name: String) = file(name).exists()
    override fun read(name: String): String? = file(name).let { if (it.exists()) it.readText() else null }
    override fun write(name: String, content: String): Boolean {
        check(root.isDirectory || root.mkdirs())
        FileOutputStream(file(name)).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        return true
    }
    override fun rename(from: String, to: String): Boolean = !file(to).exists() && file(from).renameTo(file(to))
    override fun delete(name: String) = file(name).delete()
}
