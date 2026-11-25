package com.iqraa.feature.recite

import android.content.Context
import com.iqraa.core.align.ExpectedWord
import com.iqraa.core.align.RecitationTrackerImpl
import com.iqraa.core.align.TrackerConfig
import com.iqraa.core.asr.RecitationRecognizer
import com.iqraa.core.asr.SileroVad
import com.iqraa.core.audio.Segmenter
import com.iqraa.core.session.PipelineEngine
import com.iqraa.core.session.ReciteSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The SINGLE assembly path for a recitation pipeline (V-048, issue #11): the Hilt graph
 * and the instrumented fixture test both build through [build] — what the app runs is
 * exactly what the tests prove (no parallel construction that can drift).
 */
@Singleton
class RecitePipelineFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** One live session: tracker + session + engine + the VAD instance feeding it. */
        class RecitePipeline(
            val session: ReciteSession,
            val engine: PipelineEngine,
            val vad: SileroVad,
        )

        fun build(
            recognizer: RecitationRecognizer,
            expected: List<ExpectedWord>,
            config: TrackerConfig,
            chunkSource: ChunkSource,
        ): Pair<RecitePipeline, ReciteViewModel> {
            val tracker =
                RecitationTrackerImpl().apply {
                    reset(expected, config)
                }
            val session = ReciteSession(recognizer, tracker)
            val vad = SileroVad(context.assets, VAD_ASSET)
            val engine =
                PipelineEngine(
                    sampleRate = SAMPLE_RATE,
                    vad = { samples -> vad.isSpeech(samples) },
                    segmenter = Segmenter(),
                    session = session,
                )
            return RecitePipeline(session, engine, vad) to ReciteViewModel(session, engine, chunkSource)
        }

        private companion object {
            const val SAMPLE_RATE = 16_000
            const val VAD_ASSET = "models/silero_vad.onnx"
        }
    }
