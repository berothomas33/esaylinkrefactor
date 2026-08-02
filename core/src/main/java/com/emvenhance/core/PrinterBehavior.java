package com.emvenhance.core;

import java.util.List;

/**
 * Printer behavior: puts receipt lines on paper.
 */
public interface PrinterBehavior {

    /**
     * @return true when the receipt printed successfully
     */
    boolean print(List<String> lines);
}
