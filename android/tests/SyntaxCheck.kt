import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

/** Kotlin syntax only; this deliberately does not assert Android symbol/API resolution. */
fun main(args: Array<String>) {
    val root = File(args.single())
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project, false)
        var count = 0
        root.walkTopDown().onEnter { it.name !in setOf(".local", "build", ".gradle", ".git") }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }.forEach { source ->
                val file = factory.createFile(source.name, source.readText())
                val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
                check(errors.isEmpty()) { "${source.relativeTo(root)}: " + errors.joinToString { it.errorDescription + " at " + it.textOffset } }
                count++
            }
        println("PASS: $count Kotlin/Kotlin-DSL files parsed with zero syntax errors; Android type checking is NOT covered.")
    } finally { Disposer.dispose(disposable) }
}
