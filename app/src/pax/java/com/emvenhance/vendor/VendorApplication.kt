package com.emvenhance.vendor

import com.pax.commonlib.application.BaseApplication

/**
 * PAX flavor's application base. The PAX SDK modules (commonutils, poslib, bizlib, bizentity)
 * reach the app through `BaseApplication.getAppContext()`, so the PAX build's application must be
 * a [BaseApplication]. `EmvEnhanceApp` extends this, and so stays free of PAX imports.
 */
open class VendorApplication : BaseApplication()
