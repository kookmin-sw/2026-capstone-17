package com.kmu_focus.focusandroid

import android.app.Application
import android.util.Log
import com.kakao.sdk.common.KakaoSdk
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

@HiltAndroidApp
class FocusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        KakaoSdk.init(this, BuildConfig.kakaoNativeStringAppKey)
        // OpenCV 네이티브 라이브러리 로드 — FaceDetectorYN 등 사용 전 필수
        if (OpenCVLoader.initLocal()) {
            Log.d("FocusApp", "OpenCV 초기화 성공")
        } else {
            Log.e("FocusApp", "OpenCV 초기화 실패")
        }
    }
}
