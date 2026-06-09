package com.containerdashboard

import com.containerdashboard.data.models.EnvVar
import com.containerdashboard.data.repository.toContainer
import com.containerdashboard.data.repository.toContainerInspect
import com.containerdashboard.data.repository.toDockerImage
import com.containerdashboard.data.repository.toDockerNetwork
import com.containerdashboard.data.repository.toImageInspect
import com.containerdashboard.data.repository.toNetworkInspect
import com.containerdashboard.data.repository.toVolumeInspect
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.command.InspectImageResponse
import com.github.dockerjava.api.command.InspectVolumeResponse
import com.github.dockerjava.core.DockerClientConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.github.dockerjava.api.model.Container as DockerContainer
import com.github.dockerjava.api.model.Image as DockerJavaImage
import com.github.dockerjava.api.model.Network as DockerNetworkModel

/**
 * Covers the docker-java → app-model mapping functions extracted from DesktopDockerRepository.
 * Response objects are built through docker-java's own deserializer (same convention as
 * [DockerJavaRawValuesTest]) so the JSON shapes match what real daemons send, including the
 * all-fields-absent case every mapper must survive.
 */
class DockerJavaMappersTest {
    private val mapper = DockerClientConfig.getDefaultObjectMapper()

    // --- InspectContainerResponse.toContainerInspect ---

    @Test
    fun `container inspect maps populated response`() {
        val json =
            """
            {"Id":"abc123","Name":"/web","Image":"sha256:imgid","Created":"2026-01-01T00:00:00Z",
             "Path":"nginx","Args":["-g","daemon off;"],"Platform":"linux",
             "State":{"Status":"running","Running":true,"StartedAt":"2026-01-02T00:00:00Z"},
             "Config":{"Image":"nginx:latest","Env":["FOO=bar","NOVALUE","EMPTY="],
                       "Cmd":["nginx","-g","daemon off;"],"Entrypoint":["/entry.sh"],
                       "WorkingDir":"/srv","User":"www","Hostname":"web1","Labels":{"a":"1"}},
             "HostConfig":{"RestartPolicy":{"Name":"on-failure","MaximumRetryCount":3},
                           "PortBindings":{"443/tcp":null}},
             "NetworkSettings":{"Ports":{"80/tcp":[{"HostIp":"0.0.0.0","HostPort":"8080"}]},
                                "Networks":{"bridge":{"IPAddress":"172.17.0.2","Gateway":"172.17.0.1",
                                                      "MacAddress":"02:42:ac:11:00:02","Aliases":["web"]}}},
             "Mounts":[{"Name":"vol1","Source":"/var/lib/docker/volumes/vol1/_data","Destination":"/data",
                        "Mode":"rw","RW":true},
                       {"Source":"/host","Destination":"/mnt","Mode":"ro","RW":false}]}
            """.trimIndent()
        val inspect = mapper.readValue(json, InspectContainerResponse::class.java).toContainerInspect()

        assertEquals("abc123", inspect.id)
        assertEquals("web", inspect.name) // leading "/" stripped
        assertEquals("nginx:latest", inspect.image)
        assertEquals("running", inspect.state)
        assertEquals("running", inspect.status)
        assertEquals("2026-01-02T00:00:00Z", inspect.startedAt)
        assertEquals("nginx -g daemon off;", inspect.command)
        assertEquals(listOf("/entry.sh"), inspect.entrypoint)
        assertEquals("/srv", inspect.workingDir)
        assertEquals("www", inspect.user)
        assertEquals("on-failure:3", inspect.restartPolicy)
        assertEquals("web1", inspect.hostname)
        assertEquals("linux", inspect.platform)
        // Env entries without "=" or with an empty value both map to empty-string values.
        assertEquals(
            listOf(EnvVar("FOO", "bar"), EnvVar("NOVALUE", ""), EnvVar("EMPTY", "")),
            inspect.environment,
        )
        // Named mount → volume, anonymous → bind.
        assertEquals(listOf("volume", "bind"), inspect.mounts.map { it.type })
        assertEquals("/data", inspect.mounts[0].destination)
        // 80/tcp bound to a host port; 443/tcp exposed with a null binding list → hostPort null.
        val bound = inspect.ports.first { it.containerPort == 80 }
        assertEquals(8080, bound.hostPort)
        assertEquals("0.0.0.0", bound.hostIp)
        assertEquals("tcp", bound.protocol)
        val unbound = inspect.ports.first { it.containerPort == 443 }
        assertNull(unbound.hostPort)
        assertNull(unbound.hostIp)
        assertEquals(1, inspect.networks.size)
        assertEquals("bridge", inspect.networks[0].name)
        assertEquals("172.17.0.2", inspect.networks[0].ipAddress)
        assertEquals(listOf("web"), inspect.networks[0].aliases)
        assertEquals(mapOf("a" to "1"), inspect.labels)
        assertTrue(inspect.rawJson.contains("abc123"))
    }

