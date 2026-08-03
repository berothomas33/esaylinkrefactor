package com.emvenhance.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.emvenhance.core.PosTerminal;

public class MainViewModelFactory implements ViewModelProvider.Factory {

    private final PosTerminal terminal;

    public MainViewModelFactory(PosTerminal terminal) {
        this.terminal = terminal;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(MainViewModel.class)) {
            return (T) new MainViewModel(terminal);
        }
        throw new IllegalArgumentException("Unknown ViewModel: " + modelClass);
    }
}
