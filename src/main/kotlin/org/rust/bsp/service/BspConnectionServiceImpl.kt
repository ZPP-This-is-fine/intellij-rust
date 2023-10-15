package org.rust.bsp.service

import ch.epfl.scala.bsp4j.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.project.stateStore
import com.intellij.util.EnvironmentUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.rust.bsp.BspClient
import org.rust.bsp.BspConstants
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.stdext.HashCode
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class BspConnectionServiceImpl(val project: Project) : BspConnectionService {

    private var bspServer: BspServer? = null
    private var bspClient: BspClient? = null
    private var disconnectActions: MutableList<() -> Unit> = mutableListOf()
    private var initializeBuildResult: InitializeBuildResult? = null

    private fun getBspServer(): BspServer {
        if (bspServer == null) {
            createBspServer()
            initializeBuildResult = queryForInitialize(bspServer!!)
                .catchSyncErrors { println("Error while initializing BSP server $it") }
                .get()
            bspServer!!.onBuildInitialized()
        }
        return bspServer!!
    }

    private fun getBspClient(): BspClient {
        if (bspServer == null) {
            createBspServer()
        }
        return bspClient!!
    }

    override fun isConnected(): Boolean = bspServer != null

    override fun connect() {
        println("Starting BSP server")
        getBspServer()
    }

    override fun getProjectData(projectDirectory: Path): CargoWorkspaceData {
        val server = getBspServer()
        return calculateProjectDetailsWithCapabilities(server, projectDirectory, project) {
            println("BSP server capabilities: $it")
        }
    }

    private fun createBspServer() {
        bspServer = getBspConnectionDetailsFile()
            ?.let { parseBspConnectionDetails(it) }
            ?.let { createBspServer(it) }!!
    }

    private fun queryForInitialize(server: BspServer): CompletableFuture<InitializeBuildResult> {
        val buildParams = createInitializeBuildParams()
        return server.buildInitialize(buildParams)
    }

    private fun createInitializeBuildParams(): InitializeBuildParams {
        val projectBaseDir = project.basePath
        val params = InitializeBuildParams(
            "IntelliJ-Rust",
            "0.4.0",
            "2.0.0",
            projectBaseDir.toString(),
            BuildClientCapabilities(listOf("java"))
        )
        val dataJson = JsonObject()
        dataJson.addProperty("clientClassesRootDir", "$projectBaseDir/out")
        params.data = dataJson

        return params
    }

    override fun disconnect() {
        val exceptions = disconnectActions.mapNotNull { executeDisconnectActionAndReturnThrowableIfFailed(it) }
        disconnectActions.clear()
        throwExceptionWithSuppressedIfOccurred(exceptions)

        bspServer = null
        bspClient = null
    }

    override fun compileAllSolutions(params: CompileParams): CompletableFuture<CompileResult> {
        val wbt = getBspServer().workspaceBuildTargets().get()
        params.targets = wbt.targets.map { it.id }
        return compileSolution(params)

    }

    override fun compileSolution(params: CompileParams): CompletableFuture<CompileResult> {
        return getBspServer().buildTargetCompile(params)
    }

    private fun executeDisconnectActionAndReturnThrowableIfFailed(disconnectAction: () -> Unit): Throwable? =
        try {
            disconnectAction()
            null
        } catch (e: Exception) {
            e
        }

    private fun throwExceptionWithSuppressedIfOccurred(exceptions: List<Throwable>) {
        val firstException = exceptions.firstOrNull()

        if (firstException != null) {
            exceptions
                .drop(1)
                .forEach { firstException.addSuppressed(it) }

            throw firstException
        }
    }

    private fun createLauncher(bspIn: InputStream, bspOut: OutputStream, client: BuildClient): Launcher<BspServer> =
        Launcher.Builder<BspServer>()
            .setRemoteInterface(BspServer::class.java)
            .setExecutorService(AppExecutorUtil.getAppExecutorService())
            .setInput(bspIn)
            .setOutput(bspOut)
            .setLocalService(client)
            .create()

    private fun createAndStartProcess(bspConnectionDetails: BspConnectionDetails): Process =
        ProcessBuilder(bspConnectionDetails.argv)
            .directory(project.stateStore.projectBasePath.toFile())
            .withRealEnvs()
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    private fun createBspClient(): BspClient {
        return BspClient()
    }

    private fun createBspServer(bspConnectionDetails: BspConnectionDetails): BspServer {
        val process = createAndStartProcess(bspConnectionDetails)

        disconnectActions.add { bspServer?.buildShutdown() }
        disconnectActions.add { bspServer?.onBuildExit() }

        disconnectActions.add { process.waitFor(3, TimeUnit.SECONDS) }
        disconnectActions.add { process.destroy() }

        val bspClient = createBspClient()

        val bspIn = process.inputStream
        disconnectActions.add { bspIn.close() }

        val bspOut = process.outputStream
        disconnectActions.add { bspOut.close() }

        val launcher = createLauncher(bspIn, bspOut, bspClient)
        val listening = launcher.startListening()
        disconnectActions.add { listening.cancel(true) }

        this.bspClient = bspClient

        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(BspServer::class.java)) { _, method, args ->
            println("Calling method: ${method.name} with args: ${args?.joinToString()}")
            method.invoke(launcher.remoteProxy, *args.orEmpty())
        } as BspServer
    }

    private fun parseBspConnectionDetails(file: VirtualFile): BspConnectionDetails? =
        try {
            Gson().fromJson(VfsUtil.loadText(file), BspConnectionDetails::class.java)
        } catch (e: Exception) {
            println("Parsing file '$file' to BspConnectionDetails failed! ${e.message}")
            null
        }

    // TODO
    private fun getBspConnectionDetailsFile(): VirtualFile? =
        "${project.stateStore.projectBasePath}/.bsp/bazelbsp.json".toVirtualFile()

    override fun hasBspServer(): Boolean {
        return getBspConnectionDetailsFile() != null
    }

    override fun getBspTargets(): List<BuildTarget> = queryForBazelTargets(getBspServer()).get().targets

    override fun dispose() = disconnect()

}

