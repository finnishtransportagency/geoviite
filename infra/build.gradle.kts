import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.6"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    id("com.github.jk1.dependency-license-report") version "2.0"
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.spring") version "1.7.20"
}

group = "fi.fta.geoviite"
version = "SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    maven(url = "https://repo.osgeo.org/repository/release")
    mavenCentral()
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

val geotoolsVersion = "27.2"
dependencies {
    // Version overrides for transitive deps (due to known vulnerabilities)

    // For gt-epsg-hsql
    implementation("org.hsqldb", "hsqldb").version {
        strictly("[2.7.1,)")
    }
    // For spring-boot-starter-actuator & aws-java-sdk-cloudfront
    implementation("org.yaml", "snakeyaml").version {
        strictly("[1.3.3,)")
    }

    // Actual deps
    implementation("com.amazonaws:aws-java-sdk-cloudfront:1.12.347")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.zaxxer:HikariCP")
    implementation("org.flywaydb:flyway-core")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.1")
    implementation("org.geotools:gt-main:$geotoolsVersion") {
        // Excluded as the license (JDL or JRL) compatibility is unconfirmed. We don't need this.
        exclude("javax.media", "jai_core")
        // jgridshift doesn't provide licensing information. We don't need it.
        exclude("it.geosolutions.jgridshift", "jgridshift-core")
    }
    implementation("org.geotools:gt-epsg-hsql:$geotoolsVersion") {
        // Excluded as the license (JDL or JRL) compatibility is unconfirmed. We don't need this.
        exclude("javax.media","jai_core")
        // jgridshift doesn't provide licensing information. We don't need it.
        exclude("it.geosolutions.jgridshift", "jgridshift-core")
    }
    implementation("org.apache.commons:commons-csv:1.9.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.auth0:jwks-rsa:0.21.2")
    implementation("com.auth0:java-jwt:4.2.1")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.85.Final:osx-aarch_64")
    implementation("org.postgresql:postgresql:42.5.0")
    implementation("net.postgis:postgis-jdbc:2021.1.0")
    implementation("jakarta.activation:jakarta.activation-api:2.1.0")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
    compileOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.0.1")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.7.20")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.6.0")
    testImplementation("io.github.bonigarcia:webdrivermanager:5.3.1")
}

licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("report.html","Backend"))
    filters = arrayOf<DependencyFilter>(
        LicenseBundleNormalizer(),
        // ExcludeTransitiveDependenciesFilter(),
    )
    allowedLicensesFile = File("$projectDir/allowed-licenses.json")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}
tasks.withType<Jar> {
    from("${rootProject.projectDir}/..") {
        include("LICENSE.txt")
        into("META-INF")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("browser.name", System.getProperty("browser.name"))
    systemProperty("browser.headless", System.getProperty("browser.headless"))
    testLogging.exceptionFormat = FULL
    //testLogging.events = mutableSetOf(FAILED, PASSED, SKIPPED)
    testLogging.events = mutableSetOf(FAILED, PASSED, SKIPPED, STANDARD_OUT, STANDARD_ERROR)
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
