package sa.factory.multaqahubcoworkin.data.remote

import sa.factory.multaqahubcoworkin.model.MultaqaHubItem
import sa.factory.multaqahubcoworkin.model.MultaqaHubOrder

interface MultaqaHubApiService {
    suspend fun fetchCatalog(): List<MultaqaHubItem>
    suspend fun submitOrder(order: MultaqaHubOrder): Boolean
}
