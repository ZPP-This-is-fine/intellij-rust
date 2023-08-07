/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.stdext.singleOrFilter
import org.rust.stdext.toPath
import kotlin.io.path.absolutePathString
import kotlin.io.path.div

/**
 * This mir representations are generated by rustc
 * using `rustc -Z dump-mir=main src/main.rs`.
 * There is a problem that you cannot disable all
 * the optimizations (SimplifyCFG e.g.), so I am
 * using slightly modified compiler
 */
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class MirBuildTest : RsTestBase() {
    fun `test const pos constant`() = doTest()
    fun `test static zero constant`() = doTest()
    fun `test static mut neg constant`() = doTest()
    fun `test const double neg constant`() = doTest()
    fun `test static mut many parenthesis constant`() = doTest()
    fun `test static mut many parenthesis and two neg constant`() = doTest()
    fun `test static mut many parenthesis and many neg constant`() = doTest()
    fun `test const way too many parenthesis and way too many neg constants`() = doTest()
    fun `test const plus`() = doTest()
    fun `test const minus`() = doTest()
    fun `test const mul`() = doTest()
    fun `test const shl`() = doTest()
    fun `test const shr`() = doTest()
    fun `test const div`() = doTest()
    fun `test const rem`() = doTest()
    fun `test const bitand`() = doTest()
    fun `test const bitxor`() = doTest()
    fun `test const bitor`() = doTest()
    fun `test const complicated arith expr`() = doTest()
    fun `test const lt`() = doTest()
    fun `test const lteq`() = doTest()
    fun `test const gt`() = doTest()
    fun `test const gteq`() = doTest()
    fun `test const eq`() = doTest()
    fun `test const noteq`() = doTest()
    fun `test const with block`() = doTest()
    fun `test const with block with complicated arith expr`() = doTest()
    fun `test const with block and simple if`() = doTest()
    fun `test const boolean and`() = doTest()
    fun `test const boolean or`() = doTest()
    fun `test const boolean long logical`() = doTest()
    fun `test unit type`() = doTest()
    fun `test tuple fields simple`() = doTest()
    fun `test tuple fields nested`() = doTest()
    fun `test tuple fields temporary value`() = doTest()
    fun `test three element tuple with tuples`() = doTest()
    fun `test struct named fields simple`() = doTest()
    fun `test struct named fields complex`() = doTest()
    fun `test struct literal simple`() = doTest()
    fun `test struct literal different fields order`() = doTest()
    fun `test struct literal field shorthand`() = doTest()
    fun `test struct literal nested 1`() = doTest()
    fun `test struct literal nested 2`() = doTest()
    fun `test enum variant literal simple`() = doTest()
    fun `test enum variant literal complex`() = doTest()
    fun `test tuple struct literals`() = doTest()
    fun `test loop break`() = doTest()
    fun `test block with let`() = doTest()
    fun `test block with 3 lets`() = doTest()
    fun `test if with else`() = doTest()
    fun `test if without else`() = doTest()
    fun `test if without else used as expr`() = doTest()
    fun `test if let`() = doTest()
    fun `test let mut and assign`() = doTest()
    fun `test let mut and add assign`() = doTest()
    fun `test let mut and add assign other variable`() = doTest()
    fun `test let mut and multiple add assign`() = doTest()
    fun `test immutable move`() = doTest()
    fun `test mutable move`() = doTest()
    fun `test immutable borrow`() = doTest()
    fun `test empty function`() = doTest()
    fun `test empty function with return ty`() = doTest()
    fun `test mutable borrow`() = doTest()
    fun `test empty let`() = doTest()
    fun `test array`() = doTest()
    fun `test repeat`() = doTest()
    fun `test non-const zero repeat`() = doTest()
    fun `test zero repeat`() = doTest()
    fun `test expr stmt`() = doTest()
    // TODO more terminator comments
    fun `test function call without arguments`() = doTest()
    fun `test function call with 1 copy argument`() = doTest()
    fun `test function call with 2 copy arguments`() = doTest()
    fun `test function call with 2 move arguments`() = doTest()
    fun `test function call with return value`() = doTest()
    fun `test nested function call`() = doTest()
    fun `test associated function call without arguments`() = doTest()
    fun `test method call with self receiver`() = doTest()
    // TODO fix wrong order of drops
    fun `test method call with ref self receiver`() = doTest()
    fun `test deref`() = doTest()
    fun `test deref and borrow`() = doTest()
    @Test(expected = Throwable::class) // TODO support overloaded deref
    fun `test overloaded deref`() = doTest()
    fun `test index`() = doTest()
    fun `test constant index`() = doTest()
    fun `test function in impl`() = doTest()
    fun `test while`() = expect<Throwable> { doTest() }
    fun `test while count`() = expect<Throwable> { doTest() }
    fun `test while let`() = expect<Throwable> { doTest() }
    fun `test fun with args`() = doTest()
    fun `test match pat binding 1`() = doTest()
    fun `test match pat binding 2`() = doTest()
    fun `test match single enum variant`() = doTest()
    fun `test match enum variant without fields`() = doTest()
    fun `test match enum variant with fields`() = doTest()
    fun `test match enum variant fields pat rest`() = doTest()
    fun `test match enum variant nested`() = doTest()
    fun `test match pat wild`() = doTest()
    fun `test match pat ident enum variant`() = doTest()
    fun `test match pat tuple`() = doTest()
    fun `test match pat struct for struct simple`() = doTest()
    fun `test match pat struct for enum simple`() = doTest()
    fun `test match pat struct complex`() = doTest()
    fun `test match pat ref`() = doTest()
    fun `test match pat ref mutable`() = doTest()
    fun `test immutable self argument`() = doTest()
    fun `test mutable self argument`() = doTest()
    fun `test immutable ref self argument`() = doTest()
    fun `test mutable ref self argument`() = doTest()
    fun `test explicit self argument`() = doTest()
    fun `test mut borrow adjustment`() = doTest()
    fun `test range`() = doTest()
    // TODO: more terminator comments
    // TODO: `RangeInclusive::<i32>::new` instead of `new`
    fun `test range inclusive`() = doTest()
    fun `test cast i32 to i64`() = doTest()
    fun `test path expr named const`() = doTest()

    private fun doTest(fileName: String = "main.rs") {
        val name = getTestName(true)
        val codeFile = TEST_DATA_PATH / BASE_PATH / "$name.$RS_EXTENSION"
        val mirFile = TEST_DATA_PATH / BASE_PATH / "$name.$MIR_EXTENSION"
        doTest(
            FileUtil.loadFile(codeFile.toFile()).trim(),
            mirFile.absolutePathString(),
            fileName,
        )
    }

    private fun doTest(
        @Language("Rust") code: String,
        expectedFilePath: String,
        fileName: String = "main.rs",
    ) {
        InlineFile(code, fileName)
        val builtMir = MirBuilder.build(myFixture.file as RsFile)
            .singleOrFilter { it.sourceElement.let { fn -> fn is RsFunction && fn.name == "main" }  }
            .single()
        val builtMirStr = MirPrettyPrinter(mir = builtMir).print()
        UsefulTestCase.assertSameLinesWithFile(expectedFilePath, builtMirStr)
    }

    @Suppress("unused")
    private fun test(
        @Language("Rust") code: String,
        mir: String,
        fileName: String = "main.rs"
    ) {
        InlineFile(code, fileName)
        val builtMir = MirBuilder.build(myFixture.file as RsFile).single()
        val buildMirStr = MirPrettyPrinter(mir = builtMir).print()
        TestCase.assertEquals(mir, buildMirStr)
    }

    companion object {
        private const val RS_EXTENSION = "rs"
        private const val MIR_EXTENSION = "mir"
        private val TEST_DATA_PATH = "src/test/resources".toPath()
        private val BASE_PATH = "org/rust/lang/core/mir/fixtures".toPath()
    }
}
