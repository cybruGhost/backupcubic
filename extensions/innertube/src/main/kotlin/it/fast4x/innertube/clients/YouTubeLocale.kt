package it.fast4x.innertube.clients

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeLocale(
    val gl: String, // geolocation / country
    val hl: String, // host language
)