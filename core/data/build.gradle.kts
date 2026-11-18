import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo core:data: base de datos (SQLDelight) e implementación de los repositorios.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()
    android {
        namespace = "app.cuaderno.core.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        // PQ-10: pruebas instrumentadas (corren en el emulador o en la tablet).
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    sourceSets {
        // Código compartido entre Android y Windows: ambos corren sobre Java (ZIP, SHA-256, JDBC).
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        jvmMain.get().dependsOn(jvmCommonMain)
        androidMain.get().dependsOn(jvmCommonMain)
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            // Motor de PDF de Windows (docs/arquitectura.md §8 y §11; prototipo P-1).
            implementation(libs.pdfbox)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
            implementation(libs.androidx.testExt.junit)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("CuadernoDatabase") {
            packageName.set("app.cuaderno.data.db")
        }
    }
}
