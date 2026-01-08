package com.sf.urduwriter

import android.content.Context
import com.sf.urduwriter.FileUtils
import java.io.File
import java.io.FilenameFilter

object FontManager {

    fun getBuiltInFonts(context: Context): List<String> {
        return try {
            context.assets.list("fonts")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getUserAddedFonts(context: Context): List<String> {
        val userFontsDir = FileUtils.getUserFontsDir(context)
        val filter = FilenameFilter { _, name -> name.endsWith(".ttf") || name.endsWith(".otf") }
        return userFontsDir.list(filter)?.toList() ?: emptyList()
    }

    fun addFont(context: Context, fontName: String, fontBytes: ByteArray) {
        val userFontsDir = FileUtils.getUserFontsDir(context)
        val fontFile = File(userFontsDir, fontName)
        fontFile.writeBytes(fontBytes)
    }

    fun deleteFont(context: Context, fontName: String): Boolean {
        val userFontsDir = FileUtils.getUserFontsDir(context)
        val fontFile = File(userFontsDir, fontName)
        return fontFile.delete()
    }
}