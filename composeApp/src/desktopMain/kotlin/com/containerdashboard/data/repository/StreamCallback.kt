package com.containerdashboard.data.repository

import com.github.dockerjava.api.async.ResultCallback
import java.io.Closeable

/**
 * A docker-java callback whose [close] also holds when it lands before the engine has answered.
 * docker-java hands the callback its stream only in [onStart], after the response headers, and
 * onStart also resets the closed flag: a close that came first was undone and the stream ran on
 * (review-2026-10 B6). On the stream client, which has no read timeout, such a stream then stayed
 * open until the engine ended it (review-2026-10 B8).
 */
internal open class StreamCallback<T> : ResultCallback.Adapter<T>() {
    private var closeRequested = false

    override fun onStart(stream: Closeable?) {
        synchronized(this) {
            super.onStart(stream)
            if (closeRequested) super.close()
        }
    }

    override fun close() {
        synchronized(this) {
            closeRequested = true
            super.close()
        }
    }
}
