package com.iqraa.feature.recite

import android.content.Context
import com.iqraa.core.asr.ModelInfo
import com.iqraa.core.asr.RecitationRecognizer
import com.iqraa.core.asr.SherpaOnnxRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V-048: loads the bundled sherpa models on demand (§8.2; model load ~300–500 ms, once per
 * process). §11 semantics live in [ReciteFlowViewModel] (retry once → other bundled model);
 * this provider only constructs + loads + caches. The pinned SHAs/revs are the ones the
 * asset pipeline fetches and CI verifies (V-006).
 */
@Singleton
class RecognizerProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private var cached: Pair<String, RecitationRecognizer>? = null
        private val mutex = Mutex()

        suspend fun get(modelId: String = SMALL_ID): RecitationRecognizer =
            mutex.withLock {
                val c = cached
                if (c != null && c.first == modelId) return@withLock c.second
                c?.second?.close() // switching models: release the old recognizer
                val recognizer = SherpaOnnxRecognizer(context = context, info = infoFor(modelId))
                try {
                    recognizer.load()
                } catch (t: Throwable) {
                    recognizer.close()
                    throw t
                }
                cached = modelId to recognizer
                recognizer
            }

        private fun infoFor(modelId: String): ModelInfo =
            when (modelId) {
                TINY_ID -> TINY
                else -> SMALL
            }

        companion object {
            const val SMALL_ID = "quran-asr-small"
            const val TINY_ID = "quran-asr-tiny"

            /** Pinned (V-006): fetched by scripts/fetch_models.py, SHA-verified by CI. */
            val SMALL =
                ModelInfo(
                    id = SMALL_ID,
                    revision = "0c2744b9d70aaf45ba574c30c5d318c575258e66",
                    sha256 = "88f9df290d57b8890275d79187b77515aeeb12626a42bc76cbd0f8bfcaff00f0",
                )

            val TINY =
                ModelInfo(
                    id = TINY_ID,
                    revision = "a291b33fd5a31cc4f212363ee1ba2d56d9710b49",
                    sha256 = "58de6db034bb4f6516b3bc1c68e7a0ac4e77abffe603a550ee87b39dcd0c7ac6",
                )
        }
    }
