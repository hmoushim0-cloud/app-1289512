package sa.factory.multaqahubcoworkin.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MultaqaHubEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun coworkingDao(): MultaqaHubDao
}
