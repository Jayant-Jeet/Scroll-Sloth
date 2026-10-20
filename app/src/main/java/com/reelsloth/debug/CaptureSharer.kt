package com.reelsloth.debug

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

/** Hands capture files to the system share sheet; nothing leaves the device unless the user picks a target. */
class CaptureSharer(
    private val context: Context,
    private val repository: CaptureRepository,
) {
    fun share(captures: List<CaptureSummary>) {
        val uris = captures.mapNotNull { repository.fileFor(it.fileName) }
            .map { FileProvider.getUriForFile(context, authority(context), it) }
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
        }.apply {
            type = "application/json"
            clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share captures"))
    }

    companion object {
        fun authority(context: Context) = "${context.packageName}.inspector"
    }
}
