package com.sf.urduwriter

import android.content.Context
import java.io.File

object FileUtils {
    fun getDocsDir(context: Context): File {
        val docsDir = File(context.filesDir, "docs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }
        return docsDir
    }

    fun getUserFontsDir(context: Context): File {
        val fontsDir = File(context.filesDir, "user_fonts")
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }
        return fontsDir
    }
}