    @Test
    fun `container inspect with empty cmd falls back to path plus args`() {
        val json = """{"Path":"java","Args":["-jar","app.jar"]}"""
        val inspect = mapper.readValue(json, InspectContainerResponse::class.java).toContainerInspect()
        assertEquals("java -jar app.jar", inspect.command)
    }

    @Test
    fun `container inspect state falls back to status text then unknown`() {
        val exited = mapper.readValue("""{"State":{"Status":"exited"}}""", InspectContainerResponse::class.java)
        assertEquals("exited", exited.toContainerInspect().state)

        val empty = mapper.readValue("{}", InspectContainerResponse::class.java)
        assertEquals("unknown", empty.toContainerInspect().state)
    }

    @Test
    fun `container inspect survives an all-null response`() {
        val inspect = mapper.readValue("{}", InspectContainerResponse::class.java).toContainerInspect()
        assertEquals("", inspect.id)
        assertEquals("", inspect.name)
        assertEquals("", inspect.command)
        assertEquals("no", inspect.restartPolicy)
        assertEquals(emptyList(), inspect.environment)
        assertEquals(emptyList(), inspect.mounts)
        assertEquals(emptyList(), inspect.ports)
        assertEquals(emptyList(), inspect.networks)
        assertEquals(emptyMap(), inspect.labels)
    }

    // --- InspectImageResponse.toImageInspect ---

    @Test
    fun `image inspect maps populated response and shortens sha256 id`() {
        val json =
            """
            {"Id":"sha256:deadbeefcafe0123456789","RepoTags":["nginx:latest"],"RepoDigests":["nginx@sha256:aa"],
             "Architecture":"arm64","Os":"linux","Size":1000,"VirtualSize":1200,
             "Created":"2026-01-01T00:00:00Z","DockerVersion":"24.0.7","Author":"someone",
             "Config":{"Env":["A=1"],"Entrypoint":["/e"],"Cmd":["run"],"WorkingDir":"/w","User":"u",
                       "ExposedPorts":{"8080/tcp":{}},"Labels":{"k":"v"}},
             "RootFS":{"Type":"layers","Layers":["sha256:l1","sha256:l2"]}}
            """.trimIndent()
        val inspect = mapper.readValue(json, InspectImageResponse::class.java).toImageInspect()

        assertEquals("sha256:deadbeefcafe0123456789", inspect.id)
        assertEquals("deadbeefcafe", inspect.shortId)
        assertEquals(listOf("nginx:latest"), inspect.repoTags)
        assertEquals("arm64", inspect.architecture)
        assertEquals(1000L, inspect.size)
        assertEquals(1200L, inspect.virtualSize)
        assertEquals(listOf("/e"), inspect.entrypoint)
        assertEquals(listOf("run"), inspect.command)
        assertEquals(listOf("8080/tcp"), inspect.exposedPorts)
        assertEquals(listOf(EnvVar("A", "1")), inspect.environment)
        assertEquals(listOf("sha256:l1", "sha256:l2"), inspect.layers)
    }

