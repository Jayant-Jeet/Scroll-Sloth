package com.halfbloodcoder.scrollsloth.detect

import java.io.IOException
import java.security.AlgorithmParameters
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec

/** Verifies detached DER signatures over the exact downloaded bytes, without JSON normalization. */
class RulesSignatureVerifier(private val publicKeys: () -> List<ByteArray>) {
    fun verify(bytes: ByteArray, signature: ByteArray): Boolean {
        if (signature.isEmpty() || signature.size > MAX_SIGNATURE_BYTES) return false
        val keys = try {
            publicKeys()
        } catch (_: IOException) {
            return false
        }
        if (keys.isEmpty() || keys.size > 2) return false
        return keys.any { encoded ->
            try {
                val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded)) as? ECPublicKey
                    ?: return@any false
                val p256 = AlgorithmParameters.getInstance("EC").apply {
                    init(ECGenParameterSpec("secp256r1"))
                }.getParameterSpec(ECParameterSpec::class.java)
                val params = key.params
                if (params.curve != p256.curve || params.generator != p256.generator ||
                    params.order != p256.order || params.cofactor != p256.cofactor
                ) return@any false
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(bytes)
                    verify(signature)
                }
            } catch (_: GeneralSecurityException) {
                false
            }
        }
    }

    companion object {
        const val MAX_SIGNATURE_BYTES = 1_024
    }
}
