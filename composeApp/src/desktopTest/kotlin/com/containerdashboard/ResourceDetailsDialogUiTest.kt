package com.containerdashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.containerdashboard.ui.components.DetailsTarget
import com.containerdashboard.ui.components.ResourceDetailsDialog
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Every time the Inspect dialog opens it loads afresh. A window-scoped view model, keyed by the
 * resource, kept the first result for the whole process (review-2026-10 G5).
 */
@OptIn(ExperimentalTestApi::class)
class ResourceDetailsDialogUiTest {
    @BeforeTest
    fun setUp() {
        // Tests must never read or write the developer's real settings; the desktopTest task
        // points this property at build/.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    @Test
    fun `reopening the dialog inspects the resource again`() =
        runComposeUiTest {
            val fake = FakeDockerRepository()
            var open by mutableStateOf(true)
            // A window-scoped owner, as the app has: the old code kept its view model here.
            val owner =
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }

            setContent {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    if (open) {
                        ResourceDetailsDialog(
                            target = DetailsTarget.ContainerTarget("c1", "one"),
                            onDismiss = { open = false },
                            repository = { fake },
                        )
                    }
                }
            }
            waitForIdle()
            assertEquals(1, fake.inspectContainerCalls, "the first open loads once")

            open = false
            waitForIdle()
            open = true
            waitForIdle()
            assertEquals(2, fake.inspectContainerCalls, "reopening loads again")
        }
}
