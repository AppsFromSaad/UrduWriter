package com.sf.urduwrier

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient

class HtmlPrintDocumentAdapter(private val context: Context, private val html: String, private val jobName: String) : PrintDocumentAdapter() {

    private var webView: WebView? = null

    override fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle
    ) {
        webView = WebView(context)
        webView?.settings?.javaScriptEnabled = true

        webView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return
                }

                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()

                callback.onLayoutFinished(info, true)
            }
        }

        webView?.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "utf-8", null)
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        webView?.let {
            it.createPrintDocumentAdapter(jobName).onWrite(pages, destination, cancellationSignal, callback)
        }
    }
}