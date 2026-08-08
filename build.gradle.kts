import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
    `maven-publish`
}

group = "xyz.acrylicstyle"
version = "1.6.3+1.15.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
    maven {
        name = "spigot-repo"
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net/")
    }
    maven {
        name = "neetgames"
        url = uri("https://nexus.neetgames.com/repository/maven-releases/")
    }
    maven {
        name = "sk89q-repo"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io/")
    }
    mavenLocal()
}

dependencies {
    compileOnly("org.spigotmc:spigot:1.15.2-R0.1-SNAPSHOT")
    compileOnly("com.gmail.nossr50.mcMMO:mcMMO:2.1.196")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.0") {
        exclude(group = "org.bstats", module = "bstats-bukkit")
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.0.0") {
        exclude(group = "org.bstats", module = "bstats-bukkit")
    }
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly(files("libs/MyPet-3.10.jar"))
}

publishing {
    repositories {
        maven {
            name = "repo"
            credentials(PasswordCredentials::class)
            url =
                uri(
                    if (project.version.toString().endsWith("SNAPSHOT")) {
                        project.findProperty("deploySnapshotURL")
                            ?: System.getProperty("deploySnapshotURL", "https://repo.azisaba.net/repository/maven-snapshots/")
                    } else {
                        project.findProperty("deployReleasesURL")
                            ?: System.getProperty("deployReleasesURL", "https://repo.azisaba.net/repository/maven-releases/")
                    },
                )
        }
    }

    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

tasks {
    javadoc {
        options.encoding = "UTF-8"
    }

    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        doNotTrackState("plugin.yml should be updated every time")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        filesMatching("**/plugin.yml") {
            expand("version" to project.version.toString())
        }
    }
}