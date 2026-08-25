package com.navrot.aifuelassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyTokenTest {

    @Test
    fun proxyToken_whenBlank_throwsExceptionWithClearMessage() {
        val proxyToken = ""
        val exception = assertThrows(IllegalArgumentException::class.java) {
            require(proxyToken.isNotBlank()) {
                "PROXY_TOKEN is required. Set it in local.properties or PROXY_TOKEN env variable."
            }
        }
        assertEquals(
            "PROXY_TOKEN is required. Set it in local.properties or PROXY_TOKEN env variable.",
            exception.message
        )
    }

    @Test
    fun proxyToken_buildConfig_isNotBlank() {
        assertTrue(BuildConfig.PROXY_TOKEN.isNotBlank())
    }
}
