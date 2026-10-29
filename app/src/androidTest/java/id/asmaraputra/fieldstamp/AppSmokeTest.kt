package id.asmaraputra.fieldstamp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.asmaraputra.fieldstamp.app.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches without any account or network and walks the primary destinations. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun opensWithoutLoginAndNavigatesPrimaryDestinations() {
        compose.onNodeWithText("Ambil foto").assertIsDisplayed()
        compose.onNodeWithText("Impor dari galeri").assertIsDisplayed()

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
