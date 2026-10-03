package com.lulu.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReplyChoices(
    val aggressive: String,
    val normal: String,
    val conservative: String
)

object CoachApi {
    fun login(baseUrl: String, email: String, password: String): String {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
        val connection = open("$baseUrl/api/auth/login", "POST")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("登录失败：${connection.responseCode} $response")
        }
        val setCookie = connection.headerFields
            .entries
            .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            ?.value
            ?.firstOrNull()
            ?: throw IllegalStateException("后端未返回登录会话")
        return setCookie.substringBefore(';')
    }

    fun suggest(baseUrl: String, cookie: String, sharedText: String): ReplyChoices {
        val request = JSONObject()
            .put("platform", "android")
            .put("conversationId", "android-" + System.currentTimeMillis())
            .put("userAlias", "我")
            .put("otherAlias", "对方")
            .put("relationshipStage", "未提供")
            .put("goal", "自动判断")
            .put("userStyle", "短句、自然、不油腻")
            .put("messages", parseMessages(sharedText))

        val connection = open("$baseUrl/api/ai/coach/suggest", "POST")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Cookie", cookie)
        connection.doOutput = true
        connection.outputStream.use {
            it.write(request.toString().toByteArray(Charsets.UTF_8))
        }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("生成失败：${connection.responseCode} $response")
        }

        val json = JSONObject(response)
        if (json.optBoolean("needsClarification", false)) {
            throw IllegalStateException(
                json.optString("clarificationQuestion", "还需要补充上下文")
            )
        }
        val alternatives = json.optJSONArray("alternatives") ?: JSONArray()
        val aggressive = alternatives.optJSONObject(0)?.optString("text").orEmpty()
        val conservative = alternatives.optJSONObject(1)?.optString("text").orEmpty()
        val normal = json.optString("bestReply")
        if (aggressive.isBlank() || normal.isBlank() || conservative.isBlank()) {
            throw IllegalStateException("后端没有返回完整的 A/B/C 三条建议")
        }
        return ReplyChoices(aggressive, normal, conservative)
    }

    private fun parseMessages(text: String): JSONArray {
        val array = JSONArray()
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { i, line ->
            val match = Regex("^([^：:]{1,20})[：:]\\s*(.+)$").find(line)
            val sender = when (match?.groupValues?.getOrNull(1)?.trim()) {
                "我" -> "我"
                "对方" -> "对方"
                null -> "对方"
                else -> match.groupValues[1].trim()
            }
            val message = match?.groupValues?.getOrNull(2)?.trim() ?: line
            array.put(
                JSONObject()
                    .put("sender", sender)
                    .put("text", message)
                    .put("time", (i + 1).toString())
            )
        }
        return array
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
        }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
}
