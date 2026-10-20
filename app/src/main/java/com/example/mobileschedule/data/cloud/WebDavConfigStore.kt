package com.example.mobileschedule.data.cloud

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class WebDavConfigStore private constructor(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Unit)

    constructor() : this(null, Unit)

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var inMemoryConfig: WebDavConfig = if (context != null) loadConfig() else WebDavConfig()
    private val mutableConfigFlow = MutableStateFlow(inMemoryConfig)
    val configFlow: StateFlow<WebDavConfig> = mutableConfigFlow.asStateFlow()

    open fun getConfig(): WebDavConfig = inMemoryConfig

    open fun saveConfig(config: WebDavConfig) {
        inMemoryConfig = config.copy(
            serverUrl = config.serverUrl.trim(),
            username = config.username.trim(),
            remoteDir = config.normalizedRemoteDir(),
        )
        prefs?.let { p ->
            val encryptedPassword = encryptPassword(config.password)
            p.edit()
                .putString(KEY_SERVER_URL, config.serverUrl.trim())
                .putString(KEY_USERNAME, config.username.trim())
                .putString(KEY_PASSWORD_ENC, encryptedPassword)
                .putString(KEY_REMOTE_DIR, config.normalizedRemoteDir())
                .putBoolean(KEY_AUTO_BACKUP, config.autoBackupOnExport)
                .apply()
        }
        mutableConfigFlow.value = inMemoryConfig
    }

    open fun clearConfig() {
        inMemoryConfig = WebDavConfig()
        prefs?.edit()?.clear()?.apply()
        mutableConfigFlow.value = inMemoryConfig
    }

    private fun loadConfig(): WebDavConfig {
        val p = prefs ?: return WebDavConfig()
        val serverUrl = p.getString(KEY_SERVER_URL, "").orEmpty()
        val username = p.getString(KEY_USERNAME, "").orEmpty()
        val encPassword = p.getString(KEY_PASSWORD_ENC, "").orEmpty()
        val remoteDir = p.getString(KEY_REMOTE_DIR, "/MobileSchedule/").orEmpty()
        val autoBackup = p.getBoolean(KEY_AUTO_BACKUP, false)

        val password = decryptPassword(encPassword)
        return WebDavConfig(
            serverUrl = serverUrl,
            username = username,
            password = password,
            remoteDir = if (remoteDir.isBlank()) "/MobileSchedule/" else remoteDir,
            autoBackupOnExport = autoBackup,
        )
    }

    private fun encryptPassword(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            // Format: [IV_LENGTH (1 byte)] + [IV] + [CIPHERTEXT]
            val combined = ByteArray(1 + iv.size + cipherText.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // Safe fallback for environments where AndroidKeyStore is unavailable (e.g. host JVM testing)
            fallbackEncrypt(plain)
        }
    }

    private fun decryptPassword(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = combined[0].toInt()
            val iv = ByteArray(ivSize)
            System.arraycopy(combined, 1, iv, 0, ivSize)
            val cipherTextSize = combined.size - 1 - ivSize
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, 1 + ivSize, cipherText, 0, cipherTextSize)

            val key = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            fallbackDecrypt(encoded)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }

        val keyGen = KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER)
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun fallbackEncrypt(plain: String): String {
        val key = SecretKeySpec(FALLBACK_KEY, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return "FB:" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun fallbackDecrypt(encoded: String): String {
        return try {
            val clean = if (encoded.startsWith("FB:")) encoded.removePrefix("FB:") else encoded
            val key = SecretKeySpec(FALLBACK_KEY, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decoded = Base64.decode(clean, Base64.NO_WRAP)
            String(cipher.doFinal(decoded), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val PREFS_NAME = "webdav_config_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD_ENC = "password_enc"
        private const val KEY_REMOTE_DIR = "remote_dir"
        private const val KEY_AUTO_BACKUP = "auto_backup"

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "mobileschedule_webdav_keystore_alias"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

        private val FALLBACK_KEY = byteArrayOf(
            0x2d, 0x4a, 0x61, 0x79, 0x73, 0x6f, 0x6e, 0x57,
            0x65, 0x62, 0x44, 0x61, 0x76, 0x4b, 0x65, 0x79,
        ) // 16 bytes key
    }
}
