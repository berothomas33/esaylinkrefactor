package com.emvenhance.network

/** A sale/exchange step failed — either a transport-level error or a non-success envelope. */
class SaleException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
