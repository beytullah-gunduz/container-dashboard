package com.containerdashboard

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.containerdashboard.data.models.Container
import com.containerdashboard.data.models.ContainerFileEntry
import com.containerdashboard.data.models.FileType
import com.containerdashboard.ui.components.FilesTabContent
import com.containerdashboard.ui.screens.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Renders the Files tab against a real [AppViewModel] and drives it by clicking rows.
 *
 * On a merged-/usr image (Debian 12+, Ubuntu 22.04+) `/bin -> usr/bin`, so the symlink's
 * children are the same files as the real `/usr/bin`'s. With both expanded, two rows used to
 * share one LazyColumn key, which throws during composition and takes the window down
 * (review-2026-10 G1).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class FilesTabContentUiTest {
    @BeforeTest
    fun setUp() {
        // The fake repository answers at once, so every load completes inside the click.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dir(path: String) =
        ContainerFileEntry(
            name = path.substringAfterLast('/'),
            path = path,
            type = FileType.DIRECTORY,
            sizeBytes = 0,
            permissions = "drwxr-xr-x",
        )

    private fun file(path: String) =
        ContainerFileEntry(
            name = path.substringAfterLast('/'),
            path = path,
            type = FileType.FILE,
            sizeBytes = 10,
            permissions = "-rwxr-xr-x",
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

    @Test
    fun `a symlinked directory and its target can both be expanded`() =
        runComposeUiTest {
            val fake =
                FakeDockerRepository(
                    directoryListings =
                        mapOf(
                            "/" to Result.success(listOf(dir("/usr"), symlink("/bin", target = "usr/bin"))),
                            "/usr" to Result.success(listOf(dir("/usr/bin"))),
                            "/usr/bin" to Result.success(listOf(file("/usr/bin/ls"), file("/usr/bin/sh"))),
                        ),
                )
            val vm = AppViewModel(repoProvider = { fake }, repoFlow = MutableStateFlow(fake))
            vm.openFilesTab(Container(id = "c1", names = listOf("/c1"), image = "debian:12", state = "running"))

            setContent {
                val state by vm.filesPaneState.collectAsState()
                FilesTabContent(
                    state = state,
                    onToggleNode = { vm.toggleNode(it) },
                    onFileClick = {},
                    onRefresh = {},
                    onCloseViewer = {},
                    onDownload = {},
                )
            }

            // Rows, top to bottom: usr, bin (the real /usr/bin), bin → usr/bin (the symlink).
            onNodeWithText("usr").performClick()
            waitForIdle()
            onAllNodesWithText("bin")[0].performClick()
            waitForIdle()
            onAllNodesWithText("ls").assertCountEquals(1)

            onNodeWithText("→ usr/bin").performClick()
            waitForIdle()

            onAllNodesWithText("ls").assertCountEquals(2)
            onAllNodesWithText("sh").assertCountEquals(2)
        }
}
