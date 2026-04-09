package app.it.fast4x.rimusic.recognition

import android.util.Log

class ShazamSignature {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("shazam_signature_jni")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("ShazamSignature", "Failed to load native library", e)
        } catch (e: SecurityException) {
            Log.e("ShazamSignature", "Security exception loading native library", e)
        }
    }

    external fun create(input: ShortArray): String

    fun safeCreate(input: ShortArray): String {
        if (!isLoaded) {
            throw UnsatisfiedLinkError("Native library shazam_signature_jni not loaded")
        }
        return create(input)
    }
}
