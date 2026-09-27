package com.xiaohan.xhsnotegen.ui.generate

import android.content.Context
import android.net.Uri
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
        onPhase(Phase.PREPARING_PHOTOS)
        val images = withContext(Dispatchers.IO) {
            // An unreadable photo just gives the model less to look at; only
            // fail when there is nothing left to show it.
            draft.photoUris.mapNotNull { uri ->
                runCatching {
                    ImageCompressor.compress(context, Uri.parse(uri), ImageCompressor.FOR_AI).toBase64()
                }.getOrNull()
            }
        }
        if (images.isEmpty()) throw IllegalStateException("None of this note's photos could be read.")

        onPhase(Phase.WRITING)
        val voiceSamples = repo.getVoiceSamples(excludeId = draft.id)
        return GeminiClient.generateVariants(
            context = context,
            systemPrompt = FoodPrompts.SYSTEM_PROMPT,
            userPrompt = FoodPrompts.buildUserPrompt(draft.foodInfo, styles, voiceSamples, images.size),
            imagesBase64 = images,
            styles = styles,
        )
    }
}
