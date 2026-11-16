package app.cuaderno

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform