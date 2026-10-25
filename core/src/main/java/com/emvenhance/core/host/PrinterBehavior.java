package com.emvenhance.core.host;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;

public interface PrinterBehavior {

    Completable print(List<String> lines);
}
