package golden

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.datasource.StationCacheImpl
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.datasource.StationLoaderImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SverdlovskyTest {

    @Test
    fun `sverdlovsky trakt 12V is always in station registry`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = StationLoaderImpl(
            httpClient = mock(),
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        )

        val stations = loader.loadFromAssets()

        val sverdlovskyStation = stations.find {
            it.address.contains("Свердловский тракт") && it.address.contains("12")
        }

        assertNotNull("Station on Sverdlovsky Trakt 12 must exist in asset bundle", sverdlovskyStation)
        assertEquals("Station brand must be Газпромнефть", "Газпромнефть", sverdlovskyStation!!.brand)
        assertTrue("Latitude must be valid (non-zero)", sverdlovskyStation.latitude != 0.0)
        assertTrue("Longitude must be valid (non-zero)", sverdlovskyStation.longitude != 0.0)
    }
}
