package com.halfbloodcoder.scrollsloth.ai

import com.google.ai.edge.litertlm.ActivationDataType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs a `.litertlm` model with LiteRT-LM. The engine is created for each [withChat] call and
 * closed straight after, so the model only occupies memory while commentary is being written.
 *
 * The `-gpu` model file only contains GPU sections, so there is no CPU fallback; if the GPU cannot
 * load it the caller falls back to templates. Activations are FLOAT32: with the default FLOAT16,
 * Gemma 4 E2B emits garbled tokens on some Samsung GPUs (LiteRT-LM issue #3879, seen on an S24 Ultra).
 * The compiled GPU cache lives in its own directory so kernels built for FLOAT16 are not reused.
 */
class LiteRtLanguageModel(
    private val modelFile: suspend () -> File?,
    private val cacheDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LanguageModel {
    private val mutex = Mutex()

    override suspend fun isAvailable(): Boolean = modelFile() != null

    override suspend fun <T> withChat(block: suspend (LanguageModelChat) -> T): T = mutex.withLock {
        withContext(dispatcher) {
            val file = modelFile() ?: throw IllegalStateException("The model is not downloaded.")
            openEngine(file).use { engine ->
                engine.createConversation(conversationConfig()).use { conversation ->
                    block(LanguageModelChat { message -> conversation.sendMessage(message).text() })
                }
            }
        }
    }

    private fun openEngine(file: File): Engine {
        val gpuCache = File(cacheDir, GPU_CACHE_DIR).apply { mkdirs() }
        val engine = Engine(
            EngineConfig(
                modelPath = file.path,
                backend = Backend.GPU(),
                maxNumTokens = MAX_TOKENS,
                cacheDir = gpuCache.path,
                activationDataType = ActivationDataType.FLOAT32,
            ),
        )
        try {
            engine.initialize()
        } catch (e: Exception) {
            engine.close()
            throw e
        }
        return engine
    }

    private fun Message.text(): String =
        contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    companion object {
        const val MAX_OUTPUT_TOKENS = 60

        /** Prompt (~250 tokens), reply, one retry and its reply fit comfortably. */
        const val MAX_TOKENS = 1024
        private const val GPU_CACHE_DIR = "litertlm-gpu-f32"

        /** Greedy decoding: top-k of 1 always picks the most likely token, so the temperature barely matters. */
        fun conversationConfig() = ConversationConfig(
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.1, seed = 0),
            maxOutputToken = MAX_OUTPUT_TOKENS,
            thinkingConfig = ThinkingConfig(enableThinking = false),
        )
    }
}
