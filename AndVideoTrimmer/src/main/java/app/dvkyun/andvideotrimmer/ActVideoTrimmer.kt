package app.dvkyun.andvideotrimmer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.FFmpeg
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Player.DefaultEventListener
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.BandwidthMeter
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.act_video_trimmer.*
import kotlinx.android.synthetic.main.alert_convert.view.*
import kotlinx.android.synthetic.main.view_video_controller.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.Executors

open class ActVideoTrimmer: AppCompatActivity() {

    companion object {
        private const val PER_REQ_CODE = 115
        private val SCOPE by lazy {
            CoroutineScope(Executors.newFixedThreadPool(2).asCoroutineDispatcher())
        }
    }

    private val videoPlayer: SimpleExoPlayer by lazy {
        val bandwidthMeter: BandwidthMeter = DefaultBandwidthMeter()
        val trackSelector: TrackSelector = DefaultTrackSelector(AdaptiveTrackSelection.Factory(bandwidthMeter))
        ExoPlayerFactory.newSimpleInstance(this, trackSelector)
    }

    private lateinit var imageViews: Array<ImageView>
    private lateinit var uri: Uri

    private var totalDuration: Long = 0
    private var lastMinValue: Long = 0
    private var lastMaxValue: Long = 0

    private var menuDone: MenuItem? = null
    
    private var isValidVideo = true
    private var isVideoEnded = false

    private var currentDuration: Long = 0

    private var alertDialog: AlertDialog? = null

    private var destinationPath: String? = null

    private var trimType = 0
    private var fixedGap: Long = 0
    private var minGap:Long = 2000
    private var minFromGap:Long = 0
    private var maxToGap:Long = 0

    private var hidePlayerSeek = false

