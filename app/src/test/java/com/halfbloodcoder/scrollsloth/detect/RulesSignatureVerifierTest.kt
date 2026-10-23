package com.halfbloodcoder.scrollsloth.detect

import java.security.KeyPair
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun signingKey(curve: String = "secp256r1"): KeyPair =
    KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec(curve))
        generateKeyPair()
    }

internal fun sign(bytes: ByteArray, key: KeyPair): ByteArray =
    Signature.getInstance("SHA256withECDSA").run {
        initSign(key.private)
        update(bytes)
        sign()
    }

class RulesSignatureVerifierTest {
    private val primary = signingKey()
    private val backup = signingKey()
    private val bytes = "{\"rulesVersion\":2}\r\n".toByteArray()
    private val verifier = RulesSignatureVerifier { listOf(primary.public.encoded, backup.public.encoded) }

    @Test fun validSignatureAccepted() {
        assertTrue(verifier.verify(bytes, sign(bytes, primary)))
    }

    @Test fun flippedByteRejected() {
        val changed = bytes.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        assertFalse(verifier.verify(changed, sign(bytes, primary)))
    }

    @Test fun backupAccepted() {
        assertTrue(verifier.verify(bytes, sign(bytes, backup)))
    }

    @Test fun wrongKeyRejected() {
        assertFalse(verifier.verify(bytes, sign(bytes, signingKey())))
    }

    @Test fun emptyMalformedAndOversizedSignaturesRejected() {
        listOf(byteArrayOf(), byteArrayOf(1, 2, 3), ByteArray(1_025)).forEach {
            assertFalse(verifier.verify(bytes, it))
        }
    }

    @Test fun missingMalformedOrTooManyKeysRejected() {
        val signature = sign(bytes, primary)
        assertFalse(RulesSignatureVerifier { emptyList() }.verify(bytes, signature))
        assertFalse(RulesSignatureVerifier { listOf(byteArrayOf(1)) }.verify(bytes, signature))
        assertFalse(RulesSignatureVerifier { List(3) { primary.public.encoded } }.verify(bytes, signature))
        assertFalse(RulesSignatureVerifier { throw IOException("unreadable asset") }.verify(bytes, signature))
    }

    @Test fun wrongCurveRejectedEvenWithValidSignature() {
        val key = signingKey("secp384r1")
        assertFalse(RulesSignatureVerifier { listOf(key.public.encoded) }.verify(bytes, sign(bytes, key)))
    }

    @Test fun malformedPrimaryDoesNotPreventBackupVerification() {
        assertTrue(RulesSignatureVerifier { listOf(byteArrayOf(1), backup.public.encoded) }
            .verify(bytes, sign(bytes, backup)))
    }
}
