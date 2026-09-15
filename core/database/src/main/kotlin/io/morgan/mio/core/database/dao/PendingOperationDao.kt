package io.morgan.mio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.morgan.mio.core.database.entity.PendingOperationDependencyEntity
import io.morgan.mio.core.database.entity.PendingOperationEntity
import io.morgan.mio.core.database.entity.PendingOperationGroupEntity

@Dao
public abstract class PendingOperationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertGroup(group: PendingOperationGroupEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertOperations(operations: List<PendingOperationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    public abstract suspend fun insertDependencies(dependencies: List<PendingOperationDependencyEntity>)

    @Query("SELECT * FROM pending_operation_group WHERE operation_group_id = :groupId")
    public abstract suspend fun getGroup(groupId: String): PendingOperationGroupEntity?

    @Query("SELECT * FROM pending_operation WHERE operation_group_id = :groupId ORDER BY position_in_group")
    public abstract suspend fun getOperationsForGroup(groupId: String): List<PendingOperationEntity>

    @Query(
        "SELECT COUNT(*) FROM pending_operation_dependency AS dependency " +
            "INNER JOIN pending_operation AS dependent " +
            "ON dependent.operation_id = dependency.operation_id " +
            "WHERE dependent.lifecycle_state NOT IN ('delivered', 'cancelled', 'terminal_failure') " +
            "AND dependency.depends_on_operation_id IN " +
            "(SELECT operation_id FROM pending_operation WHERE operation_group_id = :groupId) " +
            "AND dependent.operation_group_id != :groupId",
    )
    public abstract suspend fun countExternalDependents(groupId: String): Int

    @Query(
        "DELETE FROM pending_operation_group " +
            "WHERE operation_group_id = :groupId AND account_id = :accountId",
    )
    public abstract suspend fun deleteGroup(
        accountId: String,
        groupId: String,
    ): Int

    @Query("SELECT * FROM pending_operation WHERE operation_id = :operationId")
    public abstract suspend fun getOperation(operationId: String): PendingOperationEntity?

    @Query(
        "SELECT * FROM pending_operation_dependency " +
            "WHERE operation_id = :operationId ORDER BY depends_on_operation_id",
    )
    public abstract suspend fun getDependenciesForOperation(
        operationId: String,
    ): List<PendingOperationDependencyEntity>

    @Query(
        "SELECT pending_operation.* FROM pending_operation " +
            "INNER JOIN pending_operation_group " +
            "ON pending_operation.operation_group_id = pending_operation_group.operation_group_id " +
            "WHERE pending_operation.account_id = :accountId " +
            "ORDER BY pending_operation_group.group_order, pending_operation.position_in_group",
    )
    public abstract suspend fun getOperationsInEnqueueOrder(accountId: String): List<PendingOperationEntity>
}
