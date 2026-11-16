import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo core:domain: interfaces de repositorio y contratos de plataforma.
// Solo conoce a core:model: no sabe nada de pantallas ni de bases de datos.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm()
    android {
        namespace = "app.cuaderno.core.domain"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
