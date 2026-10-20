package com.reelsloth.ai

/** A local language model that is loaded for one conversation and released afterwards. */
interface LanguageModel {
    /** True when the model file is present and verified. Cheap; does not load the model. */
    suspend fun isAvailable(): Boolean

    /** Loads the model, runs [block] with one conversation, then releases everything. */
    suspend fun <T> withChat(block: suspend (LanguageModelChat) -> T): T
}

fun interface LanguageModelChat {
    /** Sends a user turn and returns the model's whole reply. */
    suspend fun send(message: String): String
}
