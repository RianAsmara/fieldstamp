package id.asmaraputra.fieldstamp

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.asmaraputra.fieldstamp.app.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches without any account or network and walks the primary destinations. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    // Starts on Home: the camera would otherwise open on top and raise its permission dialog.
    @Before fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SKIP_CAMERA, true)
        scenario = ActivityScenario.launch(intent)
    }

    @After fun close() = scenario.close()

    @Test fun opensWithoutLoginAndNavigatesPrimaryDestinations() {
        compose.onNodeWithText("Ambil foto").assertIsDisplayed()
        compose.onNodeWithContentDescription("Impor dari galeri").assertIsDisplayed()

        compose.onAllNodesWithText("Foto")[0].performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Laporan")[0].performClick()
        compose.onNodeWithContentDescription("Buat laporan").assertIsDisplayed()
        compose.onAllNodesWithText("Setelan")[0].performClick()
        compose.onNodeWithText("Catat lokasi").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun reportCreationFlowOpensBuilder() {
        compose.onAllNodesWithText("Laporan")[0].performClick()
        compose.onNodeWithContentDescription("Buat laporan").performClick()
        compose.onNodeWithText("Buat draf laporan").performClick()
        // The draft is inserted on a database coroutine before navigation; wait for the builder to appear.
        compose.waitUntilExactlyOneExists(hasText("Pengaturan"), timeoutMillis = 10_000)
        compose.onNodeWithText("Pengaturan").assertIsDisplayed()
        compose.onNodeWithText("Buat PDF").assertIsDisplayed()
    }
}
