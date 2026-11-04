package sa.factory.multaqahubcoworkin.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.multaqahubcoworkin.domain.usecase.*
import sa.factory.multaqahubcoworkin.model.MultaqaHubItem
import sa.factory.multaqahubcoworkin.model.MultaqaHubOrder
import sa.factory.multaqahubcoworkin.model.MultaqaHubTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MultaqaHubUiState(
    val catalog: List<MultaqaHubItem> = emptyList(),
    val filteredCatalog: List<MultaqaHubItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<MultaqaHubItem> = emptyList(),
    val telemetry: MultaqaHubTelemetry? = null,
    val lastOrder: MultaqaHubOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class MultaqaHubViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MultaqaHubUiState())
    val uiState: StateFlow<MultaqaHubUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: MultaqaHubItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: MultaqaHubItem) = manageCartUseCase.remove(item)

    fun checkout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase()
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}
