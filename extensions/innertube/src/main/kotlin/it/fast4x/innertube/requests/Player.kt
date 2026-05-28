package it.fast4x.innertube.requests

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.PlayerResponse
import it.fast4x.innertube.models.bodies.PlayerBody
import it.fast4x.innertube.utils.runCatchingCancellable

// it/fast4x/innertube/requests/player.kt

suspend fun Innertube.player(
    videoId: String,
    poToken: String? = null,
    playlistId: String? = null,
    context: Context? = null,
) = runCatchingCancellable {
    // ✅ Use ANDROID_VR as the default context
    val effectiveContext = context ?: Context.DefaultAndroidVr

    client.post(player) {
        setLogin(effectiveContext.client, setLogin = cookie != null)
        setBody(
            PlayerBody(
                context = effectiveContext,
                videoId = videoId,
                playlistId = playlistId,
                contentCheckOk = true,      // required for ANDROID_VR
                racyCheckOk = true,         // required for ANDROID_VR
                serviceIntegrityDimensions = poToken?.let(PlayerBody::ServiceIntegrityDimensions)
            )
        )
    }.body<PlayerResponse>()
}