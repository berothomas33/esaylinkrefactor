package com.emvenhance.vendor;

import com.pax.commonlib.application.BaseApplication;

/**
 * PAX flavor's application base. The PAX SDK modules (commonutils, poslib, bizlib, bizentity)
 * reach the app through {@code BaseApplication.getAppContext()}, so the PAX build's application
 * must be a {@link BaseApplication}. {@code EmvEnhanceApp} extends this, and so stays free of PAX
 * imports.
 */
public class VendorApplication extends BaseApplication {
}
