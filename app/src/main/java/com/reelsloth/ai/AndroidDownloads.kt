package com.reelsloth.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri

/** [Downloads] backed by Android's DownloadManager. */
class AndroidDownloads(context: Context) : Downloads {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)

    override fun enqueue(request: DownloadRequest): Long {
        val root = requireNotNull(appContext.getExternalFilesDir(null)) { "App storage is not available." }
        val subPath = request.destination.relativeTo(root).path
        val systemRequest = DownloadManager.Request(Uri.parse(request.url))
            .setTitle(request.title)
            .setDescription(request.description)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(appContext, null, subPath)
            .setAllowedOverMetered(request.allowMetered)
            .setAllowedOverRoaming(false)
        return manager.enqueue(systemRequest)
    }

    override fun query(id: Long): DownloadInfo? =
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            DownloadInfo(
                status = when (status) {
                    DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
                    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
                    else -> DownloadStatus.FAILED
                },
                bytesSoFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                totalBytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                waitingFor = if (status != DownloadManager.STATUS_PAUSED) null else when (reason) {
                    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> Waiting.WIFI
                    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> Waiting.NETWORK
                    DownloadManager.PAUSED_WAITING_TO_RETRY -> Waiting.RETRY
                    else -> Waiting.OTHER
                },
            )
        }

    override fun remove(id: Long) {
        manager.remove(id)
    }
}
