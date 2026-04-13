// Note: TYPESAFE_PROJECT_ACCESSORS is intentionally NOT enabled — the root project
// (`lantern-android`) and the only subproject (`:lantern-android`) share a name, which
// causes Gradle to generate two `getLanternAndroid()` accessors on RootProjectAccessor
// and the build fails to configure. We don't reference any project accessors anyway.

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "lantern-android"
include(":lantern-android")
include(":sampleApp")
