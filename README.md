# Container Dashboard

A container management dashboard built with **Jetpack Compose Multiplatform**. This application provides interface to manage your containers, images, volumes, and networks.

![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blue)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.10.0-green)
![Gradle](https://img.shields.io/badge/Gradle-8.14.4-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

## Features

- 📊 **Dashboard** - Overview of your container environment with statistics
- 📦 **Containers** - List, start, stop, pause, and manage containers
- 🖼️ **Images** - View, pull, and manage container images
- 💾 **Volumes** - Create and manage persistent data volumes
- 🌐 **Networks** - Configure and manage networks
- ⚙️ **Settings** - Configure application preferences and engine connection

## Screenshots

The application features :

- Sidebar navigation with branding
- Statistics cards with real-time data
- Sortable and filterable data tables
- Status badges and indicators
- Action buttons for common operations

## Project Structure

```
composeApp/
├── src/
│   ├── commonMain/kotlin/com/containerdashboard/
│   │   ├── data/
│   │   │   ├── models/          # Data models (Container, Image, Volume, Network)
│   │   │   └── repository/      # Docker API repository interface
│   │   ├── ui/
│   │   │   ├── components/      # Reusable UI components
│   │   │   ├── navigation/      # Navigation definitions
│   │   │   ├── screens/         # Screen composables
│   │   │   └── theme/           # Material3 theme configuration
│   │   └── App.kt               # Main application composable
│   └── desktopMain/kotlin/com/containerdashboard/
│       └── Main.kt              # Desktop entry point
```

## Tech Stack

- **Kotlin Multiplatform** - Share code across platforms
- **Jetpack Compose Multiplatform** - Modern declarative UI framework
- **Material3** - Latest Material Design components
- **Ktor Client** - HTTP client for API communication
- **Kotlinx Serialization** - JSON serialization for API responses
- **Kotlinx Coroutines** - Asynchronous programming

## Getting Started

### Prerequisites

- JDK 17 or higher
- Gradle 8.14 or higher (included via wrapper)
- Container engine installed and running (for full functionality)

### Build & Run

1. Clone the repository:
```bash
git clone https://github.com/yourusername/container-dashboard.git
cd container-dashboard
```

2. Run the desktop application:
```bash
./gradlew :composeApp:run
```

3. Build distribution packages:
```bash
# macOS
./gradlew :composeApp:packageDmg

# Windows
./gradlew :composeApp:packageMsi

# Linux
./gradlew :composeApp:packageDeb
```

## Configuration

The application connects to the container engine via the Unix socket. You can configure the host in Settings:

- **Unix socket** (default): `unix:///var/run/docker.sock`
- **TCP**: `tcp://localhost:2375`
- **TLS**: `tcp://localhost:2376` (with certificates)

## Development

### Adding a New Screen

1. Create a new screen composable in `ui/screens/`
2. Add the screen to `Screen` enum class in `ui/navigation/Navigation.kt`
3. Add navigation case in `App.kt`

### Implementing Container API

The `DockerRepository` interface in `data/repository/` defines all container operations. To connect to a container engine:

1. Implement the `DockerRepository` interface using your preferred client
2. Connect to the API via Unix socket or TCP
3. Replace `MockDockerRepository` with your implementation

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- Icons from [Material Icons](https://fonts.google.com/icons)
