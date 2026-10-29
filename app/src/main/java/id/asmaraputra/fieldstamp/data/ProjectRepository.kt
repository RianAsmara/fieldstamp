package id.asmaraputra.fieldstamp.data

import id.asmaraputra.fieldstamp.core.database.ActivityDao
import id.asmaraputra.fieldstamp.core.database.ActivityEntity
import id.asmaraputra.fieldstamp.core.database.ProjectDao
import id.asmaraputra.fieldstamp.core.database.ProjectEntity
import id.asmaraputra.fieldstamp.core.database.toModel
import id.asmaraputra.fieldstamp.core.model.FieldActivity
import id.asmaraputra.fieldstamp.core.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProjectSummary(val project: Project, val photoCount: Int, val activityCount: Int)

class ProjectRepository(
    private val projects: ProjectDao,
    private val activities: ActivityDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeSummaries(): Flow<List<ProjectSummary>> = projects.observeWithCounts().map { rows ->
        rows.map { ProjectSummary(it.project.toModel(), it.photoCount, it.activityCount) }
    }

    fun observeProjects(): Flow<List<Project>> = projects.observeAll().map { list -> list.map { it.toModel() } }

    fun observeProject(id: Long): Flow<Project?> = projects.observe(id).map { it?.toModel() }

    fun observeActivities(): Flow<List<FieldActivity>> = activities.observeAll().map { list -> list.map { it.toModel() } }

    fun observeActivities(projectId: Long): Flow<List<FieldActivity>> =
        activities.observeForProject(projectId).map { list -> list.map { it.toModel() } }

    suspend fun createProject(name: String, description: String): Long {
        val now = clock()
        return projects.insert(ProjectEntity(name = name.trim(), description = description.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun updateProject(id: Long, name: String, description: String) {
        val existing = projects.get(id) ?: return
        projects.update(existing.copy(name = name.trim(), description = description.trim(), updatedAt = clock()))
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val existing = projects.get(id) ?: return
        val now = clock()
        projects.update(existing.copy(archivedAt = if (archived) now else null, updatedAt = now))
    }

    /** Photos, activities and reports keep existing; their project reference is cleared. */
    suspend fun deleteProject(id: Long) = projects.delete(id)

    suspend fun createActivity(projectId: Long?, name: String, description: String): Long {
        val now = clock()
        return activities.insert(ActivityEntity(projectId = projectId, name = name.trim(), description = description.trim(), createdAt = now, updatedAt = now))
    }

    suspend fun updateActivity(id: Long, name: String, description: String) {
        val existing = activities.get(id) ?: return
        activities.update(existing.copy(name = name.trim(), description = description.trim(), updatedAt = clock()))
    }

    suspend fun activityPhotoCount(id: Long): Int = activities.photoCount(id)

    suspend fun deleteActivity(id: Long) = activities.delete(id)
}
