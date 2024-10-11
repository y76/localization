package com.google.apps.hellouwb.ui.home

import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingPosition
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbClientSessionScope
import androidx.core.uwb.UwbComplexChannel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.apps.hellouwb.data.UwbRangingControlSource
import com.google.apps.uwbranging.EndpointEvents
import com.google.apps.uwbranging.UwbEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

class HomeViewModel(uwbRangingControlSource: UwbRangingControlSource) : ViewModel() {

  private val _uiState: MutableStateFlow<ExtendedHomeUiState> =
    MutableStateFlow(ExtendedHomeUiStateImpl(listOf(), listOf(), emptyMap(), false))

  private val endpoints = mutableListOf<UwbEndpoint>()
  private val endpointPositions = mutableMapOf<UwbEndpoint, RangingPosition>()
  private val rangingInfoMap = mutableMapOf<UwbEndpoint, RangingInfo>()
  private var isRanging = false

  private fun updateUiState(): ExtendedHomeUiState {
    return ExtendedHomeUiStateImpl(
      endpoints
        .mapNotNull { endpoint ->
          endpointPositions[endpoint]?.let { position ->
            ConnectedEndpoint(endpoint, position, rangingInfoMap[endpoint])
          }
        }
        .toList(),
      endpoints.filter { !endpointPositions.containsKey(it) }.toList(),
      rangingInfoMap.toMap(),
      isRanging
    )
  }

  val uiState = _uiState.asStateFlow()

  init {
    uwbRangingControlSource
      .observeRangingResults()
      .onEach { result ->
        when (result) {
          is EndpointEvents.EndpointFound -> {
            endpoints.add(result.endpoint)
            // We'll use placeholder values for now, and update them when we have more information
            rangingInfoMap[result.endpoint] = result.rangingParameters.complexChannel?.let {
              RangingInfo(
                configId = result.rangingParameters.uwbConfigType,
                endpointAddress = UwbAddress(result.endpoint.id.toByteArray()),
                complexChannel = it,//UwbComplexChannel(9, 0), // Default values, update if available
                sessionId = result.rangingParameters.sessionId // Placeholder, update when available
              )
            }!!
          }
          is EndpointEvents.UwbDisconnected -> {
            endpointPositions.remove(result.endpoint)
            rangingInfoMap.remove(result.endpoint)
          }
          is EndpointEvents.PositionUpdated -> endpointPositions[result.endpoint] = result.position
          is EndpointEvents.EndpointLost -> {
            endpoints.remove(result.endpoint)
            endpointPositions.remove(result.endpoint)
            rangingInfoMap.remove(result.endpoint)
          }
          else -> return@onEach
        }
        _uiState.update { updateUiState() }
      }
      .launchIn(viewModelScope)

    uwbRangingControlSource.isRunning
      .onEach { running ->
        isRanging = running
        if (!running) {
          endpoints.clear()
          endpointPositions.clear()
          rangingInfoMap.clear()
        }
        _uiState.update { updateUiState() }
      }
      .launchIn(CoroutineScope(Dispatchers.IO))
  }

  private data class ExtendedHomeUiStateImpl(
    override val connectedEndpoints: List<ConnectedEndpoint>,
    override val disconnectedEndpoints: List<UwbEndpoint>,
    override val rangingInfoMap: Map<UwbEndpoint, RangingInfo>,
    override val isRanging: Boolean,
  ) : ExtendedHomeUiState

  companion object {
    fun provideFactory(
      uwbRangingControlSource: UwbRangingControlSource
    ): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          return HomeViewModel(uwbRangingControlSource) as T
        }
      }
  }
}


interface HomeUiState {
  val connectedEndpoints: List<ConnectedEndpoint>
  val disconnectedEndpoints: List<UwbEndpoint>
  val isRanging: Boolean
}

data class ConnectedEndpoint(
  val endpoint: UwbEndpoint,
  val position: RangingPosition,
  val rangingInfo: RangingInfo?
)

