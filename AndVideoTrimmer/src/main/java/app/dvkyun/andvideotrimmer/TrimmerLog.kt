package app.dvkyun.andvideotrimmer

import android.util.Log

internal object TrimmerLog {

    const val IS_LOG = true

    private const val TAG = "VIDEO_TRIMMER ::"

    fun v(msg: String) {
        if (IS_LOG) Log.v(TAG, msg)
    }

    fun e(msg: String) {
        if (IS_LOG) Log.e(TAG, msg)
    }
}