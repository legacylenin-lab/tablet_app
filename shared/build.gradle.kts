import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    android {
       namespace = "app.cuaderno.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // Tinta de baja latencia (P-0). Solo Android: en la PC no se dibuja.
            // Gesto «atrás» del sistema (PQ-23, aprobado por Leyni).
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.ink.authoringCompose)
            implementation(libs.androidx.ink.brush)
            implementation(libs.androidx.ink.geometry)
            implementation(libs.androidx.ink.rendering)
            implementation(libs.androidx.ink.strokes)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            // La interfaz conoce el dominio y el modelo, no la base de datos (docs/arquitectura.md §2).
            api(project(":core:domain"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            // Texto enriquecido (RF-04), docs/arquitectura.md §5.4 y §11.
            implementation(libs.richEditor.compose)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.testRunner)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}