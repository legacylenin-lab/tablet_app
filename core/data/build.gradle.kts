import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Módulo core:data: base de datos (SQLDelight) e implementación de los repositorios.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.sqldelight)
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
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
