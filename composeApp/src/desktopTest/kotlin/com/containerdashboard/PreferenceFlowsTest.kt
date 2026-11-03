package com.containerdashboard

import com.containerdashboard.data.repository.PreferenceRepository
import com.containerdashboard.util.DATA_DIR_PROPERTY
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * `collectAsState()` restarts its collection whenever it is handed a new flow instance, so each
 * preference flow must be one instance, not one per call (review-2026-10 G11).
 */
class PreferenceFlowsTest {
    @BeforeTest
    fun setUp() {
        // Tests must never read or write the developer's real settings; the desktopTest task
        // points this property at build/.
        assertNotNull(System.getProperty(DATA_DIR_PROPERTY), "$DATA_DIR_PROPERTY must be set for tests")
    }

    @Test
    fun `every preference flow is built once`() {
        assertSame(PreferenceRepository.engineHost(), PreferenceRepository.engineHost(), "engineHost")
        assertSame(PreferenceRepository.themeMode(), PreferenceRepository.themeMode(), "themeMode")
        assertSame(PreferenceRepository.themeStyle(), PreferenceRepository.themeStyle(), "themeStyle")
        assertSame(
            PreferenceRepository.monitoringAggregation(),
            PreferenceRepository.monitoringAggregation(),
            "monitoringAggregation",
        )
        assertSame(
            PreferenceRepository.showSystemContainers(),
            PreferenceRepository.showSystemContainers(),
            "showSystemContainers",
        )
        assertSame(
            PreferenceRepository.confirmBeforeDelete(),
            PreferenceRepository.confirmBeforeDelete(),
            "confirmBeforeDelete",
        )
        assertSame(PreferenceRepository.logsPaneLayout(), PreferenceRepository.logsPaneLayout(), "logsPaneLayout")
        assertSame(
            PreferenceRepository.trayRefreshRateSeconds(),
            PreferenceRepository.trayRefreshRateSeconds(),
            "trayRefreshRateSeconds",
        )
        assertSame(PreferenceRepository.logsWordWrap(), PreferenceRepository.logsWordWrap(), "logsWordWrap")
        assertSame(PreferenceRepository.logsMaxLines(), PreferenceRepository.logsMaxLines(), "logsMaxLines")
        assertSame(
            PreferenceRepository.containerColumnWidths(),
            PreferenceRepository.containerColumnWidths(),
            "containerColumnWidths",
        )
        assertSame(
            PreferenceRepository.volumeColumnWeights(),
            PreferenceRepository.volumeColumnWeights(),
            "volumeColumnWeights",
        )
        assertSame(
            PreferenceRepository.logsPaneRightWidth(),
            PreferenceRepository.logsPaneRightWidth(),
            "logsPaneRightWidth",
        )
        assertSame(
            PreferenceRepository.logsPaneBottomHeight(),
            PreferenceRepository.logsPaneBottomHeight(),
            "logsPaneBottomHeight",
        )
    }
}
