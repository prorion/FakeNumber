package com.example.fakenumber

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class FakeNumberHook : IXposedHookLoadPackage {

    private val prefs = XSharedPreferences("com.example.fakenumber", Const.PREF_NAME)
    private val CC = "82"

    private enum class Fmt { RAW, DASHED, E164, E164_NOPLUS }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        val pkg = lpparam.packageName
        val cl = lpparam.classLoader
        val INT = Int::class.javaPrimitiveType!!

        // 표준 공개 경로
        hook(pkg, "android.telephony.SubscriptionManager", "getPhoneNumber", Fmt.E164, cl, INT)
        hook(pkg, "android.telephony.TelephonyManager",    "getLine1Number", Fmt.RAW,  cl)
        hook(pkg, "android.telephony.SubscriptionInfo",    "getNumber",      Fmt.RAW,  cl)

        // 추가 경로 (직접 호출/hidden 오버로드 — 없는 단말에선 miss로 무시됨)
        hook(pkg, "android.telephony.SubscriptionManager", "getPhoneNumber", Fmt.E164, cl, INT, INT)  // (subId, source)
        hook(pkg, "android.telephony.TelephonyManager",    "getLine1Number", Fmt.RAW,  cl, INT)        // (subId) hidden
        hook(pkg, "android.telephony.TelephonyManager",    "getMsisdn",      Fmt.RAW,  cl)             // hidden
    }

    private fun hook(pkg: String, clazz: String, method: String, def: Fmt, cl: ClassLoader, vararg paramTypes: Any) {
        val params = arrayOf(*paramTypes, object : XC_MethodHook() {
            override fun afterHookedMethod(p: MethodHookParam) {
                val nsn = currentNsn(pkg) ?: return         // 미설정이면 원본 통과
                val f = detect(p.result as? String) ?: def
                p.result = render(f, nsn)
            }
        })
        try {
            XposedHelpers.findAndHookMethod(clazz, cl, method, *params)
        } catch (t: Throwable) {
            XposedBridge.log("FakeNumberHook miss: $clazz#$method ${t.message}")
        }
    }

    private fun currentNsn(pkg: String): String? {
        prefs.reload()                                      // 매 호출 최신값
        // 앱별 번호 우선, 없으면 기본 번호 폴백
        var s = (prefs.getString(Const.KEY_APP_PREFIX + pkg, null)
            ?: prefs.getString(Const.KEY_NUMBER, ""))?.trim().orEmpty()
        if (s.isEmpty()) return null
        s = s.replace("-", "").replace(" ", "")
        if (s.startsWith("+")) s = s.substring(1)
        s = when {
            s.startsWith(CC)  -> s.substring(CC.length)
            s.startsWith("0") -> s.substring(1)
            else              -> s
        }
        return s.ifEmpty { null }
    }

    private fun detect(s: String?): Fmt? {
        val v = s?.trim().orEmpty()
        if (v.isEmpty()) return null
        return when {
            v.startsWith("+")                      -> Fmt.E164
            v.contains("-")                        -> Fmt.DASHED
            v.startsWith(CC) && !v.startsWith("0") -> Fmt.E164_NOPLUS
            else                                   -> Fmt.RAW
        }
    }

    private fun render(fmt: Fmt, nsn: String): String {
        val raw = "0$nsn"
        return when (fmt) {
            Fmt.E164        -> "+$CC$nsn"
            Fmt.E164_NOPLUS -> "$CC$nsn"
            Fmt.DASHED      -> dash(raw)
            Fmt.RAW         -> raw
        }
    }

    private fun dash(raw: String): String = when (raw.length) {
        11   -> "${raw.substring(0,3)}-${raw.substring(3,7)}-${raw.substring(7)}"
        10   -> "${raw.substring(0,3)}-${raw.substring(3,6)}-${raw.substring(6)}"
        else -> raw
    }
}
