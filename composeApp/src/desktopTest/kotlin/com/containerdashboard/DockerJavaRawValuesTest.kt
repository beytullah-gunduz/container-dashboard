package com.containerdashboard

import com.github.dockerjava.api.command.InspectVolumeResponse
import com.github.dockerjava.api.model.PruneResponse
import com.github.dockerjava.core.DockerClientConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins the docker-java behavior DesktopDockerRepository relies on for prune counts and volume
 * scope/createdAt: the default object mapper's DockerObjectDeserializer attaches the FULL raw
 * JSON of every response to `DockerObject.rawValues`, exposing fields the typed models omit in
 * the pinned docker-java version (3.3.4) — the prune deleted-item lists and the volume-inspect
 * Scope/CreatedAt fields. If a docker-java upgrade changes this, these tests fail and the
 * repository should switch to the then-typed accessors.
 */
class DockerJavaRawValuesTest {
    private val mapper = DockerClientConfig.getDefaultObjectMapper()

    @Test
    fun `prune response raw values carry the deleted-items list`() {
        val json = """{"ContainersDeleted":["aaa","bbb","ccc"],"SpaceReclaimed":2147483648}"""
        val response = mapper.readValue(json, PruneResponse::class.java)

        // spaceReclaimed stays a Long — no .toInt() truncation above 2 GiB.
        assertEquals(2_147_483_648L, response.spaceReclaimed)
        assertEquals(3, (response.rawValues["ContainersDeleted"] as List<*>).size)
    }

    @Test
    fun `prune response with nothing deleted has no list in raw values`() {
        val json = """{"ContainersDeleted":null,"SpaceReclaimed":0}"""
        val response = mapper.readValue(json, PruneResponse::class.java)

        assertNull(response.rawValues["ContainersDeleted"])
    }

    @Test
    fun `volume inspect raw values carry scope and createdAt`() {
        val json =
            """
            {"Name":"data","Driver":"local","Mountpoint":"/var/lib/docker/volumes/data/_data",
             "Scope":"local","CreatedAt":"2026-01-01T00:00:00Z","Labels":null,"Options":null}
            """.trimIndent()
        val response = mapper.readValue(json, InspectVolumeResponse::class.java)

        assertEquals("data", response.name)
        assertEquals("local", response.rawValues["Scope"])
        assertEquals("2026-01-01T00:00:00Z", response.rawValues["CreatedAt"])
    }
}
