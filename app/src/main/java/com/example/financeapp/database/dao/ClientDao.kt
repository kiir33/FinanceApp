package com.example.financeapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.financeapp.database.entities.Client
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: Client): Long

    @Update
    suspend fun updateClient(client: Client)

    @Query("SELECT * FROM clients ORDER BY name ASC")
    fun getAllClients(): Flow<List<Client>>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientById(id: Long): Client?

    @Query("SELECT * FROM clients WHERE clientId = :clientId LIMIT 1")
    suspend fun getClientByClientId(clientId: Int): Client?

    @Query("SELECT * FROM clients WHERE name LIKE :query OR phoneNumber LIKE :query OR CAST(clientId AS TEXT) LIKE :query ORDER BY name ASC")
    fun searchClients(query: String): Flow<List<Client>>

    @Query("SELECT COUNT(*) FROM clients")
    fun getClientCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM clients WHERE phoneNumber = :phoneNumber")
    suspend fun countByPhoneNumber(phoneNumber: String): Int

    @Query("SELECT COUNT(*) FROM clients WHERE clientId = :clientId AND name = :name")
    suspend fun countByClientIdAndName(clientId: Int, name: String): Int
}
