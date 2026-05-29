package com.containerdashboard.terminal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Frame
import com.jediterm.terminal.TtyConnector
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

class DockerExecTtyConnector(
    private val dockerClient: DockerClient,
    private val containerId: String,
    private val cmd: List<String> = listOf("/bin/sh"),
) : TtyConnector {
    private val logger = LoggerFactory.getLogger(DockerExecTtyConnector::class.java)

    private val stdinPipe = PipedOutputStream()
    private val stdoutPipe = PipedOutputStream()
    private val stdoutPipeIn = PipedInputStream(stdoutPipe, 65536)

    // Streaming UTF-8 reader: buffers partial multibyte sequences across reads.
    private val stdoutReader = InputStreamReader(stdoutPipeIn, StandardCharsets.UTF_8)

    @Volatile
    private var connected = false

    @Volatile
    private var execId: String? = null

    // Counted down when the exec stream ends (onComplete/onError) OR when close() is called,
    // so waitFor() always unblocks even if docker-java never fires a terminal callback.
    private val doneLatch = CountDownLatch(1)

    private var callback: com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>? = null

    fun start() {
        try {
            val execCreate =
                dockerClient
                    .execCreateCmd(containerId)
                    .withCmd(*cmd.toTypedArray())
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .exec()

            execId = execCreate.id

            callback =
                object : com.github.dockerjava.api.async.ResultCallback.Adapter<Frame>() {
                    override fun onNext(frame: Frame?) {
                        frame?.payload?.let { data ->
                            try {
                                stdoutPipe.write(data)
                                stdoutPipe.flush()
                            } catch (_: IOException) {
                                // Pipe closed
                            }
                        }
                    }

                    override fun onError(throwable: Throwable?) {
                        logger.warn("Docker exec stream error: {}", throwable?.message)
                        connected = false
                        doneLatch.countDown()
                    }

                    override fun onComplete() {
                        connected = false
                        doneLatch.countDown()
                    }
                }

            val stdinStream = PipedInputStream(stdinPipe, 65536)

            // NOTE: execStartCmd has no built-in read/connection timeout exposed by docker-java's
            // fluent API. Adding a socket-level timeout here would require configuring the
            // DockerClient itself (e.g. DockerClientConfig.withReadTimeout). That lives in
            // DockerRepository, which is owned by another agent — left as a cross-lane concern.
            dockerClient
                .execStartCmd(execCreate.id)
                .withDetach(false)
                .withTty(true)
                .withStdIn(stdinStream)
                .exec(callback!!)

            connected = true
            logger.info("Docker exec session started for container {}", containerId)
        } catch (e: Exception) {
            logger.error("Failed to start Docker exec session", e)
            connected = false
            doneLatch.countDown()
            throw e
        }
    }

    override fun read(
        buf: CharArray,
        offset: Int,
        length: Int,
    ): Int {
        if (!connected) return -1
        // Reads chars (not bytes) so the InputStreamReader's internal CharsetDecoder can
        // accumulate partial multibyte sequences across successive calls, preventing mojibake.
        return stdoutReader.read(buf, offset, length)
    }

    override fun write(bytes: ByteArray) {
        if (!connected) return
        stdinPipe.write(bytes)
        stdinPipe.flush()
    }

    override fun write(string: String) {
        write(string.toByteArray(StandardCharsets.UTF_8))
    }

    override fun isConnected(): Boolean = connected

    override fun waitFor(): Int {
        // Blocks until onComplete/onError fires OR close() is called — no busy-wait.
        doneLatch.await()
        return 0
    }

    override fun ready(): Boolean =
        try {
            connected && stdoutReader.ready()
        } catch (_: IOException) {
            false
        }

    override fun getName(): String = "docker-exec-$containerId"

    override fun close() {
        connected = false
        // Release any thread blocked in waitFor() even if docker-java callbacks never fire.
        doneLatch.countDown()
        try {
            callback?.close()
        } catch (_: Exception) {
        }
        try {
            stdinPipe.close()
        } catch (_: Exception) {
        }
        try {
            stdoutPipe.close()
        } catch (_: Exception) {
        }
        try {
            stdoutReader.close()
        } catch (_: Exception) {
        }
        logger.info("Docker exec session closed for container {}", containerId)
    }
}
