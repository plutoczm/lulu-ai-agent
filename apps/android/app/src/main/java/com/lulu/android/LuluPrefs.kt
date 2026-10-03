package com.lulu.android

import android.content.Context

object LuluPrefs {
    private const val FILE = "lulu_android"
    private const val BASE_URL = "base_url"
    private const val COOKIE = "session_cookie"

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
}