private fun String.toVirtualFile(): VirtualFile? =
    VirtualFileManager.getInstance().findFileByNioPath(Path(this))

interface BspServer : BuildServer, RustBuildServer

fun ProcessBuilder.withRealEnvs(): ProcessBuilder {
    val env = environment()
    env.clear()
    env.putAll(EnvironmentUtil.getEnvironmentMap())

    return this
}


fun calculateProjectDetailsWithCapabilities(
    server: BspServer,
    projectDirectory: Path,
    project: Project,
    errorCallback: (Throwable) -> Unit
): CargoWorkspaceData {
    val projectBazelTargets = queryForBazelTargets(server).get()
    val bspWorkspaceRoot = projectBazelTargets.targets.find { it.id.uri == BspConstants.BSP_WORKSPACE_ROOT_URI }
    val workspaceRoot = bspWorkspaceRoot?.baseDirectory?.removePrefix("file://") // TODO there must be a better way to do this
    projectBazelTargets.targets.removeAll { it.id.uri == BspConstants.BSP_WORKSPACE_ROOT_URI }
    val pathReplacer = createSymlinkReplacer(workspaceRoot, projectDirectory)
    val changedWorkspaceRoot = workspaceRoot?.let { pathReplacer(it) }

    var rustBspTargetsIds = collectRustBspTargets(projectBazelTargets.targets).map { it.id }
    changedWorkspaceRoot?.let {
        val bspProjectViewService = project.service<BspProjectViewService>()
        rustBspTargetsIds = bspProjectViewService.filterIncludedPackages(rustBspTargetsIds)
    }

    val projectWorkspaceData = queryForWorkspaceData(server, rustBspTargetsIds).get()

    val projectPackages = createPackages(projectWorkspaceData, pathReplacer)
    val dependencies = createDependencies(projectWorkspaceData)
    val rawDependencies = createRawDependencies(projectWorkspaceData)

    changedWorkspaceRoot?.let {
        val bspProjectViewService = project.service<BspProjectViewService>()
        bspProjectViewService.updateTargets(projectWorkspaceData.resolvedTargets)
        val refreshStatusPublisher = project.messageBus.syncPublisher(BspConnectionService.BSP_WORKSPACE_REFRESH_TOPIC)
        refreshStatusPublisher.onRefreshFinished()

    }

    return CargoWorkspaceData(projectPackages, dependencies, rawDependencies, changedWorkspaceRoot, true)
}

