package com.linqinen708.zxing.activity

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Message
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.WindowManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.ResultPointCallback
import com.linqinen708.zxing.R
import com.linqinen708.zxing.camera.CameraManager
import com.linqinen708.zxing.utils.*
import kotlinx.android.synthetic.main.activity_capture.*
import java.io.IOException

class CaptureActivity : AppCompatActivity(), SurfaceHolder.Callback {


//    private var source: IntentSource? = null
    private var inactivityTimer: InactivityTimer? = null
    private var beepManager: BeepManager? = null
    private var ambientLightManager: AmbientLightManager? = null
    private var cameraManager: CameraManager? = null
//    private var handler: CaptureActivityHandler? = null

    private var hasSurface: Boolean = false

    private var decodeFormats: Collection<BarcodeFormat>? = null
    private var decodeHints: Map<DecodeHintType, *>? = null

    private var characterSet: String? = null
    private var decodeThread: DecodeThread? = null

    fun getCameraManager(): CameraManager? {
        return cameraManager
    }

    fun drawViewfinder() {
        mViewFinderView.drawViewfinder()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window = window
        /*屏幕常亮*/
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_capture)

        /*当设备用电（屏幕常亮）时，一段时间静止时，关闭activity*/
        inactivityTimer = InactivityTimer(this)
        /*管理“嘟嘟嘟”声音和震动*/
        beepManager = BeepManager(this)
        /*在黑暗情况下，在前灯上检测周围的光和开关，当有足够的光时，就会再次关闭*/
        ambientLightManager = AmbientLightManager(this)
    }

    override fun onResume() {
        super.onResume()

        // CameraManager must be initialized here, not in onCreate(). This is necessary because we don't
        // want to open the camera driver and measure the screen size if we're going to show the help on
        // first launch. That led to bugs where the scanning rectangle was the wrong size and partially
        // off screen.
        cameraManager = CameraManager(application)

        mViewFinderView.setCameraManager(cameraManager)

        beepManager?.updatePrefs()
        ambientLightManager?.start(cameraManager)

        inactivityTimer?.onResume()
        if (hasSurface) {
            /**The activity was paused but not stopped, so the surface still exists. Therefore
             *surfaceCreated() won't be called, so init the camera here.
             *当activity was paused but not stopped，SurfaceView仍然存在，只需要初始化就行
             */
            initCamera(mSurfaceView.holder)
        } else {
            // Install the callback and wait for surfaceCreated() to init the camera.
            mSurfaceView.holder.addCallback(this)
        }
    }

    override fun onPause() {
        quitSynchronously()
        inactivityTimer?.onPause()
        ambientLightManager?.stop()
        beepManager?.close()
        cameraManager?.closeDriver()
        //historyManager = null; // Keep for onActivityResult
        if (!hasSurface) {
            mSurfaceView.holder.removeCallback(this)
        }
        super.onPause()
    }

    override fun onDestroy() {
        inactivityTimer?.shutdown()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        LogT.i("keyCode：$keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                    return true

            }
            KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA ->
                // Handle these events so they don't launch the Camera app
                return true
        // Use volume up/down to turn on light
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                cameraManager?.setTorch(false)
                return true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                cameraManager?.setTorch(true)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun initCamera(surfaceHolder: SurfaceHolder?) {
        if (surfaceHolder == null) {
            throw IllegalStateException("No SurfaceHolder provided")
        }
        if (cameraManager!!.isOpen) {
            LogT.w("initCamera() while already open -- late SurfaceView callback?")
            return
        }
        try {
            cameraManager?.openDriver(surfaceHolder)
            /**Creating the handler starts the preview, which can also throw a RuntimeException
             * 预览界面的处理机制，（这个因为是个handler，所以要处理很多信息，比如成功扫码后的回调等）
             * 开始预览，预览功能中，开启了一个线程DecodeThread
             * */
//            LogT.i("decodeThread:$decodeThread")
            if (decodeThread == null) {
                /*开启二维码的处理线程*/
                decodeThread = DecodeThread(this, decodeFormats, decodeHints, characterSet,
                        ResultPointCallback { point ->
                            //LogT.i("触发坐标点point:" + point.toString());
                            /**这个回调应该是从屏幕里获得相对于的二维码的坐标点
                             * 然后判断是否有对应的二维码信息
                             * */
                            mViewFinderView.addPossibleResultPoint(point)
                        }
                )

                decodeThread?.start()
            }
            /**打开照相机的预览功能，startPreview和restartPreviewAndDecode先后顺序不能改变*/
            cameraManager?.startPreview()
            decodeThread?.restartPreviewAndDecode()

        } catch (ioe: IOException) {
            LogT.w(ioe)
            displayFrameworkBugMessageAndExit()
        } catch (e: RuntimeException) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            LogT.w("Unexpected error initializing camera", e)
            displayFrameworkBugMessageAndExit()
        }

    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        // do nothing
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        hasSurface = false
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        if (holder == null) {
            LogT.w("*** WARNING *** surfaceCreated() gave us a null surface!")
        }
        if (!hasSurface) {
            hasSurface = true
            initCamera(holder)
        }
    }

    /**很遗憾，Android 相机出现问题。你可能需要重启设备*/
    private fun displayFrameworkBugMessageAndExit() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.app_name))
        builder.setMessage(getString(R.string.msg_camera_framework_bug))
        builder.setPositiveButton(R.string.button_ok, { _, _ -> finish() })
        builder.setOnCancelListener({ finish() })
        builder.show()
    }



    private fun quitSynchronously() {
        LogT.i("暂停预览")
        cameraManager?.stopPreview()
        val quit = Message.obtain(decodeThread?.handler, R.id.quit)
        quit.sendToTarget()
        try {
            // Wait at most half a second; should be enough time, and onPause() will timeout quickly
            decodeThread?.join(500L)
        } catch (e: InterruptedException) {
            LogT.e("e:$e")
            // continue
        }

    }


}
