// Root project: shared configuration only. It produces no artifact itself.
plugins {
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    group = "com.roampass"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
