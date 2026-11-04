package sa.factory.multaqahubcoworkin.domain.usecase

import sa.factory.multaqahubcoworkin.domain.repository.MultaqaHubRepository
import sa.factory.multaqahubcoworkin.model.MultaqaHubItem
import sa.factory.multaqahubcoworkin.model.MultaqaHubOrder
import sa.factory.multaqahubcoworkin.model.MultaqaHubTelemetry
import kotlinx.coroutines.flow.Flow

class GetCatalogUseCase(private val repository: MultaqaHubRepository) {
    operator fun invoke(): Flow<List<MultaqaHubItem>> = repository.catalogFlow
}

class ManageCartUseCase(private val repository: MultaqaHubRepository) {
    val cart = repository.activeCart
    fun add(item: MultaqaHubItem) = repository.addToCart(item)
    fun remove(item: MultaqaHubItem) = repository.removeFromCart(item)
    fun clear() = repository.clearCart()
}

class ProcessCheckoutUseCase(private val repository: MultaqaHubRepository) {
    suspend operator fun invoke(paymentMethod: String = "Mada Contactless EMV"): MultaqaHubOrder =
        repository.checkout(paymentMethod)
}

class ObserveHardwareTelemetryUseCase(private val repository: MultaqaHubRepository) {
    operator fun invoke(): Flow<MultaqaHubTelemetry> = repository.telemetryFlow
}