    private var isPlayProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_video_trimmer)
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(R.string.txt_empty)
        toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        imageViews = arrayOf(
            image_one, image_two, image_three,
            image_four, image_five, image_six, image_seven, image_eight
        )
        initPlayer()
        val uriStr = intent.getStringExtra(TrimmerConstants.TRIM_VIDEO_URI)
        if (uriStr == null) throw NullPointerException("VideoUri can't be null")
        else if (checkStoragePermission()) setDataInView()
    }

    private fun initPlayer() {
        try {
            player_view_lib.requestFocus()
            player_view_lib.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            player_view_lib.player = videoPlayer
            videoPlayer.playWhenReady = false
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun setDataInView() {
        try {
            uri = Uri.parse(intent.getStringExtra(TrimmerConstants.TRIM_VIDEO_URI))
            uri = Uri.parse(FileUtils.getPath(this, uri))
            TrimmerLog.v("VideoUri:: $uri")
            totalDuration = TrimmerUtils.getDuration(this, uri)
            TrimmerLog.v("total duration::$totalDuration")
            trimType = intent.getIntExtra(TrimmerConstants.TRIM_TYPE, 0)
            fixedGap = intent.getLongExtra(TrimmerConstants.FIXED_GAP_DURATION, totalDuration)
            minGap = intent.getLongExtra(TrimmerConstants.MIN_GAP_DURATION, totalDuration)
            minFromGap = intent.getLongExtra(TrimmerConstants.MIN_FROM_DURATION, totalDuration)
            maxToGap = intent.getLongExtra(TrimmerConstants.MAX_TO_DURATION, totalDuration)
            destinationPath = intent.getStringExtra(TrimmerConstants.DESTINATION)
            hidePlayerSeek = intent.getBooleanExtra(TrimmerConstants.HIDE_PLAYER_SEEKBAR, false)
            validate()
            image_play_pause.setOnClickListener { onVideoClicked() }
            player_view_lib.videoSurfaceView.setOnClickListener { onVideoClicked() }
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun validate() {
        require(!(trimType > 3 || trimType < 0)) { "Invalid trim type $trimType" }
        require(fixedGap > 0) { "Invalid fixedgap duration $fixedGap" }
        require(minGap > 0) { "Invalid mingap duration $minGap" }
        require(minFromGap > 0) { "Invalid minFromGap duration $minFromGap" }
        require(maxToGap > 0) { "Invalid maxToGap duration $maxToGap" }
        val desPath = destinationPath
        if (trimType == 3) {
            require(minFromGap != maxToGap) { "Min_from_duration and Max_to_duration are same..you could use Fixed gap" }
            require(minFromGap <= maxToGap) {
                "Min_from_duration must be smaller than Max_to_duration ${TrimmerConstants.MIN_FROM_DURATION}:$minFromGap ${TrimmerConstants.MAX_TO_DURATION}:$maxToGap"
            }
        } else if (desPath != null) {
            val outputDir = File(desPath)
            outputDir.mkdirs()
            destinationPath = outputDir.toString()
            require(outputDir.isDirectory) { "Destination file path error $destinationPath" }
        }
        buildMediaSource(uri)
    }

    private fun buildMediaSource(mUri: Uri?) {
        try {
            SCOPE.launch(Dispatchers.Main) { showLoadingDialog() }
            val bandwidthMeter = DefaultBandwidthMeter()
            val dataSourceFactory: DataSource.Factory =
                DefaultDataSourceFactory(
                    this,
                    Util.getUserAgent(
                        this,
                        getString(R.string.app_name)
                    ), bandwidthMeter
                )
            val videoSource: MediaSource = ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(mUri)
            videoPlayer.prepare(videoSource)
            videoPlayer.addListener(object : DefaultEventListener() {
                override fun onPlayerStateChanged(
                    playWhenReady: Boolean,
                    playbackState: Int
                ) {
                    SCOPE.launch(Dispatchers.Main) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> TrimmerLog.v("onPlayerStateChanged: Buffering video.")
                            Player.STATE_ENDED -> {
                                TrimmerLog.v("onPlayerStateChanged: Video ended.")
                                image_play_pause.visibility = View.VISIBLE
                                isVideoEnded = true
                            }
                            Player.STATE_IDLE -> TrimmerLog.v("onPlayerStateChanged: Player.STATE_IDLE")
                            Player.STATE_READY -> {
                                isVideoEnded = false
                                image_play_pause.visibility = if (videoPlayer.playWhenReady) View.GONE else View.VISIBLE
                                TrimmerLog.v("onPlayerStateChanged: Ready to play.")
                                if(!isPlayProgress) playProgress()
                            }
                            else -> { }
                        }
                    }
                }
            })
            setImageBitmaps()
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun playProgress() {
        SCOPE.launch {
            if (!videoPlayer.playWhenReady) return@launch
            isPlayProgress = true
            currentDuration = videoPlayer.currentPosition
            if (currentDuration <= lastMaxValue)
                seekbar_controller.setMinStartValue(currentDuration.toFloat()).apply()
            else {
                videoPlayer.playWhenReady = false
                isPlayProgress = false
            }
            delay(1)
            playProgress()
        }
    }

    private fun setImageBitmaps() {
        try {
            SCOPE.launch(Dispatchers.Main) {
                val diff = totalDuration / 8
                var index = 1
                for (img in imageViews) {
                    val bitmap: Bitmap? = withContext(SCOPE.coroutineContext) {
                        TrimmerUtils.getFrameBySec(
                            this@ActVideoTrimmer,
                            uri,
                            diff * index
                        )
                    }
                    img.setImageBitmap(bitmap)
                    index++
                }
                view_image.visibility = View.VISIBLE
                range_seek_bar.visibility = View.VISIBLE
                seekbar_controller.visibility = View.VISIBLE
                txt_start_duration.visibility = View.VISIBLE
                txt_end_duration.visibility = View.VISIBLE
                txt_start_duration.text = TrimmerUtils.formatMSeconds(0)
                txt_end_duration.text = TrimmerUtils.formatMSeconds(totalDuration)
                seekbar_controller.setMaxValue(totalDuration.toFloat()).apply()
                range_seek_bar.setMaxValue(totalDuration.toFloat()).apply()
                range_seek_bar.setMaxStartValue(totalDuration.toFloat()).apply()
                when (trimType) {
                    1 -> {
                        range_seek_bar.setFixGap(fixedGap.toFloat()).apply()
                        lastMaxValue = totalDuration
                    }
                    2 -> {
                        range_seek_bar.setMaxStartValue(minGap.toFloat())
                        range_seek_bar.setGap(minGap.toFloat()).apply()
                        lastMaxValue = totalDuration
                    }
                    3 -> {
                        range_seek_bar.setMaxStartValue(maxToGap.toFloat())
                        range_seek_bar.setGap(minFromGap.toFloat()).apply()
                        lastMaxValue = maxToGap
                    }
                    else -> {
                        range_seek_bar.setGap(minGap.toFloat())
                        lastMaxValue = totalDuration
                    }
                }
                if (hidePlayerSeek) seekbar_controller.visibility = View.GONE
                range_seek_bar.setOnRangeSeekbarChangeListener { minValue: Number, maxValue: Number ->
                    val minVal = minValue as Long
                    val maxVal = maxValue as Long
                    if (lastMinValue != minVal) seekTo(minValue)
                    lastMinValue = minVal
                    lastMaxValue = maxVal
                    txt_start_duration.text = TrimmerUtils.formatMSeconds(minVal)
                    txt_end_duration.text = TrimmerUtils.formatMSeconds(maxVal)
                    if (trimType == 3) setDoneColor(minVal, maxVal)
                }
                seekbar_controller.setOnSeekbarFinalValueListener { value ->
                    val value1 = value as Long
                    if (value1 in lastMinValue until lastMaxValue) {
                        seekTo(value1)
                    } else if (value1 > lastMaxValue)
                        seekbar_controller.setMinStartValue(lastMaxValue.toFloat()).apply()
                    else if (value1 < lastMinValue) {
                        seekbar_controller.setMinStartValue(lastMinValue.toFloat()).apply()
                        if (videoPlayer.playWhenReady) seekTo(lastMinValue)
                    }
                }
                alertDialog?.dismiss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    private fun onVideoClicked() {
        try {
            isPlayProgress = false
            if (isVideoEnded) {
                seekTo(lastMinValue)
                videoPlayer.playWhenReady = true
                return
            }
            if (currentDuration - lastMaxValue > 0) seekTo(lastMinValue)
            videoPlayer.playWhenReady = !videoPlayer.playWhenReady
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun seekTo(sec: Long) {
        videoPlayer.seekTo(sec)
    }

    private fun setDoneColor(minVal: Long, maxVal: Long) {
        try {
            if (maxVal - minVal <= maxToGap) {
                menuDone?.icon?.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(this, R.color.colorWhite)
                    , PorterDuff.Mode.SRC_IN
                )
                isValidVideo = true
            } else {
                menuDone?.icon?.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(this, R.color.colorWhiteLt)
                    , PorterDuff.Mode.SRC_IN
                )
                isValidVideo = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            finish()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PER_REQ_CODE) {
            if (isPermissionOk(*grantResults)) setDataInView() else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    override fun onPause() {
        super.onPause()
        videoPlayer.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        videoPlayer.release()
        alertDialog?.let { dialog ->
            if (dialog.isShowing) dialog.dismiss()
        }
        FFmpeg.cancel()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_done, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menuDone = menu.findItem(R.id.action_done)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_done) {
            validateVideo()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun validateVideo() {
        if (isValidVideo) {
            SCOPE.launch {
                val path = if(destinationPath != null) destinationPath else getExternalFilesDir(null)!!.absolutePath
//                val newFile = File("$path${File.separator}${System.currentTimeMillis()}.${TrimmerUtils.getFileExtension(this@ActVideoTrimmer, uri)}")
                val newFile = File("$path${File.separator}${System.currentTimeMillis()}.mp4")
                val outputPath = newFile.toString()
                TrimmerLog.v("outputPath::$outputPath")
                val complexCommand = arrayOf(
                    "-i", uri.toString(),
                    "-ss", TrimmerUtils.formatCMSeconds(lastMinValue),
                    "-to", TrimmerUtils.formatCMSeconds(lastMaxValue),
                    "-qscale", "0",
                    "-c:v", "mpeg4",
                    outputPath
                )
                videoPlayer.playWhenReady = false
                withContext(Dispatchers.Main) { showTrimmingDialog { FFmpeg.cancel() } }
                val rc = FFmpeg.execute(complexCommand)
                SCOPE.launch(Dispatchers.Main) {
                    alertDialog?.dismiss()
                    when(rc) {
                        RETURN_CODE_SUCCESS -> {
                            val intent = Intent()
                            intent.putExtra(TrimmerConstants.TRIMMED_VIDEO_PATH, outputPath)
                            setResult(Activity.RESULT_OK, intent)
                            finish()
                        }
                        RETURN_CODE_CANCEL -> {
                            TrimmerLog.e("Cancel by user")
                            Toast.makeText(
                                this@ActVideoTrimmer,
                                "Trimming canceled by user",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        else -> {
                            TrimmerLog.e("FFmpeg ERR Code -> $rc")
                            Toast.makeText(
                                this@ActVideoTrimmer,
                                "Trimming Err",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        } else
            Toast.makeText(
                this,
                "${getString(R.string.txt_smaller)} ${TrimmerUtils.getLimitedTimeFormatted(maxToGap)}",
                Toast.LENGTH_SHORT
            ).show()
    }

    private fun showTrimmingDialog(cancelEvent: () -> Unit) {
        try {
            val dialogBuilder = AlertDialog.Builder(this)
            val dialogView = this.layoutInflater.inflate(R.layout.alert_convert, null)
            dialogView.txt_cancel.setOnClickListener {
                alertDialog?.dismiss()
                cancelEvent()
            }
            dialogBuilder.setView(dialogView)
            dialogBuilder.setCancelable(false)
            alertDialog = dialogBuilder.create()
            alertDialog?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showLoadingDialog() {
        try {
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setView(R.layout.alert_loading)
            dialogBuilder.setCancelable(false)
            alertDialog = dialogBuilder.create()
            alertDialog?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
        } else checkPermission(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private fun checkPermission(vararg permissions: String): Boolean {
        var allPermitted = false
        for (permission in permissions) {
            allPermitted = (ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED)
            if (!allPermitted) break
        }
        if (allPermitted) return true
        ActivityCompat.requestPermissions(
            this, permissions,
            PER_REQ_CODE
        )
        return false
    }


    private fun isPermissionOk(vararg results: Int): Boolean {
        var isAllGranted = true
        for (result in results) {
            if (PackageManager.PERMISSION_GRANTED != result) {
                isAllGranted = false
                break
            }
        }
        return isAllGranted
    }

}