package com.emvenhance.core.param;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;
import java.io.File;
import java.util.List;

public interface EmvParams {

    List<String> categories();

    List<Row> rows(String category);

    @Nullable
    List<Row> cachedRows(String category);

    Single<String> download();

    void seedDefaults();

    List<String> downloadedFiles();

    @Nullable
    File downloadedFile(String name);

    interface Row {

        String title();

        String details();
    }
}
