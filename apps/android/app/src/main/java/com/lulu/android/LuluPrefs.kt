package com.lulu.android

import android.content.Context

object LuluPrefs {
    private const val FILE = "lulu_android"
    private const val BASE_URL = "base_url"
    private const val COOKIE = "session_cookie"
    private const val BACKGROUND_VOICE_ENABLED = "background_voice_enabled"
    private const val VOICE_UI_FALLBACK_ENABLED = "voice_ui_fallback_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun baseUrl(context: Context): String =
        prefs(context).getString(BASE_URL, "")?.trim()?.trimEnd('/') ?: ""

    fun setBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(BASE_URL, value.trim().trimEnd('/')).apply()
    }

    fun cookie(context: Context): String =
        prefs(context).getString(COOKIE, "") ?: ""

    fun setCookie(context: Context, value: String) {
        prefs(context).edit().putString(COOKIE, value).apply()
    }

    fun backgroundVoiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(BACKGROUND_VOICE_ENABLED, true)

    fun setBackgroundVoiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(BACKGROUND_VOICE_ENABLED, enabled)
            .apply()
    }

    fun voiceUiFallbackEnabled(context: Context): Boolean =
        prefs(context).getBoolean(VOICE_UI_FALLBACK_ENABLED, false)

    fun setVoiceUiFallbackEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(VOICE_UI_FALLBACK_ENABLED, enabled)
            .apply()
    }
}
