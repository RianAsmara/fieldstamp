package id.asmaraputra.fieldstamp.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query(
        """
        SELECT p.*,
            (SELECT COUNT(*) FROM photos ph WHERE ph.projectId = p.id) AS photoCount,
            (SELECT COUNT(*) FROM activities a WHERE a.projectId = p.id) AS activityCount
        FROM projects p
        ORDER BY p.archivedAt IS NOT NULL, p.updatedAt DESC
        """,
    )
    fun observeWithCounts(): Flow<List<ProjectWithCounts>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE projectId = :projectId ORDER BY name COLLATE NOCASE")
    fun observeForProject(projectId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun get(id: Long): ActivityEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE activityId = :activityId")
    suspend fun photoCount(activityId: Long): Int

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PhotoDao {
    /**
     * Library query. Every filter is optional; a null bound means "unbounded".
     * Day bounds are inclusive epoch days of the recorded local capture date.
     */
    @Query(
        """
        SELECT * FROM photos
        WHERE (:startDay IS NULL OR captureEpochDay >= :startDay)
          AND (:endDay IS NULL OR captureEpochDay <= :endDay)
          AND (:projectId IS NULL OR projectId = :projectId)
          AND (:activityId IS NULL OR activityId = :activityId)
          AND (:missingCaptionOnly = 0 OR TRIM(caption) = '')
          AND (:missingGpsOnly = 0 OR latitude IS NULL)
          AND (:query = ''
               OR caption LIKE '%' || :query || '%'
               OR category LIKE '%' || :query || '%'
               OR locationName LIKE '%' || :query || '%'
               OR projectId IN (SELECT id FROM projects WHERE name LIKE '%' || :query || '%')
               OR activityId IN (SELECT id FROM activities WHERE name LIKE '%' || :query || '%'))
        ORDER BY capturedAtEpochMillis DESC, id DESC
        """,
    )
    fun observeFiltered(
        startDay: Long?,
        endDay: Long?,
        projectId: Long?,
        activityId: Long?,
        query: String,
        missingCaptionOnly: Boolean,
        missingGpsOnly: Boolean,
    ): Flow<List<PhotoEntity>>

    @Query(
        """
        SELECT * FROM photos
        WHERE captureEpochDay BETWEEN :startDay AND :endDay
          AND (:projectId IS NULL OR projectId = :projectId)
          AND (:activityId IS NULL OR activityId = :activityId)
        ORDER BY capturedAtEpochMillis ASC, id ASC
        """,
    )
    suspend fun inRange(startDay: Long, endDay: Long, projectId: Long?, activityId: Long?): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE captureEpochDay = :day ORDER BY capturedAtEpochMillis DESC")
    fun observeDay(day: Long): Flow<List<PhotoEntity>>

    @Query("SELECT COUNT(*) FROM photos")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM photos WHERE id = :id")
    fun observe(id: Long): Flow<PhotoEntity?>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id IN (:ids)")
    suspend fun getAll(ids: List<Long>): List<PhotoEntity>

    @Query("SELECT originalPath FROM photos UNION SELECT renderedPath FROM photos WHERE renderedPath IS NOT NULL UNION SELECT thumbnailPath FROM photos WHERE thumbnailPath IS NOT NULL")
    suspend fun allReferencedPaths(): List<String>

    @Query("SELECT COUNT(*) FROM report_photos WHERE photoRecordId = :photoId")
    suspend fun reportUsageCount(photoId: Long): Int

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY isDefault DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: Long): TemplateEntity?

    @Query("SELECT * FROM templates WHERE id = :id")
    fun observe(id: Long): Flow<TemplateEntity?>

    @Query("SELECT * FROM templates WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): TemplateEntity?

    @Insert
    suspend fun insert(template: TemplateEntity): Long

    @Update
    suspend fun update(template: TemplateEntity)

    @Query("UPDATE templates SET isDefault = (id = :id)")
    suspend fun setDefault(id: Long)

    @Query("UPDATE templates SET isDefault = 0")
    suspend fun clearDefault()

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReportDao {
    @Query(
        """
        SELECT r.*, (SELECT COUNT(*) FROM report_photos rp WHERE rp.reportId = r.id) AS photoCount
        FROM reports r
        ORDER BY r.periodStartEpochDay DESC, r.updatedAt DESC
        """,
    )
    fun observeWithCounts(): Flow<List<ReportWithCount>>

    @Query("SELECT * FROM reports WHERE id = :id")
    fun observe(id: Long): Flow<ReportEntity?>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun get(id: Long): ReportEntity?

    @Insert
    suspend fun insert(report: ReportEntity): Long

    @Update
    suspend fun update(report: ReportEntity)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT pdfPath FROM reports WHERE pdfPath IS NOT NULL")
    suspend fun allPdfPaths(): List<String>

    @Transaction
    @Query("SELECT * FROM report_photos WHERE reportId = :reportId ORDER BY reportEpochDay, displayOrder, id")
    fun observeItems(reportId: Long): Flow<List<ReportPhotoWithPhoto>>

    @Transaction
    @Query("SELECT * FROM report_photos WHERE reportId = :reportId ORDER BY reportEpochDay, displayOrder, id")
    suspend fun items(reportId: Long): List<ReportPhotoWithPhoto>

    @Query("SELECT * FROM report_photos WHERE id = :id")
    suspend fun getItem(id: Long): ReportPhotoEntity?

    @Insert
    suspend fun insertItems(items: List<ReportPhotoEntity>)

    @Update
    suspend fun updateItems(items: List<ReportPhotoEntity>)

    @Query("DELETE FROM report_photos WHERE id IN (:ids)")
    suspend fun deleteItems(ids: List<Long>)

    @Query("DELETE FROM report_photos WHERE reportId = :reportId AND photoRecordId IS NULL")
    suspend fun deleteMissingItems(reportId: Long)

    @Query("UPDATE reports SET pdfOutdated = 1, updatedAt = :now WHERE id IN (SELECT reportId FROM report_photos WHERE photoRecordId = :photoId) AND pdfPath IS NOT NULL")
    suspend fun markOutdatedForPhoto(photoId: Long, now: Long)
}
