import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.mavenPublish)
    signing
}

android {
    namespace = "com.lynxal.lantern.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    // Note: NOT declaring `publishing { singleVariant("release") { ... } }` here.
    // The com.vanniktech.maven.publish plugin already configures the release
    // singleVariant with sources & javadoc jars internally — declaring it again
    // fails with "Using singleVariant publishing DSL multiple times to publish
    // variant release to component release is not allowed".
}

dependencies {
    implementation(libs.coroutines.android)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("com.lynxal.lantern", "lantern-android", "0.0.1")

    pom {
        name.set("Lantern Android")
        description.set(
            "A zero-dependency Android library for reliable mDNS/DNS-SD service discovery on " +
                "local networks. A drop-in alternative to Android's NsdManager that avoids " +
                "cross-device record mixing by parsing raw multicast UDP datagrams.",
        )
        url.set("https://github.com/lynxal/lantern_android")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/lynxal/lantern_android/blob/main/LICENSE")
            }
        }
        issueManagement {
            system.set("GitHub Issues")
            url.set("https://github.com/lynxal/lantern_android/issues")
        }
        developers {
            developer {
                id.set("VardanK")
                name.set("Vardan Kurkchiyan")
                email.set("central.repo@Lynxal.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/lynxal/lantern_android.git")
            developerConnection.set("scm:git:ssh://github.com/lynxal/lantern_android.git")
            url.set("https://github.com/lynxal/lantern_android")
        }
    }
}
