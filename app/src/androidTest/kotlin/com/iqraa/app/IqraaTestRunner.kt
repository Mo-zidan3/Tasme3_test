package com.iqraa.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * V-048: the Hilt test runner — HiltTestApplication backs @AndroidEntryPoint activities
 * in instrumented tests so @BindValue can replace bindings per test class (ReaderErrorState
 * / ReaderEmptyState inject failing/empty surah sources). Tests that do not use Hilt are
 * unaffected (same classpath, same behavior — they simply run under this Application).
 */
class IqraaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
