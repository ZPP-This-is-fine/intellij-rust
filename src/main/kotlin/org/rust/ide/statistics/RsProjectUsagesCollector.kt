/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.crate.crateGraph

@Suppress("UnstableApiUsage")
class RsProjectUsagesCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    override fun getMetrics(project: Project): Set<MetricEvent> {
        val cargoProjects = project.cargoProjects.allProjects
        if (cargoProjects.isEmpty()) return emptySet()

        val metrics = mutableSetOf<MetricEvent>()
        metrics += CARGO_PROJECTS_EVENT.metric(cargoProjects.size)

        val crates = runReadAction {
            project.crateGraph.topSortedCrates
        }

        val directDependencyIds = mutableSetOf<String>()
        val workspaceInfo = PackagesInfo()
        val dependenciesInfo = PackagesInfo()
        for (crate in crates) {
            val target = crate.cargoTarget ?: continue
            val pkg = target.pkg
            val info = when (pkg.origin) {
                PackageOrigin.WORKSPACE -> workspaceInfo
                PackageOrigin.DEPENDENCY -> dependenciesInfo
                else -> continue
            }
            info.packageIds += pkg.id
            info.editions += target.edition
            when {
                target.kind.isCustomBuild -> info.buildScriptCount += 1
                target.kind.isProcMacro -> info.procMacroLibCount += 1
            }
            if (pkg.origin == PackageOrigin.WORKSPACE) {
                for ((_, dependencyCrate) in crate.dependencies) {
                    if (dependencyCrate.origin == PackageOrigin.DEPENDENCY) {
                        val dependencyPackage = dependencyCrate.cargoTarget?.pkg ?: continue
                        metrics += DIRECT_DEPENDENCY.metric(dependencyPackage.name, dependencyPackage.version)
                        directDependencyIds += dependencyPackage.id
                    }
                }
            }
        }

        metrics += PACKAGES.metric(
            workspaceInfo.packageIds.size,
            directDependencyIds.size,
            dependenciesInfo.packageIds.size
        )
        metrics += COMPILE_TIME_TARGETS.metric(
            BUILD_SCRIPT_WORKSPACE.with(workspaceInfo.buildScriptCount),
            BUILD_SCRIPT_DEPENDENCY.with(dependenciesInfo.buildScriptCount),
            PROC_MACRO_WORKSPACE.with(workspaceInfo.procMacroLibCount),
            PROC_MACRO_DEPENDENCY.with(dependenciesInfo.procMacroLibCount)
        )
        metrics += EDITIONS.metric(
            workspaceInfo.editions.map { it.presentation },
            dependenciesInfo.editions.map { it.presentation }
        )
        return metrics
    }

    private data class PackagesInfo(
        val packageIds: MutableSet<PackageId> = mutableSetOf(),
        val editions: MutableSet<Edition> = mutableSetOf(),
        var buildScriptCount: Int = 0,
        var procMacroLibCount: Int = 0
    )

    companion object {
        private val GROUP = EventLogGroup("rust.project", 3)

        private val CARGO_PROJECTS_EVENT = GROUP.registerEvent("cargo_projects", EventFields.RoundedInt("count"))

        private val PACKAGES = GROUP.registerEvent("packages",
            EventFields.RoundedInt("workspace"),
            EventFields.RoundedInt("direct_dependency"),
            EventFields.RoundedInt("dependency")
        )

        private val PROC_MACRO_WORKSPACE = EventFields.RoundedInt("proc_macro_workspace")
        private val PROC_MACRO_DEPENDENCY = EventFields.RoundedInt("proc_macro_dependency")
        private val BUILD_SCRIPT_WORKSPACE = EventFields.RoundedInt("build_script_workspace")
        private val BUILD_SCRIPT_DEPENDENCY = EventFields.RoundedInt("build_script_dependency")

        private val COMPILE_TIME_TARGETS = GROUP.registerVarargEvent("compile_time_targets",
            BUILD_SCRIPT_WORKSPACE,
            BUILD_SCRIPT_DEPENDENCY,
            PROC_MACRO_WORKSPACE,
            PROC_MACRO_DEPENDENCY,
        )

        private val EDITIONS = GROUP.registerEvent("editions",
            EventFields.StringList("workspace", Edition.values().map { it.presentation }),
            EventFields.StringList("dependencies", Edition.values().map { it.presentation })
        )

        private val DEPENDENCY_NAME = EventFields.String("name", POPULAR_CRATES)

        private val DIRECT_DEPENDENCY = GROUP.registerEvent("dependency", DEPENDENCY_NAME, EventFields.Version)
    }
}

