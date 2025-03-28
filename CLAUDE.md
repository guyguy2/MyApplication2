# CLAUDE.md - Android Project Reference Guide

## Build Commands

- Build: `./gradlew build`
- Run app: `./gradlew installDebug`
- Unit tests: `./gradlew test`
- Single test: `./gradlew test --tests "com.guy.myapplication.ExampleUnitTest"`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Lint checks: `./gradlew lint`
- Clean: `./gradlew clean`

## Code Style Guidelines

- **Architecture**: MVVM pattern with ViewModels, StateFlow for UI state
- **Naming**: PascalCase for classes, camelCase for functions/properties, SCREAMING_SNAKE_CASE for constants
- **Documentation**: KDoc comments for public API, internal methods marked private
- **Error Handling**: Use try/catch with detailed logging and fallback mechanisms
- **Asynchronous**: Kotlin Coroutines with viewModelScope for lifecycle-aware operations
- **State Management**: Immutable UI state via StateFlow, use .update{} with copy() for changes
- **Logging**: Use TAG constant with Log.d/e/w for debugging
- **Resource Naming**: Consistent prefixes (standard\_\*) for related resources

Follow Kotlin conventions for imports and formatting. Group related functionality in packages.
This is a Jetpack Compose app, material 3, SDK version is 31+
