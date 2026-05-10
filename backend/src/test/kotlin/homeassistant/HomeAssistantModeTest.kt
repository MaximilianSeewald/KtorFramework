package homeassistant

import com.loudless.homeassistant.HomeAssistantMode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeAssistantModeTest {
    @AfterTest
    fun tearDown() {
        System.clearProperty("HA_MODE")
    }

    @Test
    fun `mode is disabled when system property is absent or false`() {
        System.clearProperty("HA_MODE")
        assertFalse(HomeAssistantMode.enabled)

        System.setProperty("HA_MODE", "false")
        assertFalse(HomeAssistantMode.enabled)
    }

    @Test
    fun `mode can be enabled from system property for tests and local runs`() {
        System.setProperty("HA_MODE", "TRUE")

        assertTrue(HomeAssistantMode.enabled)
    }
}
