package com.antigravity.dualcam

import android.app.Application
import android.util.Log

class DualCamApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DualCam Pro Application initialized on Android SDK ${android.os.Build.VERSION.SDK_INT}")
    }

    companion object {
        const val TAG = "DualCamPro"
    }
}