    @Test
    fun `image inspect survives an all-null response`() {
        val inspect = mapper.readValue("{}", InspectImageResponse::class.java).toImageInspect()
        assertEquals("", inspect.id)
        assertEquals("", inspect.shortId)
        assertEquals(emptyList(), inspect.repoTags)
        assertEquals(0L, inspect.size)
        assertEquals(emptyList(), inspect.exposedPorts)
        assertEquals(emptyList(), inspect.layers)
    }

    // --- Network.toNetworkInspect ---

    @Test
    fun `network inspect maps populated response`() {
        val json =
            """
            {"Id":"0123456789abcdef","Name":"mynet","Driver":"bridge","Scope":"local",
             "Attachable":true,"Internal":true,"EnableIPv6":false,
             "IPAM":{"Driver":"default","Config":[{"Subnet":"172.18.0.0/16","Gateway":"172.18.0.1"}]},
             "Options":{"o":"1"},"Labels":{"l":"2"},
             "Containers":{"cid1":{"Name":"web","EndpointID":"ep1","MacAddress":"02:42:ac",
                                   "IPv4Address":"172.18.0.2/16","IPv6Address":""}}}
            """.trimIndent()
        val inspect = mapper.readValue(json, DockerNetworkModel::class.java).toNetworkInspect()

        assertEquals("0123456789abcdef", inspect.id)
        assertEquals("0123456789ab", inspect.shortId)
        assertEquals("mynet", inspect.name)
        assertEquals(true, inspect.attachable)
        assertEquals(true, inspect.internal)
        assertEquals(false, inspect.ipv6Enabled)
        assertEquals("default", inspect.ipamDriver)
        assertEquals(1, inspect.ipamConfig.size)
        assertEquals("172.18.0.0/16", inspect.ipamConfig[0].subnet)
        assertEquals("172.18.0.1", inspect.ipamConfig[0].gateway)
        assertEquals("", inspect.ipamConfig[0].ipRange)
        assertEquals(1, inspect.attachedContainers.size)
        assertEquals("cid1", inspect.attachedContainers[0].id)
        assertEquals("web", inspect.attachedContainers[0].name)
        assertEquals("172.18.0.2/16", inspect.attachedContainers[0].ipv4Address)
        assertEquals(mapOf("o" to "1"), inspect.options)
        assertEquals(mapOf("l" to "2"), inspect.labels)
    }

    @Test
    fun `network inspect survives an all-null response`() {
        val inspect = mapper.readValue("{}", DockerNetworkModel::class.java).toNetworkInspect()
        assertEquals("", inspect.id)
        assertEquals("", inspect.name)
        assertEquals(false, inspect.attachable)
        assertEquals(false, inspect.internal)
        assertEquals(false, inspect.ipv6Enabled)
        assertEquals(emptyList(), inspect.ipamConfig)
        assertEquals(emptyList(), inspect.attachedContainers)
    }

    // --- InspectVolumeResponse.toVolumeInspect ---

    @Test
    fun `volume inspect reads scope and createdAt from raw values`() {
        val json =
            """
            {"Name":"data","Driver":"local","Mountpoint":"/var/lib/docker/volumes/data/_data",
             "Scope":"local","CreatedAt":"2026-01-01T00:00:00Z","Labels":{"x":"y"},"Options":{"type":"none"}}
            """.trimIndent()
        val inspect = mapper.readValue(json, InspectVolumeResponse::class.java).toVolumeInspect()

        assertEquals("data", inspect.name)
        assertEquals("local", inspect.driver)
        assertEquals("/var/lib/docker/volumes/data/_data", inspect.mountpoint)
        assertEquals("local", inspect.scope)
        assertEquals("2026-01-01T00:00:00Z", inspect.createdAt)
        assertEquals(mapOf("x" to "y"), inspect.labels)
        assertEquals(mapOf("type" to "none"), inspect.options)
    }

