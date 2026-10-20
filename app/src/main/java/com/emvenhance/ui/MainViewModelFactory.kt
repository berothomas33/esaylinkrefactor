package com.emvenhance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.emvenhance.core.terminal.PosTerminal

class MainViewModelFactory(private val terminal: PosTerminal) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(terminal) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: $modelClass")
    }
}
