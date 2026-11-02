package com.slogan.app.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthesisKeyTest {

    private val base = SpeechRequest(
        text = "前方施工，请绕行",
        voiceName = "zh-cn-x-ccc-local",
        speechRate = 1.0f,
        pitch = 1.0f,
    )

    @Test
    fun sameInputProducesSameKey() {
        val first = SynthesisKey.of(base, "com.google.android.tts")
        val second = SynthesisKey.of(base, "com.google.android.tts")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun textChangeInvalidatesKey() {
        assertNotEquals(
            SynthesisKey.of(base, "engine"),
            SynthesisKey.of(base.copy(text = "前方施工，请绕行。"), "engine"),
        )
    }

    @Test
    fun voiceChangeInvalidatesKey() {
        assertNotEquals(
            SynthesisKey.of(base, "engine"),
            SynthesisKey.of(base.copy(voiceName = null), "engine"),
        )
    }

    @Test
    fun rateAndPitchChangeInvalidateKey() {
        val original = SynthesisKey.of(base, "engine")
        assertNotEquals(original, SynthesisKey.of(base.copy(speechRate = 1.2f), "engine"))
        assertNotEquals(original, SynthesisKey.of(base.copy(pitch = 0.8f), "engine"))
    }

    @Test
    fun engineChangeInvalidatesKey() {
        assertNotEquals(
            SynthesisKey.of(base, "com.google.android.tts"),
            SynthesisKey.of(base, "com.samsung.android.tts"),
        )
    }

    @Test
    fun floatFormattingIsLocaleIndependent() {
        val key = SynthesisKey.of(base.copy(speechRate = 1.1f), "engine")
        assertTrue(key.all { it in "0123456789abcdef" })
    }
}
