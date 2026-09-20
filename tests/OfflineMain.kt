import ing.fuyaoskyrocket.photoinfo.CoreChecks

fun main() {
    val count = CoreChecks.run(::println)
    println("SUCCESS: $count core checks; Android framework and device behavior are NOT covered.")
}
