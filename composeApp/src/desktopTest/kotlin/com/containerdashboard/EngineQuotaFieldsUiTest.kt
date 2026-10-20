package com.containerdashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.containerdashboard.data.engine.ColimaConfig
import com.containerdashboard.data.engine.EngineActionStatus
import com.containerdashboard.data.engine.EngineType
import com.containerdashboard.ui.screens.EngineManagementSection
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The Colima quota fields are seeded once: when the config probe lands it must not overwrite a
 * value the user already typed (review-2026-10 G13).
 */
@OptIn(ExperimentalTestApi::class)
class EngineQuotaFieldsUiTest {
    @BeforeTest
    fun setUp() {
        // Tests must never read or write the developer's real settings; the desktopTest task
        // points this property at build/.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    @Test
    fun `a probe landing late fills only the quota fields the user has not edited`() =
        runComposeUiTest {
            var config by mutableStateOf<ColimaConfig?>(null)

            setContent {
                EngineManagementSection(
                    engineType = EngineType.COLIMA,
                    colimaProfile = "default",
                    colimaConfig = config,
                    actionStatus = EngineActionStatus.Idle,
                    commandOutput = "",
                    onStart = { _, _, _ -> },
                    onRestart = { _, _, _ -> },
                    onStop = {},
                    onClearState = {},
                )
            }

            // The quota fields have no label, placeholder or tag ("CPU (cores)" etc. are sibling
            // texts), so they are addressed by composition order: CPU, Memory, Disk.
            onAllNodes(hasSetTextAction()).assertCountEquals(3)
            onAllNodes(hasSetTextAction())[0].performTextReplacement("6")

            config = ColimaConfig(cpu = 2, memoryGB = 4, diskGB = 100)
            waitForIdle()

            onAllNodes(hasSetTextAction())[0].assert(hasText("6"))
            onAllNodes(hasSetTextAction())[1].assert(hasText("4"))
            onAllNodes(hasSetTextAction())[2].assert(hasText("100"))
        }
}
