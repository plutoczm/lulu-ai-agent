package com.lulu.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PersonProfile(
    val id: String,
    val displayName: String,
    val relationshipStage: String,
    val goal: String,
    val userStyle: String
)

data class ContactAccount(
    val id: String,
    val personId: String,
    val threadId: String,
    val platform: String,
    val alias: String,
    val accountLabel: String
) {
    val platformLabel: String
        get() = if (platform == "qq") "QQ" else "微信"

    val displayName: String
        get() {
            val suffix = if (accountLabel.isBlank()) "" else " · " + accountLabel
            return platformLabel + " · " + alias + suffix
        }
}

data class ActiveIdentity(
    val person: PersonProfile,
    val account: ContactAccount
) {
    val displayName: String
        get() = person.displayName + "｜" + account.displayName
}

object IdentityStore {
    private const val FILE = "lulu_android_identity"
    private const val PERSONS = "persons"
    private const val ACCOUNTS = "accounts"
    private const val ACTIVE_ACCOUNT_ID = "active_account_id"

    private const val LEGACY_FILE = "lulu_android_contacts"
    private const val LEGACY_CONTACTS = "contacts"
    private const val LEGACY_ACTIVE_ID = "active_contact_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun persons(context: Context): List<PersonProfile> {
        migrateLegacyIfNeeded(context)
        val raw = prefs(context).getString(PERSONS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<PersonProfile>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("displayName").trim()
            if (id.isBlank() || name.isBlank()) continue
            result += PersonProfile(
                id = id,
                displayName = name,
                relationshipStage = item.optString(
                    "relationshipStage",
                    "未提供"
                ),
                goal = item.optString("goal", "自动判断"),
                userStyle = item.optString(
                    "userStyle",
                    "短句、自然、不油腻"
                )
            )
        }
        return result
    }

    fun accounts(context: Context): List<ContactAccount> {
        migrateLegacyIfNeeded(context)
        return readAccounts(context)
    }

    fun accountsForPerson(
        context: Context,
        personId: String
    ): List<ContactAccount> =
        accounts(context).filter { it.personId == personId }

    fun person(
        context: Context,
        personId: String
    ): PersonProfile? =
        persons(context).firstOrNull { it.id == personId }

    fun activeAccount(context: Context): ContactAccount? {
        val items = accounts(context)
        if (items.isEmpty()) return null
        val activeId = prefs(context)
            .getString(ACTIVE_ACCOUNT_ID, "")
            .orEmpty()
        return items.firstOrNull { it.id == activeId } ?: items.first()
    }

    fun activeIdentity(context: Context): ActiveIdentity? {
        val account = activeAccount(context) ?: return null
        val person = person(context, account.personId) ?: return null
        return ActiveIdentity(person, account)
    }

    fun setActiveAccount(context: Context, accountId: String) {
        prefs(context).edit()
            .putString(ACTIVE_ACCOUNT_ID, accountId)
            .apply()
    }

    fun createPerson(
        displayName: String,
        relationshipStage: String,
        goal: String,
        userStyle: String
    ): PersonProfile =
        PersonProfile(
            id = "person_" + UUID.randomUUID().toString().replace("-", ""),
            displayName = displayName.trim(),
            relationshipStage = relationshipStage
                .ifBlank { "未提供" }
                .trim(),
            goal = goal.ifBlank { "自动判断" }.trim(),
            userStyle = userStyle
                .ifBlank { "短句、自然、不油腻" }
                .trim()
        )

    fun createAccount(
        personId: String,
        platform: String,
        alias: String,
        accountLabel: String
    ): ContactAccount {
        val id = UUID.randomUUID().toString().replace("-", "")
        val platformKey = if (platform == "qq") "qq" else "wechat"
        return ContactAccount(
            id = "account_" + id,
            personId = personId,
            threadId = "android_" + platformKey + "_account_" + id,
            platform = platformKey,
            alias = alias.trim(),
            accountLabel = accountLabel.trim()
        )
    }

    fun upsertPerson(context: Context, profile: PersonProfile) {
        val items = persons(context).toMutableList()
        val index = items.indexOfFirst { it.id == profile.id }
        if (index >= 0) items[index] = profile else items += profile
        savePersons(context, items)
    }

    fun upsertAccount(context: Context, account: ContactAccount) {
        val items = accounts(context).toMutableList()
        val index = items.indexOfFirst { it.id == account.id }
        if (index >= 0) items[index] = account else items += account
        saveAccounts(context, items)
        setActiveAccount(context, account.id)
    }

    fun reassignAccountLocal(
        context: Context,
        accountId: String,
        newPersonId: String
    ): ContactAccount? {
        val items = accounts(context).toMutableList()
        val index = items.indexOfFirst { it.id == accountId }
        if (index < 0) return null
        val updated = items[index].copy(personId = newPersonId)
        items[index] = updated
        saveAccounts(context, items)
        setActiveAccount(context, updated.id)
        return updated
    }

    fun cycleActiveAccount(context: Context): ActiveIdentity? {
        val items = accounts(context)
        if (items.isEmpty()) return null
        val current = activeAccount(context)
        val index = items.indexOfFirst { it.id == current?.id }
        val next = items[(if (index < 0) 0 else index + 1) % items.size]
        setActiveAccount(context, next.id)
        val person = person(context, next.personId) ?: return null
        return ActiveIdentity(person, next)
    }

    private fun readAccounts(context: Context): List<ContactAccount> {
        val raw = prefs(context).getString(ACCOUNTS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val result = mutableListOf<ContactAccount>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            val personId = item.optString("personId").trim()
            val threadId = item.optString("threadId").trim()
            val alias = item.optString("alias").trim()
            if (id.isBlank() || personId.isBlank()
                || threadId.isBlank() || alias.isBlank()) {
                continue
            }
            result += ContactAccount(
                id = id,
                personId = personId,
                threadId = threadId,
                platform = item.optString("platform", "wechat"),
                alias = alias,
                accountLabel = item.optString("accountLabel", "")
            )
        }
        return result
    }

    private fun savePersons(
        context: Context,
        persons: List<PersonProfile>
    ) {
        val array = JSONArray()
        persons.forEach { person ->
            array.put(
                JSONObject()
                    .put("id", person.id)
                    .put("displayName", person.displayName)
                    .put(
                        "relationshipStage",
                        person.relationshipStage
                    )
                    .put("goal", person.goal)
                    .put("userStyle", person.userStyle)
            )
        }
        prefs(context).edit()
            .putString(PERSONS, array.toString())
            .apply()
    }

    private fun saveAccounts(
        context: Context,
        accounts: List<ContactAccount>
    ) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("id", account.id)
                    .put("personId", account.personId)
                    .put("threadId", account.threadId)
                    .put("platform", account.platform)
                    .put("alias", account.alias)
                    .put("accountLabel", account.accountLabel)
            )
        }
        prefs(context).edit()
            .putString(ACCOUNTS, array.toString())
            .apply()
    }

    private fun migrateLegacyIfNeeded(context: Context) {
        val current = prefs(context)
        if (current.contains(PERSONS) || current.contains(ACCOUNTS)) {
            return
        }

        val legacy = context.getSharedPreferences(
            LEGACY_FILE,
            Context.MODE_PRIVATE
        )
        val raw = legacy.getString(LEGACY_CONTACTS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        if (array.length() == 0) {
            current.edit()
                .putString(PERSONS, "[]")
                .putString(ACCOUNTS, "[]")
                .apply()
            return
        }

        val migratedPersons = mutableListOf<PersonProfile>()
        val migratedAccounts = mutableListOf<ContactAccount>()
        val legacyActive = legacy.getString(LEGACY_ACTIVE_ID, "").orEmpty()
        var newActiveAccountId = ""

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val legacyId = item.optString("id").trim()
            val alias = item.optString("alias").trim()
            val threadId = item.optString("threadId").trim()
            if (legacyId.isBlank() || alias.isBlank() || threadId.isBlank()) {
                continue
            }

            val normalizedId = legacyId.replace("-", "")
            val personId = "person_" + normalizedId
            val accountId = "account_" + normalizedId
            migratedPersons += PersonProfile(
                id = personId,
                displayName = alias,
                relationshipStage = item.optString(
                    "relationshipStage",
                    "未提供"
                ),
                goal = item.optString("goal", "自动判断"),
                userStyle = item.optString(
                    "userStyle",
                    "短句、自然、不油腻"
                )
            )
            migratedAccounts += ContactAccount(
                id = accountId,
                personId = personId,
                threadId = threadId,
                platform = item.optString("platform", "wechat"),
                alias = alias,
                accountLabel = ""
            )
            if (legacyId == legacyActive) {
                newActiveAccountId = accountId
            }
        }

        savePersons(context, migratedPersons)
        saveAccounts(context, migratedAccounts)
        if (newActiveAccountId.isNotBlank()) {
            setActiveAccount(context, newActiveAccountId)
        }
    }
}
