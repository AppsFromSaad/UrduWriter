package com.sf.urduwrier

import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import androidx.annotation.RequiresApi

object PdfExportUtility {

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun exportToPdf(context: Context, printAdapter: PrintDocumentAdapter, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager

        val printAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, printAdapter, printAttributes)
    }
}
