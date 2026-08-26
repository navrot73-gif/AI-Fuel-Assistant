package com.navrot.aifuelassistant.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkMonitorImplTest {

    private lateinit var context: Context
    private lateinit var networkMonitor: NetworkMonitorImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        networkMonitor = NetworkMonitorImpl(context)
    }

    @Test
    fun testIsOnlineFlowIsNotNull() = runBlocking {
        val onlineState = networkMonitor.isOnline.first()
        assertNotNull("isOnline StateFlow value should not be null", onlineState)
    }
}
