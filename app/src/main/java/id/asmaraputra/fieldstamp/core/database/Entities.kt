package id.asmaraputra.fieldstamp.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import id.asmaraputra.fieldstamp.core.model.PhotoSource
import id.asmaraputra.fieldstamp.core.model.ReportLayout
import id.asmaraputra.fieldstamp.core.model.ReportStatus

@Entity(tableName = "projects", indices = [Index("archivedAt")])
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
)

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("projectId")],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long?,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourcePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val configurationJson: String,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ActivityEntity::class, ["id"], ["activityId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(TemplateEntity::class, ["id"], ["templateId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("captureEpochDay"),
        Index("capturedAtEpochMillis"),
        Index("projectId"),
        Index("activityId"),
        Index("templateId"),
    ],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long?,
    val activityId: Long?,
    val source: PhotoSource,
    val originalPath: String,
    val renderedPath: String?,
    val thumbnailPath: String?,
    val capturedAtEpochMillis: Long,
    val captureTimeZoneId: String,
    /** LocalDate.toEpochDay() of the capture, evaluated in [captureTimeZoneId] when the record is created. */
    val captureEpochDay: Long,
    val latitude: Double?,
    val longitude: Double?,
    val locationAccuracyMeters: Float?,
    val locationCapturedAtEpochMillis: Long?,
    /** Added in schema v2. */
    val locationName: String? = null,
    val caption: String,
    val category: String?,
    val templateId: Long?,
    val widthPx: Int,
    val heightPx: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ActivityEntity::class, ["id"], ["activityId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("projectId"), Index("activityId"), Index("periodStartEpochDay")],
)
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val projectId: Long?,
    val activityId: Long?,
    val periodStartEpochDay: Long,
    val periodEndEpochDay: Long,
    val status: ReportStatus,
    val notes: String = "",
    val closingNotes: String = "",
    val preparedBy: String = "",
    val includeGps: Boolean = true,
    val includeSignatureBlock: Boolean = true,
    val layout: ReportLayout = ReportLayout.STANDARD,
    val useBranding: Boolean = false,
    val useStampedPhotos: Boolean = true,
    val pdfPath: String? = null,
    val generatedAt: Long? = null,
    val pdfOutdated: Boolean = false,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "report_photos",
    foreignKeys = [
        ForeignKey(ReportEntity::class, ["id"], ["reportId"], onDelete = ForeignKey.CASCADE),
        // Deleting a photo keeps the report row (photoRecordId becomes null) so the report
        // builder can tell the user exactly what went missing instead of silently dropping it.
        ForeignKey(PhotoEntity::class, ["id"], ["photoRecordId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("reportId"),
        Index("photoRecordId"),
        Index(value = ["reportId", "reportEpochDay", "displayOrder"]),
    ],
)
data class ReportPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: Long,
    val photoRecordId: Long?,
    val reportEpochDay: Long,
    val displayOrder: Int,
    val captionOverride: String?,
    /** JSON-encoded ReportPhotoSnapshot, written at generation time. */
    val metadataSnapshot: String?,
)

data class ReportPhotoWithPhoto(
    @Embedded val entry: ReportPhotoEntity,
    @Relation(parentColumn = "photoRecordId", entityColumn = "id")
    val photo: PhotoEntity?,
)

data class ReportWithCount(
    @Embedded val report: ReportEntity,
    @ColumnInfo(name = "photoCount") val photoCount: Int,
)

data class ProjectWithCounts(
    @Embedded val project: ProjectEntity,
    @ColumnInfo(name = "photoCount") val photoCount: Int,
    @ColumnInfo(name = "activityCount") val activityCount: Int,
)
