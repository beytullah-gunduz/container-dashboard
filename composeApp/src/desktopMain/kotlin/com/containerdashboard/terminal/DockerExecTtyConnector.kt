package com.containerdashboard.terminal

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Frame
import com.jediterm.terminal.TtyConnector
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

class DockerExecTtyConnector(
    private val dockerClient: DockerClient,
    private val containerId: String,
    private val cmd: List<String> = listOf("/bin/sh"),
) : TtyConnector {
    private val logger = LoggerFactory.getLogger(DockerExecTtyConnector::class.java)

    private val stdinPipe = PipedOutputStream()
    private val stdoutPipe = PipedOutputStream()
    private val stdoutReader = PipedInputStream(stdoutPipe, 65536)

    @Volatile
    private var connected = false

    @Volatile
    private var execId: String? = null

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
                    }

                    override fun onComplete() {
                        connected = false
                    }
                }

            val stdinStream = PipedInputStream(stdinPipe, 65536)

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
            throw e
        }
    }

    override fun read(
        buf: CharArray,
        offset: Int,
        length: Int,
    ): Int {
        if (!connected) return -1
        val bytes = ByteArray(length)
        val read = stdoutReader.read(bytes, 0, length)
        if (read <= 0) return read
        val chars = String(bytes, 0, read, StandardCharsets.UTF_8)
        chars.toCharArray(buf, offset, 0, chars.length)
        return chars.length
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
        while (connected) {
            Thread.sleep(100)
        }
        return 0
    }

    override fun ready(): Boolean =
        try {
            connected && stdoutReader.available() > 0
        } catch (_: IOException) {
            false
        }

    override fun getName(): String = "docker-exec-$containerId"

    override fun close() {
        connected = false
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
