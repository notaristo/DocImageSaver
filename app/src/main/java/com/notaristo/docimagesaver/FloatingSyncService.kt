package com.notaristo.docimagesaver

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable

class FloatingSyncService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val label = TextView(this).apply {
            text = "D"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(151, 117, 255), Color.rgb(57, 205, 224))
            ).apply { shape = GradientDrawable.OVAL; setStroke(2, Color.argb(170, 255, 255, 255)) }
            elevation = 12f
            contentDescription = "Open DocImageSaver sync"
        }
        val params = WindowManager.LayoutParams(
            58, 58,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 18
            y = 260
        }
        label.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x; startY = params.y
                        touchX = event.rawX; touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) moved = true
                        params.x = (startX - dx).coerceAtLeast(0)
                        params.y = (startY + dy).coerceAtLeast(0)
                        windowManager.updateViewLayout(view, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val open = Intent(this@FloatingSyncService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                putExtra(MainActivity.EXTRA_SYNC_NOW, true)
                            }
                            startActivity(open)
                        }
                        return true
                    }
                }
                return false
            }
        })
        bubble = label
        windowManager.addView(label, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        bubble?.let { windowManager.removeView(it) }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.notaristo.docimagesaver.STOP_FLOATING"
    }
}
