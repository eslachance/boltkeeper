plugins {
    id("java")
    id("com.gradleup.shadow") version "9.3.1"
    id("app.ultradev.hytalegradle") version "2.0.0"
}

group = "tokebak"
version = "1.3.2"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/HytaleServer.jar"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

hytale {
    allowOp.set(true)
    patchline.set("pre-release")
    includeLocalMods.set(true)

    manifest {
        version.set(project.version.toString())
    }
}
