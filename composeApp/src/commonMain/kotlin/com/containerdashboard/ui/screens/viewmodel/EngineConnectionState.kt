package com.containerdashboard.ui.screens.viewmodel

/**
 * Three-valued engine reachability for the UI.
 *
 * A plain `Boolean` seeded with `false` cannot tell "we haven't finished the
 * first availability poll yet" apart from "we polled and the engine is down",
 * so the launch window renders as a hard failure ("Container engine is not
 * available") even when the engine is actually up — it just hasn't answered
 * the first ping. Keeping [CHECKING] explicit lets the UI show a calm
 * connecting state until the first
 * [com.containerdashboard.data.repository.DockerRepository.isDockerAvailable]
 * result actually arrives.
 */
enum class EngineConnectionState {
    /** Initial state: the first availability poll has not completed yet. */
    CHECKING,

    /** The engine answered and is reachable. */
    CONNECTED,

    /** A completed poll determined the engine is not reachable. */
    UNAVAILABLE,
}