    @Test
    fun `volume inspect defaults scope and createdAt to empty when daemon omits them`() {
        val inspect = mapper.readValue("""{"Name":"v"}""", InspectVolumeResponse::class.java).toVolumeInspect()
        assertEquals("v", inspect.name)
        assertEquals("", inspect.scope)
        assertEquals("", inspect.createdAt)
        assertEquals(emptyMap(), inspect.options)
        assertEquals(emptyMap(), inspect.labels)
    }

    // --- list-model mappers ---

    @Test
    fun `container list item maps ports and defaults`() {
        val json =
            """
            {"Id":"c1","Names":["/web"],"Image":"nginx","ImageID":"sha256:x","Command":"nginx",
             "Created":1700000000,"State":"running","Status":"Up 2 hours",
             "Ports":[{"IP":"0.0.0.0","PrivatePort":80,"PublicPort":8080,"Type":"tcp"},
                      {"PrivatePort":443,"Type":"tcp"}],
             "Labels":{"a":"b"}}
            """.trimIndent()
        val container = mapper.readValue(json, DockerContainer::class.java).toContainer()

        assertEquals("c1", container.id)
        assertEquals(listOf("/web"), container.names)
        assertEquals(1700000000L, container.created)
        assertEquals("running", container.state)
        assertEquals(2, container.ports.size)
        assertEquals(80, container.ports[0].privatePort)
        assertEquals(8080, container.ports[0].publicPort)
        assertEquals("0.0.0.0", container.ports[0].ip)
        assertNull(container.ports[1].publicPort)
        assertNull(container.ports[1].ip)

        val empty = mapper.readValue("{}", DockerContainer::class.java).toContainer()
        assertEquals("", empty.id)
        assertEquals("unknown", empty.state)
        assertEquals(emptyList(), empty.ports)
    }

    @Test
    fun `image list item maps values and keeps nullable tag lists null`() {
        val json =
            """
            {"Id":"sha256:i","ParentId":"sha256:p","RepoTags":["a:1"],"RepoDigests":null,
             "Created":1700000000,"Size":10,"VirtualSize":12,"Labels":{"k":"v"}}
            """.trimIndent()
        val image = mapper.readValue(json, DockerJavaImage::class.java).toDockerImage()

        assertEquals("sha256:i", image.id)
        assertEquals(listOf("a:1"), image.repoTags)
        assertNull(image.repoDigests)
        assertEquals(10L, image.size)

        val empty = mapper.readValue("{}", DockerJavaImage::class.java).toDockerImage()
        assertEquals("", empty.id)
        assertNull(empty.repoTags)
        assertEquals(0L, empty.size)
    }

    @Test
    fun `network list item maps ipam and containers with defaults`() {
        val json =
            """
            {"Id":"n1","Name":"mynet","Driver":"overlay","Scope":"swarm","Internal":true,"Attachable":true,
             "IPAM":{"Driver":null,"Config":[{"Subnet":"10.0.0.0/24"}]},
             "Containers":{"cid":{"Name":"web","EndpointID":"ep","MacAddress":"02:42","IPv4Address":"10.0.0.2"}}}
            """.trimIndent()
        val network = mapper.readValue(json, DockerNetworkModel::class.java).toDockerNetwork()

        assertEquals("n1", network.id)
        assertEquals("overlay", network.driver)
        assertEquals("swarm", network.scope)
        assertEquals(true, network.internal)
        assertEquals("default", network.ipam?.driver) // null IPAM driver defaults to "default"
        assertEquals(
            "10.0.0.0/24",
            network.ipam
                ?.config
                ?.get(0)
                ?.subnet,
        )
        assertNull(
            network.ipam
                ?.config
                ?.get(0)
                ?.gateway,
        )
        val attached = network.containers?.get("cid")
        assertEquals("web", attached?.name)
        assertEquals("ep", attached?.endpointId)
        assertEquals("10.0.0.2", attached?.ipv4Address)
        assertEquals("", attached?.ipv6Address)

        val empty = mapper.readValue("{}", DockerNetworkModel::class.java).toDockerNetwork()
        assertEquals("bridge", empty.driver)
        assertEquals("local", empty.scope)
        assertNull(empty.ipam)
        assertNull(empty.containers)
    }
}