private fun createSymlinkReplacer(
    workspaceRoot: String?,
    projectDirectoryRel: Path
): (String) -> String {
    val projectDirectory = projectDirectoryRel.toAbsolutePath()
    val id: (String) -> String = replacer@{ return@replacer it }
    if (workspaceRoot == null || projectDirectory.toString() == workspaceRoot) {
        return id
    }

    val workspaceRootPath = Paths.get(workspaceRoot)

    // If the selected projectDirectory doesn't resolve directly to the directory that Cargo spat out at us,
    // then there's something a bit special with the cargo workspace, and we don't want to assume anything.
    if (!Files.isSameFile(projectDirectory, workspaceRootPath)) {
        return id
    }

    // Otherwise, it's just a normal symlink.
    val normalisedWorkspace = projectDirectory.normalize().toString() + "/"
    val replacer: (String) -> String = replacer@{
        // TODO: there must be a better way to do this
        // TODO: a need to redo it
        // This function can take both file:// and non-file:// paths, so we need to handle both
        val filePrefix = "file://"
        val (path, prefix) = if (it.startsWith(filePrefix)) {
            Pair(it.removePrefix(filePrefix), filePrefix)
        } else {
            Pair(it, "")
        }

        if (!path.startsWith(workspaceRoot)) return@replacer it
        prefix + normalisedWorkspace + path.removePrefix(workspaceRoot)
    }
    return replacer
}


private fun collectRustBspTargets(bspTargets: List<BuildTarget>): List<BuildTarget> {
    return bspTargets.filter {
        "rust" in it.languageIds
    }
}

private fun createCfgOptions(cfgOptions: Map<String, List<String>>?): CfgOptions? {
    if (cfgOptions.isNullOrEmpty())
        return null
    val (nameOptions, keyValueOptionsList) = cfgOptions.entries.partition { it.value.isEmpty() }
    val name = nameOptions.map { it.key }.toSet()
    val keyValueOptions = keyValueOptionsList.associate { it.key to it.value.toSet() }
    return CfgOptions(keyValueOptions, name)
}

private fun resolveEdition(edition: String): CargoWorkspace.Edition {
    return when (edition) {
        "2015" -> CargoWorkspace.Edition.EDITION_2015
        "2018" -> CargoWorkspace.Edition.EDITION_2018
        "2021" -> CargoWorkspace.Edition.EDITION_2021
        else -> CargoWorkspace.Edition.EDITION_2015
    }
}

private fun resolveTargetKind(targetKind: RustTargetKind, crateTypes: List<RustCrateType>): CargoWorkspace.TargetKind {
    return when (targetKind) {
        RustTargetKind.BENCH -> CargoWorkspace.TargetKind.Bench
        RustTargetKind.BIN -> CargoWorkspace.TargetKind.Bin
        RustTargetKind.CUSTOM_BUILD -> CargoWorkspace.TargetKind.CustomBuild
        RustTargetKind.EXAMPLE -> if (crateTypes.contains(RustCrateType.BIN)) {
            CargoWorkspace.TargetKind.ExampleBin
        } else {
            CargoWorkspace.TargetKind.ExampleLib(resolveCrateTypes(crateTypes))
        }
        RustTargetKind.LIB -> CargoWorkspace.TargetKind.Lib(resolveCrateTypes(crateTypes))
        RustTargetKind.TEST -> CargoWorkspace.TargetKind.Test
        RustTargetKind.UNKNOWN -> CargoWorkspace.TargetKind.Unknown
    }
}

private fun resolveCrateTypes(crateTypes: List<RustCrateType>): EnumSet<CargoWorkspace.LibKind> {
    val libKinds = crateTypes.map {
        when (it) {
            RustCrateType.LIB -> CargoWorkspace.LibKind.LIB
            RustCrateType.DYLIB -> CargoWorkspace.LibKind.DYLIB
            RustCrateType.STATICLIB -> CargoWorkspace.LibKind.STATICLIB
            RustCrateType.CDYLIB -> CargoWorkspace.LibKind.CDYLIB
            RustCrateType.RLIB -> CargoWorkspace.LibKind.RLIB
            RustCrateType.PROC_MACRO -> CargoWorkspace.LibKind.PROC_MACRO
            RustCrateType.BIN -> CargoWorkspace.LibKind.UNKNOWN
            RustCrateType.UNKNOWN -> CargoWorkspace.LibKind.UNKNOWN
        }
    }
    return EnumSet.copyOf(libKinds)
}

