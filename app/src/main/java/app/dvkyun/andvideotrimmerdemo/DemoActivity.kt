package app.dvkyun.andvideotrimmerdemo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import app.dvkyun.andvideotrimmer.ActVideoTrimmer
import app.dvkyun.andvideotrimmer.TrimmerConstants
import kotlinx.android.synthetic.main.activity_demo.*
import java.io.File

class DemoActivity: AppCompatActivity() {

    companion object {
        private const val GALLERY_PICK = 300
        private const val VIDEO_TRIM = 301
        private val permissionList = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        ActivityCompat.requestPermissions(
            this,
            permissionList,
            0
        )

        btn_start.setOnClickListener {
            val intent = Intent()
            intent.action = Intent.ACTION_PICK
            intent.type= "video/*"
            startActivityForResult(intent, GALLERY_PICK)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == GALLERY_PICK && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            val intent = Intent(this, ActVideoTrimmer::class.java)
            intent.putExtra(TrimmerConstants.TRIM_VIDEO_URI, data.data!!.toString())
            intent.putExtra(
                TrimmerConstants.DESTINATION,
                "/storage/emulated/0/DCIM/Camera"
            )
            startActivityForResult(intent, VIDEO_TRIM)
        } else if(requestCode == VIDEO_TRIM && resultCode == Activity.RESULT_OK && data != null) {
            val uri: Uri = Uri.fromFile(File(data.getStringExtra(TrimmerConstants.TRIMMED_VIDEO_PATH)))
            Log.v("DemoActivity", uri.toString())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        for(res in grantResults) {
            if(res != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    permissionList,
                    0
                )
                return
            }
        }
    }

}