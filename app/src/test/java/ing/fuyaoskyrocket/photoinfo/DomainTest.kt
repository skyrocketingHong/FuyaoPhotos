package ing.fuyaoskyrocket.photoinfo

import org.junit.Test

class DomainTest {
    @Test fun coreChecks() {
        val count = CoreChecks.run(::println)
        println("$count core checks passed")
    }
}
