package sa.factory.multaqahubcoworkin.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import sa.factory.multaqahubcoworkin.model.MultaqaHubItem

@Entity(tableName = "coworking_items_cache")
data class MultaqaHubEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priceSar: Double,
    val badge: String,
    val category: String,
    val status: String
) {
    fun toDomain(): MultaqaHubItem = MultaqaHubItem(
        id = id,
        title = title,
        description = description,
        priceSar = priceSar,
        badge = badge,
        category = category,
        status = status
    )
}
