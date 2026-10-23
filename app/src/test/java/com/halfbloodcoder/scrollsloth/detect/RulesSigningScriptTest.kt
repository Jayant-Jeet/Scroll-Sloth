package com.halfbloodcoder.scrollsloth.detect

import java.io.File
import java.security.KeyPair
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Interoperability with the actual OpenSSL deployment gate; private keys remain in test memory. */
class RulesSigningScriptTest {
    @get:Rule val folder = TemporaryFolder()
    private val root by lazy { folder.newFolder("repo with spaces") }
    private val primary = signingKey()
    private val backup = signingKey()
    private val bytes = "{\"rulesVersion\":2}\r\n".toByteArray()

    private fun prepare() {
        File(root, "tools").mkdirs()
        File("../tools/sign-rules.sh").copyTo(File(root, "tools/sign-rules.sh"))
        File(root, "site/keys").mkdirs()
        File(root, "app/src/main/assets").mkdirs()
        File(root, "site/detection-rules.json").writeBytes(bytes)
        writePublicKey("primary", primary)
        writePublicKey("backup", backup)
    }

    private fun writePublicKey(slot: String, pair: KeyPair) {
        val encoded = pair.public.encoded
        File(root, "app/src/main/assets/rules-pubkey-$slot.der").writeBytes(encoded)
        val body = Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(encoded)
        File(root, "site/keys/rules-pubkey-$slot.pem")
            .writeText("-----BEGIN PUBLIC KEY-----\n$body\n-----END PUBLIC KEY-----\n")
    }

    private fun verify(expected: Int) {
        val gitBash = File("C:\\Program Files\\Git\\usr\\bin\\bash.exe")
        val windows = System.getProperty("os.name").startsWith("Windows")
        val command = if (windows) gitBash.absolutePath else "bash"
        val builder = ProcessBuilder(command, File(root, "tools/sign-rules.sh").absolutePath, "--verify")
            .directory(root).redirectErrorStream(true)
        if (windows) {
            builder.environment()["PATH"] = "${gitBash.parent};${System.getenv("PATH")}"
        }
        val process = builder.start()
        val completed = process.waitFor(15, TimeUnit.SECONDS)
        if (!completed) process.destroyForcibly()
        assertTrue("OpenSSL verification timed out", completed)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(output, expected, process.exitValue())
    }

    @Test fun primaryAndBackupVerifyAndChangedBytesFail() {
        prepare()
        val signature = File(root, "site/detection-rules.json.sig")
        signature.writeBytes(sign(bytes, primary))
        verify(0)
        signature.writeBytes(sign(bytes, backup))
        verify(0)
        File(root, "site/detection-rules.json").appendText(" ")
        verify(1)
    }

    @Test fun missingSignatureFailsClosed() {
        prepare()
        verify(1)
    }

    @Test fun mismatchedEmbeddedPublicKeyFailsDeployment() {
        prepare()
        File(root, "site/detection-rules.json.sig").writeBytes(sign(bytes, primary))
        File(root, "app/src/main/assets/rules-pubkey-primary.der").writeBytes(backup.public.encoded)
        verify(1)
    }
}
