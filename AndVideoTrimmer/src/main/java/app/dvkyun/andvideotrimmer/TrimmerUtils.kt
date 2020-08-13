package app.dvkyun.andvideotrimmer

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

internal object TrimmerUtils {
    fun formatCMSeconds(timeInMSeconds: Long): String? {
        val hours = timeInMSeconds / 3600000
        val secondsLeft = timeInMSeconds - hours * 3600000
        val minutes = secondsLeft / 60000
        val seconds = secondsLeft - minutes * 60000
        val vSeconds = seconds / 1000
        val mSeconds = seconds % 1000
        var formattedTime = ""
        if (hours < 10) formattedTime += "0"
        formattedTime += "$hours:"
        if (minutes < 10) formattedTime += "0"
        formattedTime += "$minutes:"
        if (vSeconds < 10) formattedTime += "0"
        formattedTime += "$vSeconds."
        if (mSeconds < 100) formattedTime += "0"
        else if (mSeconds < 10) formattedTime += "00"
        formattedTime += mSeconds
        return formattedTime
    }

    fun getDuration(context: Activity?, videoPath: Uri?): Long {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoPath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val timeInMillisec = time!!.toLong()
            retriever.release()
            return timeInMillisec
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    fun getFileExtension(
        context: Context,
        uri: Uri
    ): String? {
        try {
            val extension: String?
            extension = if (uri.scheme != null && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                val mime = MimeTypeMap.getSingleton()
                mime.getExtensionFromMimeType(context.contentResolver.getType(uri))
            } else MimeTypeMap.getFileExtensionFromUrl(
                Uri.fromFile(File(uri.path)).toString()
            )
            return if (extension == null || extension.isEmpty()) ".mp4" else extension
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "mp4"
    }


    fun getFrameBySec(
        context: Activity?,
        videoPath: Uri?,
        millie: Long
    ): Bitmap? {
        try {
            val formatted = millie.toString() + "000000"
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoPath)
            val bitmap = retriever.getFrameAtTime(formatted.toLong())
            retriever.release()
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun formatSeconds(timeInSeconds: Long): String? {
        val hours = timeInSeconds / 3600
        val secondsLeft = timeInSeconds - hours * 3600
        val minutes = secondsLeft / 60
        val seconds = secondsLeft - minutes * 60
        var formattedTime = ""
        if (hours < 10 && hours != 0L) {
            formattedTime += "0"
            formattedTime += "$hours:"
        }
        if (minutes < 10) formattedTime += "0"
        formattedTime += "$minutes:"
        if (seconds < 10) formattedTime += "0"
        formattedTime += seconds
        return formattedTime
    }

    fun formatMSeconds(timeInMSeconds: Long): String? {
        val hours = timeInMSeconds / 3600000
        val secondsLeft = timeInMSeconds - hours * 3600000
        val minutes = secondsLeft / 60000
        val seconds = secondsLeft - minutes * 60000
        val vSeconds = seconds / 1000
        val mSeconds = seconds % 1000
        var formattedTime = ""
        if (hours < 10 && hours != 0L) {
            formattedTime += "0"
            formattedTime += "$hours:"
        }
        if (minutes < 10) formattedTime += "0"
        formattedTime += "$minutes:"
        if (vSeconds < 10) formattedTime += "0"
        formattedTime += "$vSeconds."
        formattedTime += mSeconds
        return formattedTime
    }

    fun getLimitedTimeFormatted(mses: Long): String? {
        val hours = mses / 3600000
        val secondsLeft = mses - hours * 3600000
        val minutes = secondsLeft / 60000
        val seconds = (secondsLeft - minutes * 60000) / 1000
        val time: String
        time = when {
            hours != 0L -> {
                hours.toString() + " Hrs " + (if (minutes != 0L) "$minutes Mins " else "") +
                        if (seconds != 0L) "$seconds Secs " else ""
            }
            minutes != 0L -> minutes.toString() + " Mins " + (if (seconds != 0L) "$seconds Secs " else "")
            else -> "$seconds Secs "
        }
        TrimmerLog.v(time)
        return time
    }
}