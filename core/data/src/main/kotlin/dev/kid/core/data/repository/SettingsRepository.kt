package dev.kid.core.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "kid_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DESKTOP_HOSTNAME = stringPreferencesKey("desktop_hostname")
        val SAFE_WORD = stringPreferencesKey("safe_word")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val SUMMER_MODE = booleanPreferencesKey("summer_mode")
        val OWNER_ALIAS = stringPreferencesKey("owner_alias")
        val SOUL_PROFILE = stringPreferencesKey("soul_profile")
    }

    val desktopHostname: Flow<String> = context.dataStore.data
        .map { it[Keys.DESKTOP_HOSTNAME] ?: "desktop-g15" }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    val isSummerMode: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.SUMMER_MODE] ?: false }

    val ownerAlias: Flow<String> = context.dataStore.data
        .map { it[Keys.OWNER_ALIAS] ?: "Boss" }

    val safeWord: Flow<String> = context.dataStore.data
        .map { it[Keys.SAFE_WORD] ?: "jaago kiddo" }

    val soulProfile: Flow<String> = context.dataStore.data
        .map { it[Keys.SOUL_PROFILE] ?: "balanced" }

    suspend fun setDesktopHostname(hostname: String) {
        context.dataStore.edit { it[Keys.DESKTOP_HOSTNAME] = hostname }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setSummerMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SUMMER_MODE] = enabled }
    }

    suspend fun setOwnerAlias(alias: String) {
        context.dataStore.edit { it[Keys.OWNER_ALIAS] = alias }
    }

    suspend fun setSafeWord(word: String) {
        context.dataStore.edit { it[Keys.SAFE_WORD] = word }
    }

    suspend fun setSoulProfile(profile: String) {
        context.dataStore.edit { it[Keys.SOUL_PROFILE] = profile }
    }
}
