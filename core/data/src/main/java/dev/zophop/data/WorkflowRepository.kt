package dev.zophop.data

import dev.zophop.domain.model.Workflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface WorkflowRepository {
    fun getAllWorkflowsFlow(): Flow<List<Workflow>>
    suspend fun getActiveWorkflows(): List<Workflow>
    suspend fun getWorkflowById(id: String): Workflow?
    suspend fun saveWorkflow(workflow: Workflow)
    suspend fun setWorkflowEnabled(id: String, isEnabled: Boolean)
    suspend fun deleteWorkflow(id: String)
    suspend fun getWorkflowCount(): Int
    suspend fun saveExecutionLog(log: WorkflowExecutionLogEntity)
    fun getRecentExecutionLogsFlow(limit: Int = 50): Flow<List<WorkflowExecutionLogEntity>>
    fun getLogsForWorkflowFlow(workflowId: String, limit: Int = 50): Flow<List<WorkflowExecutionLogEntity>>
    suspend fun clearLogsForWorkflow(workflowId: String)
}

class RoomWorkflowRepository(
    private val workflowDao: WorkflowDao
) : WorkflowRepository {

    override fun getAllWorkflowsFlow(): Flow<List<Workflow>> {
        return workflowDao.getAllWorkflowsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getActiveWorkflows(): List<Workflow> {
        return workflowDao.getActiveWorkflows().map { it.toDomain() }
    }

    override suspend fun getWorkflowById(id: String): Workflow? {
        return workflowDao.getWorkflowById(id)?.toDomain()
    }

    override suspend fun saveWorkflow(workflow: Workflow) {
        val updated = workflow.copy(updatedAt = System.currentTimeMillis())
        workflowDao.insertOrUpdateWorkflow(WorkflowEntity.fromDomain(updated))
    }

    override suspend fun setWorkflowEnabled(id: String, isEnabled: Boolean) {
        val workflow = getWorkflowById(id) ?: return
        saveWorkflow(workflow.copy(isEnabled = isEnabled))
    }

    override suspend fun deleteWorkflow(id: String) {
        workflowDao.deleteWorkflowById(id)
    }

    override suspend fun getWorkflowCount(): Int {
        return workflowDao.getWorkflowCount()
    }

    override suspend fun saveExecutionLog(log: WorkflowExecutionLogEntity) {
        workflowDao.insertExecutionLog(log)
    }

    override fun getRecentExecutionLogsFlow(limit: Int): Flow<List<WorkflowExecutionLogEntity>> {
        return workflowDao.getRecentExecutionLogsFlow(limit)
    }

    override fun getLogsForWorkflowFlow(workflowId: String, limit: Int): Flow<List<WorkflowExecutionLogEntity>> {
        return workflowDao.getLogsForWorkflowFlow(workflowId, limit)
    }

    override suspend fun clearLogsForWorkflow(workflowId: String) {
        workflowDao.clearLogsForWorkflow(workflowId)
    }
}
