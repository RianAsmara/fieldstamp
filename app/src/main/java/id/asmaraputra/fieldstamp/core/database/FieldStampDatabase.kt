package id.asmaraputra.fieldstamp.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local source of truth. Images live on disk; only app-relative paths and metadata are stored here.
 *
 * Migration policy: every schema change bumps [version], exports a schema JSON to app/schemas, and ships an
 * explicit Migration in [MIGRATIONS]. Destructive fallback is never enabled, so a missing migration fails
 * loudly in testing instead of erasing a user's documentation.
 */
@Database(
    entities = [
        ProjectEntity::class,
        ActivityEntity::class,
        TemplateEntity::class,
        PhotoEntity::class,
        ReportEntity::class,
        ReportPhotoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FieldStampDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun activityDao(): ActivityDao
    abstract fun photoDao(): PhotoDao
    abstract fun templateDao(): TemplateDao
    abstract fun reportDao(): ReportDao

    companion object {
        const val NAME = "fieldstamp.db"

        /** v2: human-readable place name for photos. Additive; existing rows get NULL. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN locationName TEXT")
            }
        }

        val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)

        fun create(context: Context): FieldStampDatabase =
            Room.databaseBuilder(context, FieldStampDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
