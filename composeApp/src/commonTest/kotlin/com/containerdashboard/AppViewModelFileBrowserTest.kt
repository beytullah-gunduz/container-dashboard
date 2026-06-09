package com.containerdashboard

import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerFileEntry
import com.containerdashboard.data.models.FileType
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the lazily-loaded container file tree in [AppViewModel] (S8.4):
 * root loading + dirs-first case-insensitive sort, expand/collapse with child
 * caching, per-node error propagation, refresh, and symlink target resolution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelFileBrowserTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(fake: FakeDockerRepository): AppViewModel =
        AppViewModel(
            repoProvider = { fake },
            repoFlow = MutableStateFlow(fake),
        )

    private fun makeContainer(
        id: String = "c1",
        state: String = "running",
    ) = Container(
        id = id,
        names = listOf("/$id"),
        image = "nginx:latest",
        state = state,
    )

    private fun dir(path: String) =
        ContainerFileEntry(
            name = path.substringAfterLast('/'),
            path = path,
            type = FileType.DIRECTORY,
            sizeBytes = 0,
            permissions = "drwxr-xr-x",
        )

    private fun file(
        path: String,
        sizeBytes: Long = 10,
    ) = ContainerFileEntry(
        name = path.substringAfterLast('/'),
        path = path,
        type = FileType.FILE,
        sizeBytes = sizeBytes,
        permissions = "-rw-r--r--",
    )

    private fun symlink(
        path: String,
        target: String,
    ) = ContainerFileEntry(
        name = path.substringAfterLast('/'),
        path = path,
        type = FileType.SYMLINK,
        sizeBytes = 0,
        permissions = "lrwxrwxrwx",
        symlinkTarget = target,
    )

    // -------------------------------------------------------------------------
    // openFilesTab / root loading
    // -------------------------------------------------------------------------

    @Test
    fun `openFilesTab on a running container loads root with dirs first sorted case-insensitively`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to
                                Result.success(
                                    listOf(
                                        file("/zeta.txt"),
                                        dir("/etc"),
                                        file("/Apple.txt"),
                                        dir("/Bin"),
                                    ),
                                ),
                        ),
                )
            val vm = makeVm(fake)

            vm.openFilesTab(makeContainer())
            // loadRoot flips the loading flag synchronously, before the coroutine runs.
            assertTrue(vm.filesPaneState.value.isLoading, "root load should be in flight")
            advanceUntilIdle()

            val state = vm.filesPaneState.value
            assertEquals("c1", state.containerId)
            assertTrue(state.loaded)
            assertFalse(state.isLoading)
            assertNull(state.error)
            // Directories first, then files, each case-insensitive by name.
            assertEquals(
                listOf("/Bin", "/etc", "/Apple.txt", "/zeta.txt"),
                state.nodes.map { it.entry.path },
            )
            assertTrue(state.nodes.all { it.depth == 0 })
            assertEquals(
                listOf(true, true, false, false),
                state.nodes.map { it.isExpandable },
            )
            assertEquals(listOf("/"), fake.listedDirectoryPaths)
        }

    @Test
    fun `openFilesTab on a stopped container marks the pane loaded but lists nothing`() =
        runTest {
            val fake = FakeDockerRepository()
            val vm = makeVm(fake)

            vm.openFilesTab(makeContainer(state = "exited"))
            advanceUntilIdle()

            val state = vm.filesPaneState.value
            assertTrue(state.loaded)
            assertFalse(state.isRunning)
            assertTrue(state.nodes.isEmpty())
            assertTrue(fake.listedDirectoryPaths.isEmpty(), "stopped container must not be listed")
        }

    @Test
    fun `openFilesTab is idempotent for the already-open container`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    directoryListings = mapOf("/" to Result.success(listOf(file("/a.txt")))),
                )
            val vm = makeVm(fake)
            val container = makeContainer()

            vm.openFilesTab(container)
            advanceUntilIdle()
            vm.openFilesTab(container)
            advanceUntilIdle()

            assertEquals(listOf("/"), fake.listedDirectoryPaths, "re-selecting the tab must not reload")
        }

    @Test
    fun `root listing failure sets the pane-level error`() =
        runTest {
            val fake =
                FakeDockerRepository(
                    directoryListings = mapOf("/" to Result.failure(RuntimeException("exec failed"))),
                )
            val vm = makeVm(fake)

            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            val state = vm.filesPaneState.value
            assertFalse(state.isLoading)
            assertEquals("exec failed", state.error)
            assertTrue(state.nodes.isEmpty())
        }

    // -------------------------------------------------------------------------
    // toggleNode expand/collapse
    // -------------------------------------------------------------------------

    @Test
    fun `toggleNode expands a directory at depth+1 and collapse removes its children`() =
        runTest {
            val etc = dir("/etc")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(etc, file("/readme"))),
                            "/etc" to Result.success(listOf(file("/etc/passwd"), dir("/etc/ssl"))),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(etc)
            // loadChildren flags the node as loading synchronously.
            assertTrue(
                vm.filesPaneState.value.nodes
                    .first { it.entry.path == "/etc" }
                    .isLoading,
            )
            advanceUntilIdle()

            var nodes = vm.filesPaneState.value.nodes
            assertEquals(listOf("/etc", "/etc/ssl", "/etc/passwd", "/readme"), nodes.map { it.entry.path })
            assertEquals(listOf(0, 1, 1, 0), nodes.map { it.depth })
            val etcNode = nodes.first { it.entry.path == "/etc" }
            assertTrue(etcNode.isExpanded)
            assertFalse(etcNode.isLoading)

            // Collapse: children disappear, expansion flag clears.
            vm.toggleNode(etc)
            advanceUntilIdle()
            nodes = vm.filesPaneState.value.nodes
            assertEquals(listOf("/etc", "/readme"), nodes.map { it.entry.path })
            assertFalse(nodes.first { it.entry.path == "/etc" }.isExpanded)
        }

    @Test
    fun `re-expanding a directory reuses the cached children without a second listing`() =
        runTest {
            val etc = dir("/etc")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(etc)),
                            "/etc" to Result.success(listOf(file("/etc/passwd"))),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(etc)
            advanceUntilIdle()
            vm.toggleNode(etc)
            advanceUntilIdle()
            vm.toggleNode(etc)
            advanceUntilIdle()

            assertEquals(
                listOf("/", "/etc"),
                fake.listedDirectoryPaths,
                "children must be listed exactly once across expand/collapse/expand",
            )
            assertEquals(
                listOf("/etc", "/etc/passwd"),
                vm.filesPaneState.value.nodes
                    .map { it.entry.path },
            )
        }

    @Test
    fun `toggleNode ignores plain files`() =
        runTest {
            val readme = file("/readme")
            val fake =
                FakeDockerRepository(
                    directoryListings = mapOf("/" to Result.success(listOf(readme))),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(readme)
            advanceUntilIdle()

            assertEquals(listOf("/"), fake.listedDirectoryPaths)
            assertFalse(
                vm.filesPaneState.value.nodes
                    .single()
                    .isExpanded,
            )
        }

    @Test
    fun `failing directory listing collapses the node and attaches the error inline`() =
        runTest {
            val secret = dir("/secret")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(secret)),
                            "/secret" to Result.failure(RuntimeException("permission denied")),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(secret)
            advanceUntilIdle()

            val node =
                vm.filesPaneState.value.nodes
                    .single()
            assertEquals("permission denied", node.error)
            assertFalse(node.isExpanded, "failed expand must collapse the node again")
            assertFalse(node.isLoading)
            assertNull(vm.filesPaneState.value.error, "node failure must not become a pane-level error")
        }

    // -------------------------------------------------------------------------
    // refreshFiles
    // -------------------------------------------------------------------------

    @Test
    fun `refreshFiles drops the cached tree and reloads root`() =
        runTest {
            val etc = dir("/etc")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(etc)),
                            "/etc" to Result.success(listOf(file("/etc/passwd"))),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()
            vm.toggleNode(etc)
            advanceUntilIdle()
            assertEquals(2, vm.filesPaneState.value.nodes.size)

            vm.refreshFiles()
            advanceUntilIdle()

            // Expansion state is gone and the root was listed a second time.
            val nodes = vm.filesPaneState.value.nodes
            assertEquals(listOf("/etc"), nodes.map { it.entry.path })
            assertFalse(nodes.single().isExpanded)
            assertEquals(listOf("/", "/etc", "/"), fake.listedDirectoryPaths)
        }

    // -------------------------------------------------------------------------
    // Symlink resolution (resolveListPath)
    // -------------------------------------------------------------------------

    @Test
    fun `expanding a symlink with an absolute target lists the target but stores children under the link`() =
        runTest {
            val bin = symlink("/bin", target = "/usr/bin")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(bin)),
                            "/usr/bin" to Result.success(listOf(file("/usr/bin/ls"))),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(bin)
            advanceUntilIdle()

            assertEquals(listOf("/", "/usr/bin"), fake.listedDirectoryPaths)
            val nodes = vm.filesPaneState.value.nodes
            assertEquals(listOf("/bin", "/usr/bin/ls"), nodes.map { it.entry.path })
            assertEquals(listOf(0, 1), nodes.map { it.depth })
            assertTrue(nodes.first().isExpanded)
        }

    @Test
    fun `relative symlink targets resolve against the link's parent directory`() =
        runTest {
            // /lib -> usr/lib resolves to /usr/lib; /var/run -> ../run climbs to /run.
            val lib = symlink("/lib", target = "usr/lib")
            val varDir = dir("/var")
            val varRun = symlink("/var/run", target = "../run")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(varDir, lib)),
                            "/usr/lib" to Result.success(listOf(file("/usr/lib/libc.so"))),
                            "/var" to Result.success(listOf(varRun)),
                            "/run" to Result.success(listOf(file("/run/lock"))),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(lib)
            advanceUntilIdle()
            vm.toggleNode(varDir)
            advanceUntilIdle()
            vm.toggleNode(varRun)
            advanceUntilIdle()

            assertEquals(listOf("/", "/usr/lib", "/var", "/run"), fake.listedDirectoryPaths)
            // Children render under the symlink paths themselves.
            assertEquals(
                listOf("/var", "/var/run", "/run/lock", "/lib", "/usr/lib/libc.so"),
                vm.filesPaneState.value.nodes
                    .map { it.entry.path },
            )
        }

    @Test
    fun `symlink with a blank target falls back to listing its own path`() =
        runTest {
            val link = symlink("/data", target = "")
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(link)),
                            "/data" to Result.success(listOf(file("/data/x"))),
                        ),
                )
            val vm = makeVm(fake)
            vm.openFilesTab(makeContainer())
            advanceUntilIdle()

            vm.toggleNode(link)
            advanceUntilIdle()

            assertEquals(listOf("/", "/data"), fake.listedDirectoryPaths)
        }
}
