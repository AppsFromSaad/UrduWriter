package com.sf.urduwriter

import android.app.Application
import com.sf.urduwriter.FileUtils

class UrduWriterApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        FileUtils.getDocsDir(this)
        FileUtils.getUserFontsDir(this)

        // Pre-warm the heavy Jameel Noori Nastaliq font in the background to avoid main-thread blocking
        Thread {
            FontManager.getFont(this, "fonts/Jameel_noori_nastaleeq.ttf")
        }.start()
    }
}
