package com.navrot.aifuelassistant.ui.map

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class YandexMapsIntentTest {

    @Test
    fun `buildYandexMapsIntent creates intent with exact coordinates and package`() {
        val user = Pair(55.164, 61.436)
        val dest = Pair(55.200, 61.500)

        val intent = buildYandexMapsIntent(user, dest)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("ru.yandex.yandexmaps", intent.`package`)

        val expectedUrl = "https://yandex.ru/maps/?rtext=55.164,61.436~55.2,61.5&rtt=auto"
        assertEquals(Uri.parse(expectedUrl), intent.data)
    }
}
