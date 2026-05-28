plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "io.zlero"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.github.zlero7:CRFramework:v1.0.6")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // HikariCP — 컴파일 전용, 런타임은 CRFramework JAR에서 제공
    compileOnly("com.zaxxer:HikariCP:5.1.0")

    implementation("net.wesjd:anvilgui:1.9.2-SNAPSHOT")

    // JDBC 드라이버 — shadowJar 에 번들
    // SQLite: CRFramework 가 sqlite-jdbc 를 번들하므로 별도 포함 불필요
    implementation("com.h2database:h2:2.3.232")
    implementation("com.mysql:mysql-connector-j:8.3.0")
}

tasks {
    runServer {
        minecraftVersion("1.20.4")
    }

    shadowJar {
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
            exclude(dependency("org.jetbrains:annotations"))
        }
        // JDBC SPI 등록 파일(META-INF/services) 병합
        mergeServiceFiles()
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
