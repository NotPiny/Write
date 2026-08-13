plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven {
        name = "faststatsReleases"
        url = uri("https://repo.faststats.dev/releases")
    }
}

dependencies {
    paperweight.paperDevBundle("26.2.build.+")
    implementation("dev.faststats.metrics:bukkit:0.29.4")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

val serverLauncher = javaToolchains.launcherFor {
    vendor = JvmVendorSpec.JETBRAINS
    languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.2")
        javaLauncher = serverLauncher
        jvmArgs("-Xms2G", "-Xmx2G", "-XX:+AllowEnhancedClassRedefinition", "-Dcom.mojang.eula.agree=true")

        downloadPlugins {
            modrinth("evkiwA7V", "Ow8CJ6pP") // Axiom
            modrinth("Vebnzrzj", "b0mk8uS6") // LuckPerms
        }
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
            exclude("META-INF/MANIFEST.MF")
            exclude("META-INF/*.SF")
            exclude("META-INF/*.RSA")
        }
    }
}
