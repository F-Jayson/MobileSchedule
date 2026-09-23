pluginManagement {
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
    }
}

rootProject.name = "MobileSchedule"
include(":app")
