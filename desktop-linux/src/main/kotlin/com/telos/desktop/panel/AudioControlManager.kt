package com.telos.desktop.panel

import com.telos.desktop.linux.Sys

data class AudioState(
    val volume: Double,
    val muted: Boolean,
    val micMuted: Boolean,
    val sinks: List<String>,
    val sources: List<String>,
    val defaultSink: String,
    val track: String,
    val playing: Boolean,
)

object AudioControlManager {
    fun snapshot(): AudioState {
        val vol = Sys.run("wpctl", "get-volume", "@DEFAULT_AUDIO_SINK@").stdout
        val volume = Regex("Volume: ([0-9.]+)").find(vol)?.groupValues?.get(1)?.toDoubleOrNull()?.times(100) ?: run {
            val p = Sys.run("pactl", "get-sink-volume", "@DEFAULT_SINK@").stdout
            Regex("(\\d+)%").find(p)?.groupValues?.get(1)?.toDoubleOrNull() ?: 50.0
        }
        val muted = vol.contains("MUTED") || Sys.run("pactl", "get-sink-mute", "@DEFAULT_SINK@").stdout.contains("yes")
        val mic = Sys.run("wpctl", "get-volume", "@DEFAULT_AUDIO_SOURCE@").stdout.contains("MUTED") ||
            Sys.run("pactl", "get-source-mute", "@DEFAULT_SOURCE@").stdout.contains("yes")
        val sinks = Sys.run("pactl", "list", "short", "sinks").stdout.lines().mapNotNull { it.split('\t').getOrNull(1) }.filter { it.isNotBlank() }
        val sources = Sys.run("pactl", "list", "short", "sources").stdout.lines().mapNotNull { it.split('\t').getOrNull(1) }.filter { it.isNotBlank() }
        val def = Sys.run("pactl", "get-default-sink").stdout.trim()
        val meta = Sys.run("playerctl", "metadata", "--format", "{{artist}} — {{title}}").stdout.trim()
        val status = Sys.run("playerctl", "status").stdout.trim()
        return AudioState(volume.coerceIn(0.0, 150.0), muted, mic, sinks, sources, def, meta, status.equals("Playing", true))
    }

    fun setVolume(percent: Double) {
        val v = (percent / 100.0).coerceIn(0.0, 1.5)
        if (!Sys.run("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", String.format("%.2f", v)).ok) {
            Sys.run("pactl", "set-sink-volume", "@DEFAULT_SINK@", "${percent.toInt()}%")
        }
    }

    fun toggleMute() {
        if (!Sys.run("wpctl", "set-mute", "@DEFAULT_AUDIO_SINK@", "toggle").ok) Sys.run("pactl", "set-sink-mute", "@DEFAULT_SINK@", "toggle")
    }

    fun toggleMic() {
        if (!Sys.run("wpctl", "set-mute", "@DEFAULT_AUDIO_SOURCE@", "toggle").ok) Sys.run("pactl", "set-source-mute", "@DEFAULT_SOURCE@", "toggle")
    }

    fun setSink(name: String) { Sys.run("pactl", "set-default-sink", name) }
    fun setSource(name: String) { Sys.run("pactl", "set-default-source", name) }
    fun playPause() { Sys.run("playerctl", "play-pause") }
    fun next() { Sys.run("playerctl", "next") }
    fun prev() { Sys.run("playerctl", "previous") }
}
