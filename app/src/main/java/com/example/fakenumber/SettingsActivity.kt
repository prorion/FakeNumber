package com.example.fakenumber

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var listContainer: LinearLayout   // 앱별 행이 들어가는 컨테이너
    private var moduleActive = false                    // WORLD_READABLE 획득 = 모듈 ON

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = try {
            @Suppress("DEPRECATION")
            val p = getSharedPreferences(Const.PREF_NAME, Context.MODE_WORLD_READABLE)
            moduleActive = true
            p
        } catch (e: SecurityException) {
            // 모듈 비활성 → 여기 쓰면 후킹쪽이 못 읽음. 표시용으로만 열고 저장은 막는다.
            getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // 진입 시 EditText가 포커스를 가로채 키보드가 뜨고 화면이 밀리는 것 방지
            isFocusableInTouchMode = true
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        // --- 모듈 비활성 경고 ---
        if (!moduleActive) {
            root.addView(TextView(this).apply {
                text = "⚠ 모듈이 비활성 상태입니다.\nLSPosed에서 FakeNumber를 켠 뒤 이 앱을 다시 열어야 저장됩니다."
                setTextColor(Color.RED)
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }, rowParams())
        }

        // --- 기본 번호 ---
        root.addView(label("기본 번호 (앱별 설정 없는 앱에 적용)"))
        val defaultInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "01012341234 / 010-1234-1234 / +821012341234"
            setText(prefs.getString(Const.KEY_NUMBER, ""))
        }
        root.addView(defaultInput, rowParams())
        root.addView(Button(this).apply {
            text = "기본 번호 저장"
            setOnClickListener {
                if (!guardSave()) return@setOnClickListener
                prefs.edit().putString(Const.KEY_NUMBER, defaultInput.text.toString().trim()).apply()
                toast("기본 번호 저장됨")
            }
        }, rowParams())

        root.addView(divider())

        // --- 앱별 설정 헤더 + 추가 버튼 ---
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("앱별 설정").apply {
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        header.addView(Button(this).apply {
            text = "+ 추가"
            setOnClickListener { showAppPicker() }
        })
        root.addView(header, rowParams())

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer, rowParams())

        setContentView(ScrollView(this).apply { addView(root) })
        refreshList()
    }

    /** prefs에서 num_* 키들을 읽어 앱별 행을 다시 그린다 */
    private fun refreshList() {
        listContainer.removeAllViews()
        val pm = packageManager
        val pkgs = prefs.all.keys
            .filter { it.startsWith(Const.KEY_APP_PREFIX) }
            .map { it.removePrefix(Const.KEY_APP_PREFIX) }
            .sorted()

        if (pkgs.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "설정된 앱이 없습니다. [+ 추가]로 등록하세요."
                setTextColor(Color.GRAY)
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        for (pkg in pkgs) {
            val number = prefs.getString(Const.KEY_APP_PREFIX + pkg, "").orEmpty()
            listContainer.addView(appRow(pkg, number, pm))
        }
    }

    /** 앱별 한 줄: [아이콘] [이름/번호] [수정][삭제] */
    private fun appRow(pkg: String, number: String, pm: PackageManager): View {
        val appLabel = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg   // 삭제된 앱이면 패키지명 그대로
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(ImageView(this).apply {
            try { setImageDrawable(pm.getApplicationIcon(pkg)) } catch (_: Exception) {}
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) }
        })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(TextView(this@SettingsActivity).apply { text = appLabel; textSize = 15f })
            addView(TextView(this@SettingsActivity).apply {
                text = number; textSize = 13f; setTextColor(Color.GRAY)
            })
        })
        row.addView(Button(this).apply {
            text = "수정"
            setOnClickListener { showNumberDialog(pkg, appLabel) }
        })
        row.addView(Button(this).apply {
            text = "삭제"
            setOnClickListener {
                if (!guardSave()) return@setOnClickListener
                prefs.edit().remove(Const.KEY_APP_PREFIX + pkg).apply()
                refreshList()
            }
        })
        return row
    }

    /** 설치된 앱 목록 다이얼로그 — 사용자 앱 / 시스템 앱 탭으로 구분 */
    private fun showAppPicker() {
        val pm = packageManager
        @Suppress("DEPRECATION")
        val all = pm.getInstalledApplications(0)
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        val systemMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        val userApps = all.filter { (it.flags and systemMask) == 0 }
        val systemApps = all.filter { (it.flags and systemMask) != 0 }

        var shown = userApps   // 기본 탭: 사용자 앱

        val listView = ListView(this)
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convertView: View?, parent: ViewGroup): View {
                val info = shown[p]
                val v = convertView as? LinearLayout ?: LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    addView(ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(14) }
                    })
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(context).apply { textSize = 15f })
                        addView(TextView(context).apply { textSize = 12f; setTextColor(Color.GRAY) })
                    })
                }
                val iconV = v.getChildAt(0) as ImageView
                val textBlock = v.getChildAt(1) as LinearLayout
                iconV.setImageDrawable(info.loadIcon(pm))
                (textBlock.getChildAt(0) as TextView).text = info.loadLabel(pm).toString()
                (textBlock.getChildAt(1) as TextView).text = info.packageName
                return v
            }
        }
        listView.adapter = adapter

        // --- 탭 버튼 (활성 탭은 비활성화 처리로 강조) ---
        val tabUser = Button(this).apply { text = "사용자 앱 (${userApps.size})" }
        val tabSystem = Button(this).apply { text = "시스템 앱 (${systemApps.size})" }
        fun selectTab(user: Boolean) {
            shown = if (user) userApps else systemApps
            adapter.notifyDataSetChanged()
            listView.setSelection(0)
            tabUser.isEnabled = !user
            tabSystem.isEnabled = user
        }
        tabUser.setOnClickListener { selectTab(true) }
        tabSystem.setOnClickListener { selectTab(false) }

        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(tabUser, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(tabSystem, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabRow)
            // ListView는 명시적 높이를 줘야 다이얼로그 안에서 스크롤됨
            addView(listView, LinearLayout.LayoutParams(
                MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.6).toInt()))
        }
        selectTab(true)   // 초기 상태 반영

        val dialog = AlertDialog.Builder(this)
            .setTitle("앱 선택")
            .setView(container)
            .setNegativeButton("취소", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val info = shown[position]
            dialog.dismiss()
            showNumberDialog(info.packageName, info.loadLabel(pm).toString())
        }
        dialog.show()
    }

    /** 특정 앱의 번호 입력/수정 다이얼로그 */
    private fun showNumberDialog(pkg: String, appLabel: String) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            hint = "01012341234 / 010-1234-1234 / +821012341234"
            setText(prefs.getString(Const.KEY_APP_PREFIX + pkg, ""))
        }
        AlertDialog.Builder(this)
            .setTitle(appLabel)
            .setMessage(pkg)
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                if (!guardSave()) return@setPositiveButton
                val v = input.text.toString().trim()
                val e = prefs.edit()
                if (v.isEmpty()) e.remove(Const.KEY_APP_PREFIX + pkg)   // 빈 값 = 개별 설정 해제
                else e.putString(Const.KEY_APP_PREFIX + pkg, v)
                e.apply()
                refreshList()
                toast("저장됨 (대상 앱 강제중지 후 반영)")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // --- helpers ---
    /** 모듈이 비활성이면 저장을 막고 안내 (PRIVATE 폴백에 몰래 쓰는 것 방지) */
    private fun guardSave(): Boolean {
        if (!moduleActive) {
            toast("모듈이 비활성 상태입니다. LSPosed에서 모듈을 켠 뒤 앱을 다시 열어 저장하세요.")
            return false
        }
        return true
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rowParams() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 14f }
    private fun divider() = View(this).apply {
        setBackgroundColor(Color.LTGRAY)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
            topMargin = dp(20); bottomMargin = dp(4)
        }
    }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
}
