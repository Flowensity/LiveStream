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

rootProject.name = "LiveStream"

include(":app")
include(":core:common")
include(":core:model")
include(":core:ui")
include(":core:database")
include(":core:datastore")
include(":domain:channel")
include(":data:channel")
include(":data:epg")
include(":playback:playback-api")
include(":playback:playback-media3")
