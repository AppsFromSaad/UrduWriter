package com.sf.urduwriter

import android.content.Context
import android.graphics.Typeface
import com.sf.urduwriter.FileUtils
import java.io.File
import java.io.FilenameFilter
import java.util.concurrent.ConcurrentHashMap

object FontManager {

    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    fun getFont(context: Context, fontPathOrName: String): Typeface? {
        if (typefaceCache.containsKey(fontPathOrName)) {
            return typefaceCache[fontPathOrName]
        }
        return try {
            val typeface = if (fontPathOrName.startsWith("fonts/")) {
                Typeface.createFromAsset(context.assets, fontPathOrName)
            } else {
                val userFontsDir = FileUtils.getUserFontsDir(context)
                val fontFile = File(userFontsDir, fontPathOrName)
                if (fontFile.exists()) {
                    Typeface.createFromFile(fontFile)
                } else {
                    Typeface.createFromAsset(context.assets, "fonts/$fontPathOrName")
                }
            }
            if (typeface != null) {
                typefaceCache[fontPathOrName] = typeface
            }
            typeface
        } catch (e: Exception) {
            null
        }
    }

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