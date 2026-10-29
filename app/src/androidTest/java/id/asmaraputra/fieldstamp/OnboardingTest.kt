package id.asmaraputra.fieldstamp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.asmaraputra.fieldstamp.app.MainActivity
import id.asmaraputra.fieldstamp.app.container
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** First launch shows onboarding once; finishing it lands on Home and is remembered. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val settings = ApplicationProvider.getApplicationContext<android.content.Context>().container.settings
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun freshInstall() = runBlocking { settings.setLaunchedBefore(false) }

    @After fun close() { scenario?.close() }

    @Test fun firstLaunchWalksOnboardingToHome() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Foto lapangan langsung berstempel").assertIsDisplayed()
        compose.onNodeWithText("Lanjut").performClick()
        compose.onNodeWithText("Rapi per proyek, jadi laporan bulanan").assertIsDisplayed()
        compose.onNodeWithText("Lanjut").performClick()
        compose.onNodeWithText("Tersimpan di perangkat Anda").assertIsDisplayed()
        compose.onNodeWithText("Mulai").performClick()

        compose.onNodeWithText("Ambil foto").assertIsDisplayed()
        compose.waitUntil(5_000) { runBlocking { settings.current().launchedBefore } }
    }

    @Test fun skipFinishesOnboarding() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Lewati").performClick()
        compose.onNodeWithText("Ambil foto").assertIsDisplayed()
        compose.waitUntil(5_000) { runBlocking { settings.current().launchedBefore } }
    }
}