private fun resolveOrigin(targetKind: String): PackageOrigin {
    return when (targetKind.lowercase()) {
        "stdlib" -> PackageOrigin.STDLIB
        "workspace" -> PackageOrigin.WORKSPACE
        "dependency" -> PackageOrigin.DEPENDENCY
        "stdlib-dependency" -> PackageOrigin.STDLIB_DEPENDENCY
        else -> PackageOrigin.WORKSPACE
    }
}

private val LOG: Logger = logger<BspConnectionServiceImpl>()

fun createPackages(projectWorkspaceData: RustWorkspaceResult, pathReplacer: (String) -> String): List<CargoWorkspaceData.Package> {
    return projectWorkspaceData.packages.map { rustPackage ->
        CargoWorkspaceData.Package(
            id = rustPackage.id,
            contentRootUrl = pathReplacer(rustPackage.rootUrl),
            name = rustPackage.name,
            version = rustPackage.version,
            targets = rustPackage.resolvedTargets.map { resolveTarget(it, pathReplacer) },
            allTargets = rustPackage.allTargets.map { resolveTarget(it, pathReplacer) },
            source = rustPackage.source,
            origin = resolveOrigin(rustPackage.origin),
            edition = resolveEdition(rustPackage.edition),
            features = rustPackage.features.mapValues { it.value.toList() },
            enabledFeatures = rustPackage.enabledFeatures.toSet(),
            cfgOptions = createCfgOptions(rustPackage.cfgOptions),
            env = rustPackage.env,
            outDirUrl = rustPackage.outDirUrl,
            procMacroArtifact = rustPackage.procMacroArtifact?.let {
                CargoWorkspaceData.ProcMacroArtifact(
                    path = Path(it),
                    hash = HashCode.ofFile(Path(it))
                )
            }
        )
    }.sortedBy { it.id }
}

private fun resolveTarget(target: RustTarget, pathReplacer: (String) -> String): CargoWorkspaceData.Target {
    return CargoWorkspaceData.Target(
        crateRootUrl = pathReplacer(target.crateRootUrl),
        name = target.name,
        kind = resolveTargetKind(target.kind, target.crateTypes),
        edition = resolveEdition(target.edition),
        doctest = target.doctest,
        requiredFeatures = target.requiredFeatures.toList()
    )
}

private fun resolveDependency(dependencyType: String): CargoWorkspace.DepKind {
    return when (dependencyType) {
        "build" -> CargoWorkspace.DepKind.Build
        "dev" -> CargoWorkspace.DepKind.Development
        "normal" -> CargoWorkspace.DepKind.Normal
        else -> CargoWorkspace.DepKind.Unclassified
    }
}

fun createDependencies(projectWorkspaceData: RustWorkspaceResult): Map<PackageId, Set<CargoWorkspaceData.Dependency>> {
    return projectWorkspaceData.dependencies.mapValues { it.value.map { dep ->
        CargoWorkspaceData.Dependency(
            dep.pkg,
            dep.name,
            dep.depKinds.map { kind ->
                CargoWorkspace.DepKindInfo(resolveDependency(kind.kind), kind.target)
            }
        )
    }.toSet() }
}

fun createRawDependencies(projectWorkspaceData: RustWorkspaceResult): Map<PackageId, List<CargoMetadata.RawDependency>> {
    return projectWorkspaceData.rawDependencies.mapValues { it.value.map { dep ->
        CargoMetadata.RawDependency(
            dep.name,
            dep.rename,
            dep.kind,
            dep.target,
            dep.optional,
            dep.usesDefaultFeatures,
            dep.features.toList()
        )
    } }
}


fun queryForWorkspaceData(server: BspServer, params: List<BuildTargetIdentifier>): CompletableFuture<RustWorkspaceResult> {
    return server.rustWorkspace(RustWorkspaceParams(params))
}

fun queryForBazelTargets(server: BspServer): CompletableFuture<WorkspaceBuildTargetsResult> {
    return server.workspaceBuildTargets()
}

private fun <T> CompletableFuture<T>.catchSyncErrors(errorCallback: (Throwable) -> Unit): CompletableFuture<T> =
    this.whenComplete { _, exception ->
        exception?.let { errorCallback(it) }
    }

