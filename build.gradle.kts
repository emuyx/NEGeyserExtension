plugins {
    id("java")
}

group = "io.github.negeyser"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

repositories {
    // Public repositories first so a clean checkout resolves the same artifacts everywhere.
    // Geyser and the Cloudburst fastutil modules are published on OpenCollab.
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    mavenCentral()
    // Fallback for local Geyser builds; not required for a normal checkout.
    mavenLocal()
}

dependencies {
    // Geyser and everything it already provides at runtime. Never bundled.
    compileOnly("org.geysermc.geyser:core:2.12.0-SNAPSHOT")

    // Geyser declares its fastutil modules at runtime scope, so they are not on the
    // compile-only classpath automatically.
    compileOnly("org.cloudburstmc.fastutil.maps:int-object-maps:8.5.15-SNAPSHOT")
    compileOnly("org.cloudburstmc.fastutil.maps:object-object-maps:8.5.15-SNAPSHOT")

    // Optional NetEase name transliteration (Chinese -> pinyin). Bundled into the extension
    // jar so no extra file has to be installed.
    implementation("com.belerweb:pinyin4j:2.5.1")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveFileName = "NEGeyserExtension.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Implementation-Title" to "NEGeyserExtension",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "NEGeyserExtension contributors",
            "Implementation-License" to "MIT"
        )
    }

    // Bundle only the optional transliteration library.
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.startsWith("pinyin4j") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }

    // Keep license information inside the distributed jar.
    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
}

tasks.build {
    dependsOn(tasks.jar)
}
