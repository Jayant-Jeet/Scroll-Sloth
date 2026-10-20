package com.halfbloodcoder.scrollsloth.ai

/** A downloadable model file, pinned to one exact revision and checksum. */
data class ModelSpec(
    val displayName: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
    val licenseUrl: String,
) {
    companion object {
        /**
         * Gemma 4 E2B instruction-tuned, LiteRT-LM GPU build, from litert-community on Hugging Face.
         * Apache 2.0 and not gated, so the download needs no account or token. The URL is pinned to a
         * commit so the file can never change under the checksum.
         */
        val GEMMA_4_E2B = ModelSpec(
            displayName = "Gemma 4 E2B (instruction-tuned)",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
                "41c5569623a7c189d6eec3e5225260c58cedbce7/gemma-4-E2B-it-gpu.litertlm",
            fileName = "gemma-4-E2B-it-gpu.litertlm",
            sizeBytes = 2_008_432_640L,
            sha256 = "a53a59001894c58e6bdb5b9b227709f91a2e3e556baa7d85acf9c55402ba5cf5",
            license = "Apache 2.0",
            licenseUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
        )
    }
}