private val POPULAR_CRATES = listOf(
    "syn",
    "rand",
    "libc",
    "rand_core",
    "quote",
    "proc-macro2",
    "cfg-if",
    "serde",
    "autocfg",
    "itoa",
    "unicode-xid",
    "bitflags",
    "getrandom",
    "log",
    "rand_chacha",
    "lazy_static",
    "serde_derive",
    "time",
    "serde_json",
    "base64",
    "memchr",
    "regex",
    "num-traits",
    "parking_lot_core",
    "regex-syntax",
    "cc",
    "smallvec",
    "parking_lot",
    "version_check",
    "ryu",
    "once_cell",
    "strsim",
    "aho-corasick",
    "semver",
    "clap",
    "bytes",
    "hashbrown",
    "digest",
    "crossbeam-utils",
    "lock_api",
    "scopeguard",
    "block-buffer",
    "generic-array",
    "num_cpus",
    "byteorder",
    "textwrap",
    "atty",
    "indexmap",
    "num-integer",
    "mio",
    "percent-encoding",
    "idna",
    "either",
    "pin-project-lite",
    "url",
    "ppv-lite86",
    "tokio",
    "itertools",
    "unicode-width",
    "heck",
    "slab",
    "thiserror",
    "thiserror-impl",
    "futures",
    "ansi_term",
    "unicode-normalization",
    "chrono",
    "memoffset",
    "rustc_version",
    "miniz_oxide",
    "fnv",
    "typenum",
    "unicode-bidi",
    "anyhow",
    "pkg-config",
    "termcolor",
    "env_logger",
    "futures-core",
    "hyper",
    "socket2",
    "tokio-util",
    "toml",
    "futures-util",
    "futures-task",
    "crossbeam-epoch",
    "futures-sink",
    "futures-channel",
    "crossbeam-channel",
    "winapi",
    "thread_local",
    "http",
    "sha2",
    "futures-io",
    "arrayvec",
    "matches",
    "tracing",
    "nom",
    "pin-utils",
    "opaque-debug",
    "tracing-core",
    "httparse",
    "tinyvec",
    "h2",
    "crossbeam-deque",
    "humantime",
    "pin-project",
    "unicode-segmentation",
    "pin-project-internal",
    "crc32fast",
    "nix",
    "remove_dir_all",
    "tempfile",
    "instant",
    "futures-macro",
    "http-body",
    "backtrace",
    "uuid",
    "adler",
    "rustc-demangle",
    "proc-macro-hack",
    "futures-executor",
    "hex",
    "vec_map",
    "mime",
    "want",
    "form_urlencoded",
    "semver-parser",
    "flate2",
    "openssl-sys",
    "ahash",
    "proc-macro-error",
    "serde_urlencoded",
    "try-lock",
    "tinyvec_macros",
    "tokio-macros",
    "wasi",
    "quick-error",
    "walkdir",
    "proc-macro-error-attr",
    "object",
    "spin",
    "same-file",
    "async-trait",
    "sha-1",
    "tower-service",
    "glob",
    "num-bigint",
    "httpdate",
    "encoding_rs",
    "gimli",
    "signal-hook-registry",
    "openssl",
    "rayon",
    "subtle",
    "unicode-ident",
    "hmac",
    "rayon-core",
    "rand_hc",
    "reqwest",
    "cpufeatures",
    "openssl-probe",
    "addr2line",
    "tracing-attributes",
    "linked-hash-map",
    "foreign-types",
    "foreign-types-shared",
    "redox_syscall",
    "which",
    "regex-automata",
    "unicase",
    "paste",
    "synstructure",
    "rustls",
    "static_assertions",
    "native-tls",
    "fastrand",
    "bstr",
    "ipnet",
    "crypto-mac",
    "winapi-x86_64-pc-windows-gnu",
    "winapi-i686-pc-windows-gnu",
    "ring",
    "untrusted",
    "time-macros",
    "dirs",
    "hyper-tls",
    "fixedbitset",
    "sct",
    "webpki",
    "num-rational",
    "petgraph",
    "darling_macro",
    "darling_core",
    "darling",
    "libloading",
    "rand_pcg",
    "block-padding",
    "tracing-subscriber",
    "jobserver",
    "crossbeam-queue",
    "hermit-abi",
    "zeroize",
    "phf_shared",
    "bumpalo",
    "crypto-common",
    "os_str_bytes",
    "siphasher",
    "winapi-util",
    "tokio-rustls",
    "wasm-bindgen",
    "wasm-bindgen-backend",
    "wasm-bindgen-shared",
    "wasm-bindgen-macro",
    "wasm-bindgen-macro-support",
    "yaml-rust",
    "net2",
    "lazycell",
    "stable_deref_trait",
    "dtoa",
    "strum_macros",
    "iovec",
    "num-iter",
    "pest",
    "sharded-slab",
    "proc-macro-crate",
    "num-complex",
    "js-sys",
    "webpki-roots",
    "filetime",
    "rustc-hash",
    "rustversion",
    "mime_guess",
    "shlex",
    "tokio-stream",
    "dirs-sys",
    "miow",
    "strum",
    "phf",
    "rand_xorshift",
    "tracing-log",
    "void",
    "ucd-trie",
    "derive_more",
    "sha1",
    "structopt",
    "libz-sys",
    "ident_case",
    "byte-tools",
    "structopt-derive",
    "bincode",
    "core-foundation-sys",
    "tracing-futures",
    "web-sys",
    "proc-macro-nested",
    "ctor",
    "clap_derive",
    "prost-derive",
    "prost",
    "serde_yaml",
    "matchers",
    "half",
    "csv",
    "phf_generator",
    "num",
    "fake-simd",
    "tokio-native-tls",
    "csv-core",
    "prost-types",
    "core-foundation",
    "scoped-tls",
    "term",
    "failure",
    "vcpkg",
    "bindgen",
    "ordered-float",
    "minimal-lexical",
    "lexical-core",
    "clap_lex",
    "arrayref",
    "failure_derive",
    "windows_x86_64_msvc",
    "convert_case",
    "async-stream",
    "error-chain",
    "maplit",
    "hostname",
    "async-stream-impl",
    "arc-swap",
    "clang-sys",
    "winreg",
    "console",
    "cookie",
    "wasm-bindgen-futures",
    "const_fn",
    "constant_time_eq",
    "cexpr",
    "prost-build",
    "cipher",
    "maybe-uninit",
    "derivative",
    "multimap",
    "bit-vec",
    "hyper-rustls",
    "dirs-sys-next",
    "zstd-sys",
    "signal-hook",
    "windows-sys",
    "schannel",
    "serde_cbor",
    "tower-layer",
    "security-framework",
    "adler32",
    "xml-rs",
    "aes",
    "windows_x86_64_gnu",
    "terminal_size",
    "zstd-safe",
    "windows_i686_msvc",
    "zstd",
    "windows_i686_gnu",
    "security-framework-sys",
    "tower",
    "event-listener",
    "peeking_take_while",
    "windows_aarch64_msvc",
    "dashmap",
    "pest_derive",
    "crunchy",
    "rand_isaac",
    "rand_os",
    "dirs-next",
    "md-5",
    "bitvec",
    "match_cfg",
    "data-encoding",
    "cast",
    "standback",
    "rustls-pemfile",
    "pest_meta",
    "pest_generator",
    "time-macros-impl",
    "vsdb",
    "fxhash",
    "globset",
    "vsdb_derive",
    "vsdbsled",
    "concurrent-queue",
    "rand_jitter",
    "nodrop",
    "phf_codegen",
    "radium",
    "criterion",
    "winapi-build",
    "safemem",
    "utf-8",
    "crossbeam",
    "kernel32-sys",
    "criterion-plot",
    "redox_users",
    "num_threads",
    "pretty_assertions",
    "threadpool",
    "fallible-iterator",
    "colored",
    "tinytemplate",
    "cargo_metadata",
    "cmake",
    "diff",
    "combine",
    "libm",
    "futures-lite",
    "serde_bytes",
    "parking",
    "zeroize_derive",
    "indoc",
    "async-channel",
    "tar",
    "crc",
    "utf8-ranges",
    "waker-fn",
    "pem",
    "bit-set",
    "cache-padded",
    "oorandom",
    "curve25519-dalek",
    "tungstenite",
    "md5",
    "tracing-serde",
    "language-tags",
    "plotters",
    "mio-uds",
    "funty",
    "sha3",
    "iana-time-zone",
    "num-derive",
    "protobuf",
    "lru-cache",
    "rustls-native-certs",
    "wyz",
    "unindent",
    "aead",
    "async-task",
    "difference",
    "bytemuck",
    "Inflector",
    "tap",
    "libgit2-sys",
    "git2",
    "approx",
    "ntapi",
    "tiny-keccak",
    "tonic",
    "tokio-io",
    "memmap2",
    "xattr",
    "trust-dns-proto",
    "doc-comment",
    "ctr",
    "polling",
    "unreachable",
    "fs2",
    "async-io",
    "keccak",
    "universal-hash",
    "pbkdf2",
    "owning_ref",
    "lru",
    "cpuid-bool",
    "signature",
    "float-cmp",
    "tokio-timer",
    "fs_extra",
    "string_cache",
    "backtrace-sys",
    "tokio-executor",
    "plotters-backend",
    "trust-dns-resolver",
    "plotters-svg",
    "tonic-build",
    "enum-as-inner",
    "rustc-serialize",
    "polyval",
    "getopts",
    "serde_with",
    "resolv-conf",
    "ignore",
    "serde_with_macros",
    "precomputed-hash",
    "tempdir",
    "number_prefix",
    "aes-gcm",
    "encode_unicode",
    "tokio-reactor",
    "ghash",
    "new_debug_unreachable",
    "futures-timer",
    "wait-timeout",
    "async-executor",
    "num_enum",
    "num_enum_derive",
    "tokio-tungstenite",
    "zip",
    "async-lock",
    "headers",
    "predicates",
    "errno",
    "tokio-threadpool",
    "png",
    "dyn-clone",
    "prometheus",
    "quick-xml",
    "blocking",
    "home",
    "tokio-tcp",
    "openssl-macros",
    "target-lexicon",
    "tokio-io-timeout",
    "headers-core",
    "serde_repr",
    "atomic-waker",
    "gcc",
    "indicatif",
    "predicates-core",
    "predicates-tree",
    "async-std",
    "curl-sys",
    "pulldown-cmark",
    "ws2_32-sys",
    "paste-impl",
    "hyper-timeout",
    "tokio-current-thread",
    "tokio-sync",
    "notify",
    "inotify",
    "image",
    "io-lifetimes",
    "config",
    "pyo3",
    "cloudabi",
    "hkdf",
    "cloudabi",
)
