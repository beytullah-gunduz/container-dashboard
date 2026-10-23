package com.containerdashboard

import com.containerdashboard.data.repository.DesktopDockerRepository
import com.containerdashboard.fakeengine.FakeDockerEngine
import com.containerdashboard.terminal.DockerExecTtyConnector
import com.containerdashboard.util.DATA_DIR_PROPERTY
import com.jediterm.core.util.TermSize
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Console's connector must tell the exec session how big the terminal is (review-2026-10 G4):
 * JediTerm only calls `TtyConnector.resize(TermSize)`, and the default implementation throws.
 */
class DockerExecTtyConnectorTest {
    private lateinit var engine: FakeDockerEngine
    private lateinit var repo: DesktopDockerRepository

    @BeforeTest
    fun setUp() {
        // The repository's init reads PreferenceRepository (DataStore). The desktopTest task points
        // this property at build/; refuse to run against the developer's real settings.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")

        engine = FakeDockerEngine(port = 0, log = {})
        engine.start()
        repo = DesktopDockerRepository("tcp://127.0.0.1:${engine.port}")
    }

    @AfterTest
    fun tearDown() {
        repo.close()
        engine.close()
    }

    @Test
    fun `resize sends the terminal size to the exec session`() {
        val connector = DockerExecTtyConnector(repo.client, containerId = "demo-web")
        connector.start()
        try {
            connector.resize(TermSize(120, 40))

            assertEquals(1, engine.execResizes.size, "one resize request: ${engine.execResizes}")
            assertTrue(engine.execResizes.single().endsWith(" 40 120"), "rows then columns: ${engine.execResizes}")
        } finally {
            connector.close()
        }
    }

    @Test
    fun `resize after close sends nothing`() {
        val connector = DockerExecTtyConnector(repo.client, containerId = "demo-web")
        connector.start()
        connector.close()

        connector.resize(TermSize(120, 40))

        assertTrue(engine.execResizes.isEmpty(), "no resize after close: ${engine.execResizes}")
    }
}
