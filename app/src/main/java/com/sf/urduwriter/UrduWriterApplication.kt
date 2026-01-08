package com.sf.urduwriter

import android.app.Application
import com.sf.urduwriter.FileUtils

class UrduWriterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FileUtils.getDocsDir(this)
        FileUtils.getUserFontsDir(this)
    }
}
