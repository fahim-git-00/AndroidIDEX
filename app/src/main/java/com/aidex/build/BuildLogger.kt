package com.aidex.build

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe build log. Every compiler / packager writes here,
 * and the UI subscribes to [stream] to display live output.
 */
object BuildLogger {

    private val _stream = MutableSharedFlow<String>(extraBufferCapacity = 512)
    val stream: SharedFlow<String> = _stream.asSharedFlow()

    private val buffer = StringBuilder()
    private val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(line: String) {
        val stamped = "[${timestamp.format(Date())}] $line"
        buffer.appendLine(stamped)
        _stream.tryEmit(stamped)
    }

    @Synchronized
    fun logRaw(line: String) {
        buffer.appendLine(line)
        _stream.tryEmit(line)
    }

    @Synchronized
    fun section(title: String) {
        log("")
        log("──────── $title ────────")
    }

    @Synchronized
    fun clear() {
        buffer.setLength(0)
    }

    @Synchronized
    fun dump(): String = buffer.toString()
}
