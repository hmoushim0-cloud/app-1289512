package sa.factory.multaqahubcoworkin.di

import sa.factory.multaqahubcoworkin.data.repository.MultaqaHubRepositoryImpl
import sa.factory.multaqahubcoworkin.domain.repository.MultaqaHubRepository
import sa.factory.multaqahubcoworkin.domain.usecase.*
import sa.factory.multaqahubcoworkin.hardware.HardwareSensorDriver
import sa.factory.multaqahubcoworkin.ui.viewmodel.MultaqaHubViewModel

class DiContainer {
    val sensorDriver = HardwareSensorDriver()
    val repository: MultaqaHubRepository = MultaqaHubRepositoryImpl(sensorDriver)

    val getCatalogUseCase = GetCatalogUseCase(repository)
    val manageCartUseCase = ManageCartUseCase(repository)
    val processCheckoutUseCase = ProcessCheckoutUseCase(repository)
    val observeHardwareTelemetryUseCase = ObserveHardwareTelemetryUseCase(repository)

    fun provideViewModel(): MultaqaHubViewModel = MultaqaHubViewModel(
        getCatalogUseCase,
        manageCartUseCase,
        processCheckoutUseCase,
        observeHardwareTelemetryUseCase
    )
}
