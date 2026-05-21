package com.example.security

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {
    
    // In-memory active symmetric session key. Never persisted to disk.
    @Volatile
    private var sessionKey: SecretKey? = null
    
    // Our ephemeral ECDH KeyPair (generated per connection session/shake)
    @Volatile
    private var ephemeralKeyPair: KeyPair? = null

    // Generates a new EC Ephemeral Key pair for a new session
    fun generateEphemeralKeyPair(): KeyPair {
        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(256)
        val keyPair = keyPairGen.generateKeyPair()
        ephemeralKeyPair = keyPair
        return keyPair
    }

    // Returns our Base64-encoded Ephemeral Public Key to transmit
    fun getMyBase64PublicKey(): String {
        val pair = ephemeralKeyPair ?: generateEphemeralKeyPair()
        return Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    // Derives the shared AES-256 session key using ECDH and peer's public key
    fun deriveSessionKey(peerBase64PublicKey: String): SecretKey {
        val myPrivate = ephemeralKeyPair?.private ?: throw IllegalStateException("My ephemeral private key is null. Handshake not initialized!")
        
        val peerKeyBytes = Base64.decode(peerBase64PublicKey, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

        // ECDH key agreement
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivate)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive standard 256-bit AES key using SHA-256 as our KDF
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val aesKeyBytes = messageDigest.digest(sharedSecret)
        
        val key = SecretKeySpec(aesKeyBytes, "AES")
        sessionKey = key
        return key
    }

    // Manual setter if we need to reset or explicitly configure (e.g., testing or multi-session)
    fun setSessionKey(key: SecretKey?) {
        sessionKey = key
    }

    fun getSessionKey(): SecretKey? = sessionKey

    fun clearSession() {
        sessionKey = null
        ephemeralKeyPair = null
    }

    fun isSecureChannelEstablished(): Boolean = sessionKey != null

    // Encrypts plaintext bytes using AES-256-GCM
    fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val key = sessionKey ?: throw IllegalStateException("No active encrypted session key!")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedPayload(
            payloadBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            timestamp = System.currentTimeMillis()
        )
    }

    // Decrypts ciphertext bytes from Base64 inputs using AES-256-GCM
    fun decrypt(payloadBase64: String, ivBase64: String): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("No active encrypted session key!")
        
        val ciphertext = Base64.decode(payloadBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        return cipher.doFinal(ciphertext)
    }
}

data class EncryptedPayload(
    val payloadBase64: String,
    val ivBase64: String,
    val timestamp: Long
)
