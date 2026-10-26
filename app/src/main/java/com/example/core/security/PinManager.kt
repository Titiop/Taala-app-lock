package com.example.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed class PinVerificationResult {
    data object Success : PinVerificationResult()
    data object Decoy : PinVerificationResult()
    data class Failed(val failedAttempts: Int, val lockoutSeconds: Int) : PinVerificationResult()
    data class LockedOut(val remainingSeconds: Int) : PinVerificationResult()
}

class PinManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("taala_pin_sec", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENCRYPTED_HASH = "enc_pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_DECOY_ENCRYPTED_HASH = "enc_decoy_hash"
        private const val KEY_DECOY_SALT = "decoy_salt"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"

        private const val PBKDF2_ITERATIONS = 10_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
    }

    fun isPinSet(): Boolean {
        return prefs.contains(KEY_ENCRYPTED_HASH) && prefs.contains(KEY_SALT)
    }

    fun isDecoyPinSet(): Boolean {
        return prefs.contains(KEY_DECOY_ENCRYPTED_HASH) && prefs.contains(KEY_DECOY_SALT)
    }

    fun setPin(pin: String): Boolean {
        if (pin.length !in 4..8) return false

        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val hash = hashPin(pin, salt)
        val encryptedHash = KeystoreHelper.encrypt(hash)

        prefs.edit()
            .putString(KEY_ENCRYPTED_HASH, encryptedHash)
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
        return true
    }

    fun setDecoyPin(pin: String): Boolean {
        if (pin.length !in 4..8) return false

        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)

        val hash = hashPin(pin, salt)
        val encryptedHash = KeystoreHelper.encrypt(hash)

        prefs.edit()
            .putString(KEY_DECOY_ENCRYPTED_HASH, encryptedHash)
            .putString(KEY_DECOY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
        return true
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_HASH)
            .remove(KEY_SALT)
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKOUT_UNTIL)
            .apply()
    }

    fun clearDecoyPin() {
        prefs.edit()
            .remove(KEY_DECOY_ENCRYPTED_HASH)
            .remove(KEY_DECOY_SALT)
            .apply()
    }

    fun getRemainingLockoutSeconds(): Int {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            ((lockoutUntil - now) / 1000L).toInt() + 1
        } else {
            0
        }
    }

    fun verifyPin(pin: String): PinVerificationResult {
        val remaining = getRemainingLockoutSeconds()
        if (remaining > 0) {
            return PinVerificationResult.LockedOut(remaining)
        }

        // Check Decoy PIN first if set
        if (isDecoyPinSet()) {
            val decoyEnc = prefs.getString(KEY_DECOY_ENCRYPTED_HASH, null)
            val decoySaltStr = prefs.getString(KEY_DECOY_SALT, null)
            if (decoyEnc != null && decoySaltStr != null) {
                val decoySalt = Base64.decode(decoySaltStr, Base64.NO_WRAP)
                val decoyDec = KeystoreHelper.decrypt(decoyEnc)
                if (decoyDec != null && hashPin(pin, decoySalt).contentEquals(decoyDec)) {
                    // Decoy PIN matched!
                    return PinVerificationResult.Decoy
                }
            }
        }

        val encryptedHash = prefs.getString(KEY_ENCRYPTED_HASH, null) ?: return PinVerificationResult.Failed(0, 0)
        val saltBase64 = prefs.getString(KEY_SALT, null) ?: return PinVerificationResult.Failed(0, 0)
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)

        val decryptedHash = KeystoreHelper.decrypt(encryptedHash)
        if (decryptedHash == null) {
            return PinVerificationResult.Failed(0, 0)
        }

        val computedHash = hashPin(pin, salt)
        val matches = computedHash.contentEquals(decryptedHash)

        return if (matches) {
            // Reset attempts on successful entry
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L)
                .apply()
            PinVerificationResult.Success
        } else {
            val failed = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
            val lockoutSeconds = calculateLockoutSeconds(failed)
            val lockoutUntil = if (lockoutSeconds > 0) {
                System.currentTimeMillis() + (lockoutSeconds * 1000L)
            } else 0L

            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, failed)
                .putLong(KEY_LOCKOUT_UNTIL, lockoutUntil)
                .apply()

            PinVerificationResult.Failed(failed, lockoutSeconds)
        }
    }

    /**
     * Escalating lockout delays per Section 5.2:
     * 1-2 attempts: no lockout
     * 3 attempts: 30s
     * 4 attempts: 60s (1 min)
     * 5 attempts: 300s (5 min)
     * 6+ attempts: 900s (15 min)
     */
    fun calculateLockoutSeconds(failedAttempts: Int): Int {
        return when {
            failedAttempts < 3 -> 0
            failedAttempts == 3 -> 30
            failedAttempts == 4 -> 60
            failedAttempts == 5 -> 300
            else -> 900
        }
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (_: Exception) {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        return factory.generateSecret(spec).encoded
    }
}
