package com.example.fakenumber

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs: SharedPreferences = try {
            @Suppress("DEPRECATION")
            getSharedPreferences(Const.PREF_NAME, Context.MODE_WORLD_READABLE)
        } catch (e: SecurityException) {
            // 모듈 비활성 상태면 여기로 폴백됨 → 후킹쪽이 못 읽으니 모듈 ON 후 재저장 필요
            getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "01012341234 / 010-1234-1234 / +821012341234"
            setText(prefs.getString(Const.KEY_NUMBER, ""))
        }
        val save = Button(this).apply {
            text = "저장"
            setOnClickListener {
                prefs.edit().putString(Const.KEY_NUMBER, input.text.toString().trim()).apply()
                Toast.makeText(
                    this@SettingsActivity,
                    "저장됨 (대상 앱 다음 조회부터 반영)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        // 자식들을 가로 꽉 차게, 위아래 여백을 둔 공통 레이아웃 파라미터
        fun rowParams() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = 24
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL          // 화면 세로 가운데 정렬
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            addView(TextView(this@SettingsActivity).apply {
                text = "위조할 전화번호"
                textSize = 16f
            }, rowParams())
            addView(input, rowParams())
            addView(save, rowParams())
        }
        setContentView(root)
    }
}
