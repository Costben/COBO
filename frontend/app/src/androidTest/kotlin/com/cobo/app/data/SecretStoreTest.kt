package com.cobo.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keystore 加密的往返与兼容：跑在真机上，因为密钥在 AndroidKeyStore 里。
 */
@RunWith(AndroidJUnit4::class)
class SecretStoreTest {

    private val plain = """{"cookie":"SUB=abc; SUBP=def","uid":"42"}"""

    @Test
    fun sealedFileRoundTrips() {
        val sealed = SecretStore.seal(plain)
        assertTrue("密文要带版本前缀", sealed.startsWith("v1:"))
        assertNotEquals("不能原样落盘", plain, sealed)
        assertEquals(plain, SecretStore.open(sealed))
    }

    @Test
    fun sameInputSealsDifferently() {
        // 每次随机 IV；相同明文不能产生相同密文。
        assertNotEquals(SecretStore.seal(plain), SecretStore.seal(plain))
    }

    @Test
    fun unprefixedFileIsPlaintext() {
        // 升级前写下的旧文件没有前缀，必须照读。
        assertEquals(plain, SecretStore.open(plain))
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val sealed = SecretStore.seal(plain)
        val broken = sealed.dropLast(4) + "AAAA"
        assertNull(SecretStore.open(broken))
    }
}
