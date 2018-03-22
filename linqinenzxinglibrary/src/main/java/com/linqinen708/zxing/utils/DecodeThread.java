/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.linqinen708.zxing.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;
import com.linqinen708.zxing.R;
import com.linqinen708.zxing.activity.CaptureActivity;
import com.linqinen708.zxing.activity.PreferencesActivity;
import com.linqinen708.zxing.constants.ExtraConstants;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 这个线程完成了对图像解码的所有繁重工作
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class DecodeThread extends Thread {

    public static final String BARCODE_BITMAP = "barcode_bitmap";
    public static final String BARCODE_SCALED_FACTOR = "barcode_scaled_factor";

    private static final String PREVIEW = "PREVIEW";
    private static final String SUCCESS = "SUCCESS";
    private static final String DONE = "DONE";

    private String state = SUCCESS;

    private final CaptureActivity activity;
    private final Map<DecodeHintType, Object> hints;
    private Handler handler;


    private final CountDownLatch handlerInitLatch;

    private final MultiFormatReader multiFormatReader = new MultiFormatReader();
    private boolean running = true;

    public DecodeThread(CaptureActivity activity,
                 Collection<BarcodeFormat> decodeFormats,
                 Map<DecodeHintType, ?> baseHints,
                 String characterSet,
                 ResultPointCallback resultPointCallback) {

        this.activity = activity;
        handlerInitLatch = new CountDownLatch(1);

        hints = new EnumMap<>(DecodeHintType.class);
        if (baseHints != null) {
            hints.putAll(baseHints);
        }

        // The prefs can't change while the thread is running, so pick them up once here.
        if (decodeFormats == null || decodeFormats.isEmpty()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
            decodeFormats = EnumSet.noneOf(BarcodeFormat.class);
            if (prefs.getBoolean(PreferencesActivity.KEY_DECODE_1D_PRODUCT, true)) {
                decodeFormats.addAll(DecodeFormatManager.PRODUCT_FORMATS);
            }
            if (prefs.getBoolean(PreferencesActivity.KEY_DECODE_1D_INDUSTRIAL, true)) {
                decodeFormats.addAll(DecodeFormatManager.INDUSTRIAL_FORMATS);
            }
            if (prefs.getBoolean(PreferencesActivity.KEY_DECODE_QR, true)) {
                decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS);
            }
            if (prefs.getBoolean(PreferencesActivity.KEY_DECODE_DATA_MATRIX, true)) {
                decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS);
            }
            if (prefs.getBoolean(PreferencesActivity.KEY_DECODE_AZTEC, false)) {
                decodeFormats.addAll(DecodeFormatManager.AZTEC_FORMATS);
            }
            if (prefs.getBoolean(PreferencesActivity.KEY_DECODE_PDF417, false)) {
                decodeFormats.addAll(DecodeFormatManager.PDF417_FORMATS);
            }
        }
        hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);

        if (characterSet != null) {
            hints.put(DecodeHintType.CHARACTER_SET, characterSet);
        }
        hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
        LogT.i("Hints: " + hints);
    }

    public Handler getHandler() {
        try {
            handlerInitLatch.await();
        } catch (InterruptedException ie) {
            LogT.e("ie:" + ie);
            // continue?
        }
        return handler;
    }

    @Override
    public void run() {
        Looper.prepare();
        final Looper threadLooper = Looper.myLooper();
        handler = new Handler(threadLooper){
            @Override
            public void handleMessage(Message message) {
//               LogT.i("message:" +message + ", running:"  + running);
                if (message == null || !running) {
                    return;
                }
                if (message.what == R.id.decode) {
                    decode((byte[]) message.obj, message.arg1, message.arg2);

                } else if (message.what == R.id.quit) {
                    running = false;
                    // Be absolutely sure we don't send any queued up messages
                    removeMessages(R.id.decode_succeeded);
                    removeMessages(R.id.decode_failed);
                    if (threadLooper != null) {
                        LogT.i("退出线程消息队列");
                        threadLooper.quit();
                    }

                }
            }
        };
        multiFormatReader.setHints(hints);
        handlerInitLatch.countDown();
        LogT.i("分线程开始消息循环");
        Looper.loop();
    }

    /**重新预览和解码*/
    public void restartPreviewAndDecode() {
            LogT.i("开始预览和解码");
            if(activity.getCameraManager() != null){
//                LogT.i("requestPreviewFrame，getHandler():" + getHandler());
                activity.getCameraManager().requestPreviewFrame(getHandler(), R.id.decode);
            }
            activity.drawViewfinder();
    }


    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
     * reuse the same reader objects from one decode to the next.
     * 在取景器中对数据进行解码，并计算出它花了多长时间。为了提高效率,
     * 将相同的阅读器对象从一个解码中重用到下一个
     *
     * @param data   The YUV preview frame.
     * @param width  The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) {
        long start = System.currentTimeMillis();
        Result rawResult = null;
        PlanarYUVLuminanceSource source = activity.getCameraManager().buildLuminanceSource(data, width, height);
        if (source != null) {
            /*目测应该是从屏幕上捕获相关的数据生成相对于的图片，然后内部扫码解析图片*/
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
//            LogT.i("bitmap:"+bitmap);
            try {
                /*解析成相对应的对象*/
                rawResult = multiFormatReader.decodeWithState(bitmap);
            } catch (ReaderException re) {
//                LogT.e("re:" + re);
                // continue
            } finally {
                multiFormatReader.reset();
            }
        }

        if (rawResult != null) {
//            // Don't log the barcode contents for security.
            long end = System.currentTimeMillis();
            LogT.d("找到二维码Found barcode in " + (end - start) + " ms" + ",text内容：" + rawResult.getText());
            LogT.i("返回原界面，并触发onActivityResult");
            Intent intent = new Intent();
            intent.putExtra(ExtraConstants.RAW_RESULT_TEXT, rawResult.getText());
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
//            if (handler != null) {
//                Message message = Message.obtain(handler, R.id.decode_succeeded, rawResult);
//                Bundle bundle = new Bundle();
//                bundleThumbnail(source, bundle);
//                message.setData(bundle);
//                message.sendToTarget();
//            }
        } else {
//            LogT.i("没找到二维码");
            if(activity.getCameraManager() != null){
//                LogT.i("requestPreviewFrame，getHandler():" + getHandler());
                activity.getCameraManager().requestPreviewFrame(getHandler(), R.id.decode);
            }
        }
    }


}
