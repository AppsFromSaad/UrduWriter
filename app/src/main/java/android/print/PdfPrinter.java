package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;

public class PdfPrinter {

    public interface Callback {
        void onSuccess();

        void onFailure(String error);
    }

    public static void print(
            final PrintDocumentAdapter adapter,
            final PrintAttributes attributes,
            final ParcelFileDescriptor pfd,
            final Callback callback
    ) {
        adapter.onLayout(null, attributes, new CancellationSignal(), new LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, new CancellationSignal(), new WriteResultCallback() {
                    @Override
                    public void onWriteFinished(PageRange[] pages) {
                        callback.onSuccess();
                    }

                    @Override
                    public void onWriteFailed(CharSequence error) {
                        callback.onFailure(error != null ? error.toString() : "Unknown write error");
                    }

                    @Override
                    public void onWriteCancelled() {
                        callback.onFailure("Write cancelled");
                    }
                });
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                callback.onFailure(error != null ? error.toString() : "Unknown layout error");
            }

            @Override
            public void onLayoutCancelled() {
                callback.onFailure("Layout cancelled");
            }
        }, null);
    }
}
