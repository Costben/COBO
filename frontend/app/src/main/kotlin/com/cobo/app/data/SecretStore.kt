package com.cobo.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 本地登录凭据的落盘加密。
 *
 * `session.json`、`accounts.json`、`visitor.json` 里装的是微博的 cookie，明文放在应用私有目录时，
 * root、备份导出或同 UID 的调试工具都能直接读走。这里用 Android Keystore 里的一把 AES-256-GCM
 * 密钥把它们包一层：密钥不导出、不出 TEE，源码和 APK 开源也拿不到；能解开的只有这台设备上以本
 * 应用 UID 运行的进程。
 *
 * 文件格式是 `v1:` + base64(iv || ciphertext)。没有前缀的文件按明文读，下一次写入自动转成密文，
 * 老版本升级不需要迁移脚本。Keystore 不可用（个别定制 ROM、密钥被清理）时退回明文并记日志，
 * 不让加密失败阻断登录。
 */
object SecretStore {
    private const val TAG = "CoboSecretStore"
    private const val PREFIX = "v1:"
    private const val KEY_ALIAS = "cobo_local_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** 加密 [plain]；任何一步失败都原样返回，调用方照常落盘。 */
    fun seal(plain: String): String = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(iv + body, Base64.NO_WRAP)
    }.onFailure {
        Log.w(TAG, "seal failed, writing plaintext", it)
    }.getOrDefault(plain)

    /** 解密；没有前缀按明文处理。解不开返回 `null`，调用方当作「没有登录态」，不会崩。 */
    fun open(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return raw
        return runCatching {
            val bytes = Base64.decode(raw.substring(PREFIX.length), Base64.NO_WRAP)
            require(bytes.size > IV_BYTES) { "ciphertext too short" }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.onFailure {
            Log.w(TAG, "open failed; the file is treated as unreadable", it)
        }.getOrNull()
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }
}
