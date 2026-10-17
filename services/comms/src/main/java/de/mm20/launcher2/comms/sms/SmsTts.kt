package de.mm20.launcher2.comms.sms

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object SmsTts {
    private var engine: TextToSpeech? = null
    private val queue = ArrayDeque<String>()

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        synchronized(this) {
            val existing = engine
            if (existing != null) {
                existing.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sms")
                return
            }
            queue.addLast(text)
            engine = TextToSpeech(context.applicationContext) { status ->
                synchronized(this) {
                    if (status == TextToSpeech.SUCCESS) {
                        engine?.language = Locale.getDefault()
                        while (queue.isNotEmpty()) {
                            engine?.speak(queue.removeFirst(), TextToSpeech.QUEUE_ADD, null, "sms")
                        }
                    } else {
                        queue.clear()
                        engine = null
                    }
                }
            }
        }
    }
}
