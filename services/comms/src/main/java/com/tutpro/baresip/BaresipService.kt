package com.tutpro.baresip

/**
 * Bridge object that the native baresip library calls back into. The class name and the callback
 * signatures are fixed by baresip.c (taken from baresip-studio, BSD-3-Clause), so they must not
 * be renamed. The Telos SIP engine sets a [Listener] to receive the events.
 */
class BaresipService {

    interface Listener {
        fun onStarted()
        fun onStopped(error: String)
        fun onUaEvent(event: String, uap: Long, callp: Long)
        fun onMessage(uap: Long, peerUri: String, contentType: String, body: ByteArray)
        fun onMessageResponse(code: Int, reason: String, time: String)
    }

    @Volatile
    var listener: Listener? = null

    /** Blocks until baresip stops, so it must run on its own thread. */
    external fun baresipStart(path: String, addresses: String, logLevel: Int, software: String)

    external fun baresipStop(force: Boolean)

    @Suppress("unused")
    fun uaEvent(event: String, uap: Long, callp: Long) {
        listener?.onUaEvent(event, uap, callp)
    }

    @Suppress("unused")
    fun messageEvent(uap: Long, peerUri: String, cType: String, msg: ByteArray) {
        listener?.onMessage(uap, peerUri, cType, msg)
    }

    @Suppress("unused")
    fun messageResponse(responseCode: Int, responseReason: String, time: String) {
        listener?.onMessageResponse(responseCode, responseReason, time)
    }

    @Suppress("unused")
    fun started() {
        listener?.onStarted()
    }

    @Suppress("unused")
    fun stopped(error: String) {
        listener?.onStopped(error)
    }

    companion object {
        /** False when the app was built without the native SIP libraries. */
        val available: Boolean by lazy {
            runCatching { System.loadLibrary("baresip") }.isSuccess
        }
    }
}
