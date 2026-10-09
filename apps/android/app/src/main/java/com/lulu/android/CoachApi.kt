package com.lulu.android

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ReplyChoices(
    val aggressive: String,
    val normal: String,
    val conservative: String
)

data class ThreadStatus(
    val recentMessages: Int,
    val memoryFacts: Int,
    val importedFacts: Int
)

data class ReassignResult(
    val movedMemoryFacts: Int,
    val recentMessages: Int,
    val memoryFacts: Int
)

data class ChatSyncMessage(
    val sender: String,
    val text: String,
    val time: String = "",
    val contentType: String = "text",
    val source: String,
    val sourceKey: String = "",
    val replacesSourceKey: String = "",
    val observedAt: String
)

data class ChatSyncResult(
    val inserted: Int,
    val merged: Int,
    val totalMessages: Int
)

data class VoiceTranscript(
    val text: String,
    val provider: String,
    val model: String
)

object CoachApi {
    fun login(baseUrl: String, email: String, password: String): String {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
        val connection = open(baseUrl + "/api/auth/login", "POST")
        connection.setRequestProperty(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        connection.doOutput = true
        connection.outputStream.use {
            it.write(body.toByteArray(Charsets.UTF_8))
        }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                "登录失败：" + connection.responseCode + " " + response
            )
        }
        val setCookie = connection.headerFields
            .entries
            .firstOrNull {
                it.key?.equals("Set-Cookie", ignoreCase = true) == true
            }
            ?.value
            ?.firstOrNull()
            ?: throw IllegalStateException("后端未返回登录会话")
        return setCookie.substringBefore(';')
    }

    fun suggest(
        baseUrl: String,
        cookie: String,
        person: PersonProfile,
        account: ContactAccount,
        sharedText: String
    ): ReplyChoices {
        val request = JSONObject()
            .put("platform", account.platform)
            .put("conversationId", account.threadId)
            .put("personId", person.id)
            .put("accountId", account.id)
            .put("userAlias", "我")
            .put("otherAlias", account.alias)
            .put("relationshipStage", person.relationshipStage)
            .put("goal", person.goal)
            .put("userStyle", person.userStyle)
            .put("messages", parseMessages(sharedText))

        val json = postJson(
            baseUrl + "/api/ai/coach/suggest/quick",
            cookie,
            request
        )
        if (json.optBoolean("needsClarification", false)) {
            throw IllegalStateException(
                json.optString(
                    "clarificationQuestion",
                    "还需要补充上下文"
                )
            )
        }

        val alternatives = json.optJSONArray("alternatives") ?: JSONArray()
        val aggressive = alternatives
            .optJSONObject(0)
            ?.optString("text")
            .orEmpty()
        val conservative = alternatives
            .optJSONObject(1)
            ?.optString("text")
            .orEmpty()
        val normal = json.optString("bestReply")
        if (aggressive.isBlank()
            || normal.isBlank()
            || conservative.isBlank()) {
            throw IllegalStateException(
                "后端没有返回完整的 A/B/C 三条建议"
            )
        }
        return ReplyChoices(aggressive, normal, conservative)
    }

    fun importHistory(
        baseUrl: String,
        cookie: String,
        person: PersonProfile,
        account: ContactAccount,
        historyText: String
    ): ThreadStatus {
        val request = JSONObject()
            .put("conversationId", account.threadId)
            .put("personId", person.id)
            .put("accountId", account.id)
            .put("platform", account.platform)
            .put("otherAlias", account.alias)
            .put("relationshipStage", person.relationshipStage)
            .put("messages", parseMessages(historyText))

        val json = postJson(
            baseUrl + "/api/ai/coach/thread/history/import",
            cookie,
            request
        )
        return parseThreadStatus(json)
    }

    fun syncMessages(
        baseUrl: String,
        cookie: String,
        person: PersonProfile,
        account: ContactAccount,
        messages: List<ChatSyncMessage>
    ): ChatSyncResult {
        if (messages.isEmpty()) {
            return ChatSyncResult(0, 0, 0)
        }

        val payload = JSONArray()
        messages.forEach { message ->
            payload.put(
                JSONObject()
                    .put("sender", message.sender)
                    .put("text", message.text)
                    .put("time", message.time)
                    .put("contentType", message.contentType)
                    .put("source", message.source)
                    .put("sourceKey", message.sourceKey)
                    .put("replacesSourceKey", message.replacesSourceKey)
                    .put("observedAt", message.observedAt)
            )
        }

        val request = JSONObject()
            .put("conversationId", account.threadId)
            .put("personId", person.id)
            .put("accountId", account.id)
            .put("platform", account.platform)
            .put("messages", payload)

        val json = postJson(
            baseUrl + "/api/ai/coach/thread/sync",
            cookie,
            request
        )
        return ChatSyncResult(
            inserted = json.optInt("inserted", 0),
            merged = json.optInt("merged", 0),
            totalMessages = json.optInt("totalMessages", 0)
        )
    }

    fun transcribeVoice(
        baseUrl: String,
        cookie: String,
        audio: ByteArray,
        format: String,
        sampleRate: Int? = null
    ): VoiceTranscript {
        require(audio.isNotEmpty()) { "语音文件为空" }

        val request = JSONObject()
            .put(
                "audioBase64",
                java.util.Base64.getEncoder().encodeToString(audio)
            )
            .put("format", format)
        if (sampleRate != null && sampleRate > 0) {
            request.put("sampleRate", sampleRate)
        }

        val json = postJson(
            baseUrl + "/api/ai/voice/transcribe",
            cookie,
            request
        )
        val text = json.optString("text").trim()
        if (text.isBlank()) {
            throw IllegalStateException("语音识别没有返回文字")
        }
        return VoiceTranscript(
            text = text,
            provider = json.optString("provider", "dashscope"),
            model = json.optString(
                "model",
                "paraformer-realtime-v2"
            )
        )
    }

    fun threadStatus(
        baseUrl: String,
        cookie: String,
        person: PersonProfile,
        account: ContactAccount
    ): ThreadStatus {
        val url = baseUrl +
            "/api/ai/coach/thread/status?conversationId=" +
            encode(account.threadId) +
            "&personId=" +
            encode(person.id)
        val connection = open(url, "GET")
        connection.setRequestProperty("Cookie", cookie)
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                "读取账号记忆状态失败：" +
                    connection.responseCode +
                    " " +
                    response
            )
        }
        return parseThreadStatus(JSONObject(response))
    }

    fun clearAccount(
        baseUrl: String,
        cookie: String,
        person: PersonProfile,
        account: ContactAccount
    ): ThreadStatus {
        val url = baseUrl +
            "/api/ai/coach/thread?conversationId=" +
            encode(account.threadId) +
            "&personId=" +
            encode(person.id)
        val connection = open(url, "DELETE")
        connection.setRequestProperty("Cookie", cookie)
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                "清空当前账号最近聊天失败：" +
                    connection.responseCode +
                    " " +
                    response
            )
        }
        return parseThreadStatus(JSONObject(response))
    }

    fun clearPersonMemory(
        baseUrl: String,
        cookie: String,
        person: PersonProfile,
        activeAccount: ContactAccount?
    ): ThreadStatus {
        var url = baseUrl +
            "/api/ai/coach/thread/person-memory?personId=" +
            encode(person.id)
        if (activeAccount != null) {
            url += "&conversationId=" + encode(activeAccount.threadId)
        }
        val connection = open(url, "DELETE")
        connection.setRequestProperty("Cookie", cookie)
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                "清空人物长期记忆失败：" +
                    connection.responseCode +
                    " " +
                    response
            )
        }
        return parseThreadStatus(JSONObject(response))
    }

    fun reassignAccount(
        baseUrl: String,
        cookie: String,
        account: ContactAccount,
        oldPersonId: String,
        newPersonId: String
    ): ReassignResult {
        val request = JSONObject()
            .put("conversationId", account.threadId)
            .put("accountId", account.id)
            .put("oldPersonId", oldPersonId)
            .put("newPersonId", newPersonId)

        val json = postJson(
            baseUrl + "/api/ai/coach/thread/account/reassign",
            cookie,
            request
        )
        return ReassignResult(
            movedMemoryFacts = json.optInt("movedMemoryFacts", 0),
            recentMessages = json.optInt("recentMessages", 0),
            memoryFacts = json.optInt("memoryFacts", 0)
        )
    }

    private fun postJson(
        url: String,
        cookie: String,
        request: JSONObject
    ): JSONObject {
        val connection = open(url, "POST")
        connection.setRequestProperty(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        connection.setRequestProperty("Cookie", cookie)
        connection.doOutput = true
        connection.outputStream.use {
            it.write(request.toString().toByteArray(Charsets.UTF_8))
        }
        val response = readResponse(connection)
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(
                "请求失败：" +
                    connection.responseCode +
                    " " +
                    response
            )
        }
        return JSONObject(response)
    }

    private fun parseThreadStatus(json: JSONObject): ThreadStatus =
        ThreadStatus(
            recentMessages = json.optInt("recentMessages", 0),
            memoryFacts = json.optInt("memoryFacts", 0),
            importedFacts = json.optInt("importedFacts", 0)
        )

    private fun parseMessages(text: String): JSONArray {
        val array = JSONArray()
        text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEachIndexed { i, line ->
                val match = Regex(
                    "^([^：:]{1,30})[：:]\\s*(.+)$"
                ).find(line)
                val sender = when (
                    match?.groupValues?.getOrNull(1)?.trim()
                ) {
                    "我" -> "我"
                    "对方" -> "对方"
                    null -> "对方"
                    else -> match.groupValues[1].trim()
                }
                val message =
                    match?.groupValues?.getOrNull(2)?.trim() ?: line
                array.put(
                    JSONObject()
                        .put("sender", sender)
                        .put("text", message)
                        .put("time", (i + 1).toString())
                )
            }
        if (array.length() == 0) {
            throw IllegalArgumentException(
                "没有可导入或分析的聊天文字"
            )
        }
        return array
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun open(
        url: String,
        method: String
    ): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 90_000
        }

    private fun readResponse(
        connection: HttpURLConnection
    ): String {
        val stream = if (
            connection.responseCode in 200..299
        ) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
    }
}
