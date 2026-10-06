package com.xiaohan.xhsnotegen.ui.generate

import android.content.Context
import android.net.Uri
import com.xiaohan.xhsnotegen.ai.AiSettings
import com.xiaohan.xhsnotegen.ai.ModeStore
import com.xiaohan.xhsnotegen.ai.AiWriter
import com.xiaohan.xhsnotegen.data.repository.DraftRepository
import com.xiaohan.xhsnotegen.domain.NoteDraft
import com.xiaohan.xhsnotegen.domain.NoteStyle
import com.xiaohan.xhsnotegen.domain.NoteVariant
import com.xiaohan.xhsnotegen.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Photos + food info → note variants. Shared by first generation, regenerate and rewrite. */
object NoteGenerator {

    enum class Phase { PREPARING_PHOTOS, WRITING }

    suspend fun generate(
        context: Context,
        repo: DraftRepository,
        draft: NoteDraft,
        styles: List<NoteStyle>,
        onPhase: (Phase) -> Unit = {},
    ): List<NoteVariant> {
        val config = AiSettings.current(context)
        config.problem()?.let { throw IllegalStateException(it) }

        onPhase(Phase.PREPARING_PHOTOS)
        val images = if (!config.vision) emptyList() else withContext(Dispatchers.IO) {
            // An unreadable photo just gives the model less to look at; only
            // fail when there is nothing left to show it.
            draft.photoUris.mapNotNull { uri ->
                runCatching {
                    ImageCompressor.compress(context, Uri.parse(uri), ImageCompressor.FOR_AI).toBase64()
                }.getOrNull()
            }
        }
        if (config.vision && images.isEmpty() && draft.photoUris.isNotEmpty()) {
            throw IllegalStateException("None of this note's photos could be read.")
        }

        onPhase(Phase.WRITING)
        // The note's own language wins over its mode's default.
        val baseMode = ModeStore.get(draft.type)
        val mode = draft.language?.let { baseMode.copy(language = it) } ?: baseMode
        val voiceSamples = repo.getVoiceSamples(excludeId = draft.id, mode = draft.type, language = mode.language)
        return AiWriter.generateVariants(
            config = config,
            systemPrompt = FoodPrompts.systemPrompt(mode.instructions, mode.language),
            userPrompt = FoodPrompts.buildUserPrompt(
                draft.foodInfo, styles, voiceSamples,
                photoCount = images.size,
                photosHidden = !config.vision && draft.photoUris.isNotEmpty(),
                styleInstruction = { mode.style(it) },
                mode = mode,
                rating = draft.rating,
            ),
            imagesBase64 = images,
            styles = styles,
        )
    }
}
