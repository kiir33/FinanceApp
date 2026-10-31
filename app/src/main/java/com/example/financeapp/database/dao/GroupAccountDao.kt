package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.financeapp.database.entities.GroupAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupAccount(account: GroupAccount): Long

    @Update
    suspend fun updateGroupAccount(account: GroupAccount)

    @Query("SELECT * FROM group_accounts WHERE id = :id LIMIT 1")
    suspend fun getGroupAccountById(id: Long = 1): GroupAccount?

    @Query("SELECT * FROM group_accounts WHERE id = :id LIMIT 1")
    fun observeGroupAccountById(id: Long = 1): Flow<GroupAccount?>

    @Query("SELECT * FROM group_accounts ORDER BY id ASC LIMIT 1")
    fun observeGroupAccount(): Flow<GroupAccount?>
}
