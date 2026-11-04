package sa.factory.multaqahubcoworkin.domain.repository

import sa.factory.multaqahubcoworkin.model.MultaqaHubItem
import sa.factory.multaqahubcoworkin.model.MultaqaHubOrder
import sa.factory.multaqahubcoworkin.model.MultaqaHubTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MultaqaHubRepository {
    val catalogFlow: Flow<List<MultaqaHubItem>>
    val activeCart: StateFlow<List<MultaqaHubItem>>
    val telemetryFlow: Flow<MultaqaHubTelemetry>
    
    fun addToCart(item: MultaqaHubItem)
    fun removeFromCart(item: MultaqaHubItem)
    fun clearCart()
    suspend fun checkout(paymentMethod: String): MultaqaHubOrder
    suspend fun refreshCatalog()
}
