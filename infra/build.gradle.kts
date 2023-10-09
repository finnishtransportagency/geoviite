import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.11"
    id("io.spring.dependency-management") version "1.0.15.RELEASE"
    id("com.github.jk1.dependency-license-report") version "2.0"
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.spring") version "1.7.22"

    // openapi spec->api
    id("org.openapi.generator") version "7.0.1"
//    id("org.openapi.generator") version "6.6.0"
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
ext["selenium.version"] = "4.11.0"
dependencies {
    // Version overrides for transitive deps (due to known vulnerabilities)

    // For gt-epsg-hsql
    implementation("org.hsqldb", "hsqldb").version {
        strictly("[2.7.1,2.8.0)")
    }
    // For spring-boot-starter-actuator & aws-java-sdk-cloudfront
    implementation("org.yaml", "snakeyaml").version {
        strictly("[1.33,2.0)")
    }

    // Actual deps
    implementation("com.amazonaws:aws-java-sdk-cloudfront:1.12.459")
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
    implementation("org.flywaydb:flyway-core:9.18.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.6")
    implementation("org.geotools:gt-main:$geotoolsVersion") {
        // Excluded as the license (JDL or JRL) compatibility is unconfirmed. We don't need this.
        exclude("javax.media", "jai_core")
        // jgridshift doesn't provide licensing information. We don't need it.
        exclude("it.geosolutions.jgridshift", "jgridshift-core")
    }
    implementation("org.geotools:gt-epsg-hsql:$geotoolsVersion") {
        // Excluded as the license (JDL or JRL) compatibility is unconfirmed. We don't need this.
        exclude("javax.media", "jai_core")
        // jgridshift doesn't provide licensing information. We don't need it.
        exclude("it.geosolutions.jgridshift", "jgridshift-core")
    }
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("com.auth0:jwks-rsa:0.22.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.92.Final:osx-aarch_64")
    implementation("org.postgresql:postgresql:42.5.4")
    implementation("jakarta.activation:jakarta.activation-api:2.1.2")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")
    implementation("com.github.davidmoten:rtree2:0.9.3")
    implementation("commons-validator:commons-validator:1.7")
    compileOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.sun.xml.bind:jaxb-impl:4.0.2")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.21")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.11.0")
    testImplementation("org.mock-server:mockserver-netty-no-dependencies:5.14.0")
    testImplementation("org.apache.httpcomponents.client5:httpclient5:5.2.1")

    // TODO
    implementation("org.springdoc:springdoc-openapi-data-rest:1.7.0")
    implementation("org.springdoc:springdoc-openapi-ui:1.7.0")
    implementation("org.springdoc:springdoc-openapi-kotlin:1.7.0")
//    implementation("org.openapitools:openapi-generator:")
    implementation("javax.xml.bind:jaxb-api:2.3.1") // For swagger
//    implementation("org.openapitools:openapi-generator-gradle-plugin:7.0.1")
//    implementation("org.openapitools:openapi-generator-gradle-plugin:6.6.0")
}
//apply(plugin = "org.openapi.generator")


licenseReport {
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("report.html", "Backend"))
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
    maxHeapSize = "1024m"
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val oasPackage = "fi.fta.geoviite.infra.tievelho.generated"
val oasSpecLocation = "src/main/resources/tievelho/oas/liikennemerkki_template.json"
val oasGenOutputDir = "generated"

openApiGenerate {
//    verbose = true
    validateSpec = true
    generatorName = "kotlin-spring"
    inputSpec = project.file(oasSpecLocation).path
    outputDir = project.file(oasGenOutputDir).path
//    outputDir = oasGenOutputDir
    modelPackage = "$oasPackage.model"
    validateSpec = true
    modelNamePrefix = "TV"
    cleanupOutput = true
    modelFilesConstrainedTo
    skipOverwrite = false
    typeMappings = mapOf(
        "java.time.OffsetDateTime" to "java.time.Instant"
    )
    configOptions = mapOf(
//        "dateLibrary" to "java8",
    )
    globalProperties = mapOf(
        "models" to "", // Only generate models
//        "models" to "kohdeluokka_varusteet_liikennemerkit.luonti"
    )
}
//sourceSets {
//    main {
//        java.srcDirs += myDir
//        kotlin.srcDirs += myDir
//    }
//}
val defaultSrc = "src/main/kotlin"
sourceSets {
    main {
//        kotlin.srcDirs += file("src/generated/src/main/kotlin")
        kotlin {
            srcDir("$oasGenOutputDir/$defaultSrc")
            srcDir(defaultSrc)
        }
    }
}

//sourceSets {
//    main {
//        kotlin {
//            srcDir "${buildDir.absolutePath}/generated/source/kapt/main"
//        }
//    }
//}

// The values oasPackage, oasSpecLocation, oasGenOutputDir are defined earlier //tasks.register("generateOpenApiDataClasses", GenerateTask::class) {
////    generatorName.set("kotlin-spring")
//    generatorName.set("kotlin")
////    input = project.file(oasSpecLocation).path
////    inputSpec.set(oasSpecLocation)
//    inputSpec.set(project.file(oasSpecLocation).path)
//    outputDir.set(oasGenOutputDir)
//    modelPackage.set("$oasPackage.model")
//    apiPackage.set("$oasPackage.api")
////    packageName.set(oasPackage)
//    validateSpec.set(true)
//    modelNamePrefix.set("TV")
//
////    configOptions.set(
////        mapOf(
////            "dateLibrary" to "java8",
////            "useTags" to "true",
////        )
////    )
//}
////            "interfaceOnly" to "true",
//
//tasks.named("generateOpenApiDataClasses") {
//    doLast {
//        logging.captureStandardOutput(LogLevel.INFO)
//        logging.captureStandardError(LogLevel.ERROR)
//    }
//}
//openapiGenerate {
//    generatorName = "kotlin"
//    inputSpec = file("path/to/your/openapi-spec.json")
//    outputDir = file("src/main/kotlin/generated")
//    packageName = "com.example.generated"
//}
