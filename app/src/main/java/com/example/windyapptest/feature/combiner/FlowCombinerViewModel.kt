package com.example.windyapptest.feature.combiner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DELAY = 100L
private const val MAXIMUM_NUMBER_OF_FLOWS = 10_000

internal class FlowCombinerViewModel : ViewModel() {

    private var combinerJob: Job? = null
    private val _uiState = MutableStateFlow(FlowCombinerState())

    val uiState: StateFlow<FlowCombinerState> = _uiState

    fun changeNumberOfFlows(numberOfFlows: String) {
        _uiState.update { state ->
            val filteredNumberOfFlows = numberOfFlows.filter { it.isDigit() }
            if (filteredNumberOfFlows.toIntOrNull() ?: 0 < MAXIMUM_NUMBER_OF_FLOWS) {
                state.copy(numberOfFlows = filteredNumberOfFlows)
            } else {
                state.copy(numberOfFlows = MAXIMUM_NUMBER_OF_FLOWS.toString())
            }
        }
    }

    fun startCombineFlow() {
        combinerJob?.cancel()
        _uiState.update { oldState -> oldState.copy(computations = "") }

        val numberOfFlows = uiState.value.numberOfFlows.toInt()
        val listOfFlows = mutableListOf<Flow<String>>()
        for (i in 1..numberOfFlows) {
            listOfFlows.add(flowOf(i.toString()).onEach { delay(DELAY * i) })
        }

        combinerJob = listOfFlows.flowCombiner { accumulation, value ->
            if (accumulation.isNullOrBlank()) {
                value
            } else {
                "$accumulation\n$value"
            }
        }.onEach { computations ->
            _uiState.update { oldState -> oldState.copy(computations = "Результат:\n$computations") }
        }.launchIn(viewModelScope)

        // Несовсем понял задания можно ли было использовать операторы поэтому написал с нюля ниже вариант с операторами
        // combinerJob = listOfFlows.merge().runningFold("") { accumulator, value ->
        //     if (accumulator.isEmpty()) {
        //         value
        //     } else {
        //         "$accumulator\n$value"
        //     }
        // }.onEach { computations ->
        //     _uiState.update { oldState -> oldState.copy(computations = "Результат:\n$computations") }
        // }.launchIn(viewModelScope)
    }
}

private fun <T> List<Flow<T>>.flowCombiner(operation: (accumulation: T?, value: T) -> T): Flow<T> {
    val flows = this
    return channelFlow {
        var accumulation: T? = null
        for (flow in flows) {
            launch {
                flow.collect { value ->
                    accumulation = operation.invoke(accumulation, value)
                    accumulation?.let { send(it) }
                }
            }
        }
    }
}
