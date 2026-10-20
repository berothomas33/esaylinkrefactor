package com.emvenhance

import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.network.env.EnvironmentProvider
import com.emvenhance.network.onboarding.OnboardingClient
import com.emvenhance.network.onboarding.OnboardingState
import com.emvenhance.vendor.TerminalFactory
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.utils.LogUtils

/**
 * Boots one vendor-agnostic [PosTerminal]. UI never sees Pax/Ingenico/Fake types.
 *
 * Which vendor gets built is a Gradle product flavor (pax/ingenico/fake), not a runtime switch —
 * each flavor compiles only its own `vendor.*` sources and provides its own [TerminalFactory].
 * Which host `EnvironmentProvider` points at is a second, independent flavor dimension
 * ("environment" — prod/envtest/uat/uatr3/bank/pos_as_atm/dss); [EnvironmentProvider.setBaseUrl]
 * must run before [TerminalFactory.create], since building a vendor terminal is what constructs
 * its `CommunicationBehavior` (PAX: `SaleCommunicationBehavior`).
 *
 * [onboarding] / [onboardingState] are exposed for a setup screen to run the onboarding cycle and
 * check its status — onboarding isn't auto-run at startup or gated on here, since real onboarding
 * is a one-time provisioning action a technician drives, not something that should fire network
 * calls unconditionally on every launch.
 */
class EmvEnhanceApp : BaseApplication() {

    lateinit var terminal: PosTerminal
        private set

    lateinit var onboarding: OnboardingClient
        private set

    lateinit var onboardingState: OnboardingState
        private set

    override fun onCreate() {
        super.onCreate()

        LogUtils.i(TAG, "vendor=" + BuildConfig.VENDOR + " baseUrl=" + BuildConfig.baseUrl)
        EnvironmentProvider.setBaseUrl(BuildConfig.baseUrl)
        onboarding = OnboardingClient()
        onboardingState = OnboardingState(this)
        terminal = TerminalFactory.create(this)
    }

    private companion object {
        const val TAG = "EmvEnhanceApp"
    }
}
