package com.linqinen708.qrcode

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.view.View
import android.widget.Toast
import com.google.zxing.WriterException
import com.linqinen708.zxing.activity.CaptureActivity
import com.linqinen708.zxing.constants.ExtraConstants
import com.linqinen708.zxing.utils.LogT
import com.linqinen708.zxing.utils.QRCodeEncoder
import kotlinx.android.synthetic.main.activity_main.*

/**
 * ？：表示当前是否对象可以为空
 *！！： 表示当前对象不为空的情况下执行
 * b?.length“？.”操作符调用变量，其含义是如果b不是null,这个表达式将会返回b.length
 * */
@SuppressLint("SetTextI18n")
class MainActivity : BaseActivity(), View.OnClickListener {


    private val REQUEST_CODE = 1000
    private val REQUEST_CAMERA_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_confirm.setOnClickListener(this)
        btn_hide.setOnClickListener(this)

//        val intent = null
//        intent?.let { test(it) }
    }

//    fun test(intent: Intent){
//        LogT.i("intent:"+intent)
//        LogT.i("111"+intent.getBooleanExtra("aaa",false))
//    }

//    //扫码结果

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        LogT.i("resultCode:$resultCode,data:$data")

        if (resultCode == RESULT_OK) { //RESULT_OK = -1
            LogT.i("二维码内容" + data?.getStringExtra(ExtraConstants.RAW_RESULT_TEXT))
            tv_content.text = "二维码内容:\n" + data?.getStringExtra(ExtraConstants.RAW_RESULT_TEXT)
//            val scanResult = data?.extras?.getString("result")
//            Toast.makeText(this, scanResult, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 配置Android 6.0 以上额外的权限
     */
    private fun initPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            val mPermissionList = arrayOf(
                    Manifest.permission.CAMERA//相机
            )
            if (checkPermissionAllGranted(mPermissionList)) {
                //扫码
                scanQRCode()
            } else {
                ActivityCompat.requestPermissions(this, mPermissionList, REQUEST_CAMERA_PERMISSION)
            }
        } else {
            //扫码
            scanQRCode()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && checkGrantResultsAllGranted(grantResults)) {
            //扫码
            scanQRCode()

        } else {
            AlertDialog.Builder(this)
                    .setMessage("未获得权限，无法获得扫码功能")
                    .setPositiveButton("设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }.setNegativeButton("取消", null)
                    .show()
        }
    }

    //扫码
    private fun scanQRCode() {
        val intent = Intent(this, CaptureActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE)
    }

    //生成二维码
    private fun createQRCode(content: String) = try {
        val mBitmap = QRCodeEncoder.encodeAsBitmap(content, 250)
        iv_qr_code.setImageBitmap(mBitmap)
    } catch (e: WriterException) {
        e.printStackTrace()
    }

    override fun onClick(v: View?) {
        when (v?.id) {

            R.id.btn_confirm -> {
                if (et_content.text == null || et_content.text.isEmpty()) {
                    Toast.makeText(baseContext, "请输入内容", Toast.LENGTH_SHORT).show()
                } else {
                    //生成二维码
                    createQRCode(et_content.text?.toString()!!)
                }

            }
            R.id.btn_hide -> {
                if (et_content.visibility == View.VISIBLE) {
                    et_content.visibility = View.GONE
                    btn_hide.text = "显示"
                } else {
                    et_content.visibility = View.VISIBLE
                    btn_hide.text = "隐藏"
                }
            }
            R.id.btn_scan -> {
                initPermissions()
            }
        }
    }
}
