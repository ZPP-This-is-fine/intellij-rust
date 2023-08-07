/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

// https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/interpret/value.rs#L114
sealed class MirScalar {
    data class Int(val scalarInt: MirScalarInt) : MirScalar()

    fun tryToInt(): MirScalarInt? = when (this) {
        is Int -> scalarInt
    }

    // TODO: there is error handling done here
    fun toBits(): Long = tryToInt()?.toBits() ?: error("Could not get bits from scalar")

    fun toBool(): Boolean {
        return when (toBits()) {
            0L -> false
            1L -> true
            else -> error("Cannot translate to bool")
        }
    }

    companion object {
        fun from(bool: Boolean) = Int(MirScalarInt(if (bool) 1 else 0, 0)) // TODO: size is not used anywhere
        fun from(value: Long, size: Byte = 0) = Int(MirScalarInt(value, size))
    }
}
