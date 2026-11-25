package com.iqraa.tasmee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Standard viewModelScope test extension — swaps Dispatchers.Main for the unconfined test
 * dispatcher so MainViewModel's init coroutine runs eagerly under test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRuleExtension :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
