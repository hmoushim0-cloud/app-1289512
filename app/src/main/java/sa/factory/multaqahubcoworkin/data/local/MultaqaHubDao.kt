package sa.factory.multaqahubcoworkin.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MultaqaHubDao {
    @Query("SELECT * FROM coworking_items_cache ORDER BY id ASC")
    fun observeAllItems(): Flow<List<MultaqaHubEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MultaqaHubEntity>)

    @Query("DELETE FROM coworking_items_cache")
    suspend fun clearAll()
}
