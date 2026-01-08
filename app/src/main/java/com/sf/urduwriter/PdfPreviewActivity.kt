package com.sf.urduwriter

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.appcompat.app.AppCompatActivity
import com.sf.urduwriter.databinding.ActivityPdfPreviewBinding
import java.io.File

class PdfPreviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pdfPath = intent.getStringExtra("pdf_path")
        if (pdfPath != null) {
            val pdfFile = File(pdfPath)
            if (pdfFile.exists()) {
                renderPdf(pdfFile)
            }
        }
    }

    private fun renderPdf(pdfFile: File) {
        try {
            val parcelFileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)

            if (pdfRenderer.pageCount > 0) {
                val page = pdfRenderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                binding.pdfImageView.setImageBitmap(bitmap)
                page.close()
            }

            pdfRenderer.close()
            parcelFileDescriptor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
