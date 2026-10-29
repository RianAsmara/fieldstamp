package id.asmaraputra.fieldstamp

import id.asmaraputra.fieldstamp.core.database.FieldStampDatabase

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FieldStampDatabase::class.java)

    @Test fun migrate1To2KeepsPhotosAndAddsEmptyPlaceName() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                """INSERT INTO photos (id, projectId, activityId, source, originalPath, renderedPath, thumbnailPath,
                   capturedAtEpochMillis, captureTimeZoneId, captureEpochDay, latitude, longitude, locationAccuracyMeters,
                   locationCapturedAtEpochMillis, caption, category, templateId, widthPx, heightPx, createdAt, updatedAt)
                   VALUES (1, NULL, NULL, 'CAMERA', 'photos/original/a.jpg', NULL, NULL, 1790000000000, 'Asia/Jakarta', 20720,
                   -6.2, 106.8, 5.0, 1790000000000, 'Sebelum migrasi', NULL, NULL, 100, 100, 0, 0)""",
            )
        }
        helper.runMigrationsAndValidate(dbName, 2, true, FieldStampDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT caption, latitude, locationName FROM photos WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals("Sebelum migrasi", c.getString(0))
                assertEquals(-6.2, c.getDouble(1), 0.0)
                assertNull(c.getString(2))
            }
        }
    }
}
