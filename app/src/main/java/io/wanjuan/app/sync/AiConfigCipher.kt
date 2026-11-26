package io.wanjuan.app.sync

import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class AiConfigCipher(private val password: String) {
    private val random = SecureRandom()
    private var salt: ByteArray? = null
    private var key: SecretKeySpec? = null

    private fun keyFor(bytes: ByteArray): SecretKeySpec {
        if (salt?.contentEquals(bytes) == true) return requireNotNull(key)
        require(bytes.size == 16) { "AI 配置密文格式错误" }
        // This PBKDF2 variant is available on every supported Android version (API 24+).
        val spec = PBEKeySpec(password.toCharArray(), bytes, 600_000, 256)
        val derived = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return SecretKeySpec(derived, "AES").also { derived.fill(0); salt = bytes; key = it }
    }

    fun seal(text: String): String {
        val bytes = salt ?: ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(bytes), GCMParameterSpec(128, iv))
        fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
        return JSONObject().put("version", 1).put("salt", encode(bytes))
            .put("iv", encode(iv))
            .put("data", encode(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))).toString()
    }

    fun open(envelope: String): String {
        val obj = JSONObject(envelope)
        require(obj.getInt("version") == 1) { "不支持的 AI 配置备份版本" }
        fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
        val iv = decode(obj.getString("iv"))
        require(iv.size == 12) { "AI 配置密文格式错误" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.DECRYPT_MODE, keyFor(decode(obj.getString("salt"))), GCMParameterSpec(128, iv))
            return cipher.doFinal(decode(obj.getString("data"))).toString(Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("AI 配置解密失败，请核对同步口令", e)
        }
    }
}
