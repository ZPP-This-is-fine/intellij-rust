package org.rust.lang.core.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.project.workspace.cargoProject

class RustStdlibResolveTestCase : RustMultiFileResolveTestCaseBase() {

    override val dataPath = "org/rust/lang/core/resolve/fixtures/stdlib"

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun testHasStdlibSources() {
        val cargoProject = myModule.cargoProject
        assertThat(cargoProject?.findExternCrateRootByName("std"))
            .overridingErrorMessage("No Rust SDK sources found during test.\n" +
                "Try running `rustup component add rust-src`")
            .isNotNull()
    }

    fun testResolveFs() = doTestResolved("fs/main.rs")
    fun testResolveCollections() = doTestResolved("collections/main.rs")
    fun testResolveCore() = doTestResolved("core/main.rs")
    fun testResolvePrelude() = doTestResolved("prelude/main.rs")
    fun testResolveBox() = doTestResolved("box/main.rs")
    fun testResolveOption() = doTestResolved("option/main.rs")

    fun testPreludeVisibility1() = doTestUnresolved("prelude_visibility1/main.rs")
    fun testPreludeVisibility2() = doTestUnresolved("prelude_visibility2/main.rs")
}
