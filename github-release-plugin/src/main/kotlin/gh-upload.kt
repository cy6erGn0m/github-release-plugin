package cy.github

import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoFailureException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.LifecyclePhase
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.settings.Server
import org.apache.maven.settings.Settings
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher
import kotlin.properties.Delegates

Mojo(name = "gh-upload", defaultPhase = LifecyclePhase.DEPLOY, requiresOnline = true, threadSafe = true)
public class GitHubUpload : AbstractMojo() {

    Component
    var security: SecDispatcher? = null

    Parameter(defaultValue = "\${project}", readonly = true, required = true)
    var project: MavenProject? = null

    Parameter(defaultValue = "\${reactorProjects}", required = true, readonly = true)
    var reactorProjects: List<MavenProject>? = null

    Parameter(property = "maven.deploy.skip", defaultValue = "false")
    var skip: Boolean = false

    Parameter(defaultValue = "\${settings}")
    var settings: Settings? = null

    Parameter(defaultValue = "github", required = true, readonly = true)
    var serverId: String = "github"

    Parameter(required = false)
    var endpointURL: String? = null

    Parameter(required = false)
    var owner: String? = null

    Parameter(required = false)
    var repository: String? = null

    Parameter(required = true, defaultValue = "\${project.version}")
    var tagName: String = ""

    Parameter(required = false)
    var releaseTitle = tagName

    Parameter(required = true, defaultValue = "false")
    var preRelease: Boolean = false

    override fun execute() {
        if (skip) {
            getLog().warn("GitHubUpload mojo has been skipped because maven.deploy.skip=true")
            return
        }

        val repo = getRepo()
        val serverSettings = findServerSettings() ?: throw MojoFailureException("GitHub upload failed: no server configuration found for $serverId in settings.xml")
        val auth = Auth(
                userName = serverSettings.getUsername() ?: throw MojoFailureException("No username configured for github server ${serverSettings.getId()}"),
                personalAccessToken = serverSettings.getPassword()?.decryptIfNeeded() ?: throw MojoFailureException("No password/personal access token specified for github server ${serverSettings.getId()}")
        )

        getLog().info("Contacting server ${serverSettings.getId()} @ ${repo.serverEndpoint}")
        val repositoryFormat = probeGitHubRepositoryFormat(repo.serverEndpoint, auth)
        val releasesFormat = probeGitHubReleasesFormat(repositoryFormat, repo, auth)
        getLog().debug("Releases URL format is $releasesFormat")

        getLog().info("Lookup/create release for tag $tagName")
        val release = findRelease(releasesFormat, tagName, auth) ?: createRelease(auth, releasesFormat, tagName, releaseTitle, "", preRelease)
                ?: throw MojoFailureException("Failed to find/create release for tag $tagName")
        getLog().debug("Found release $release")

        if (settings?.isOffline() ?: false) {
            throw MojoFailureException("Can't upload artifacts in offline mode")
        }

        val defaultArtifact = project?.getArtifact()
        if (defaultArtifact != null) {
            val file = defaultArtifact.getFile()
            if (file != null) {
                getLog().info("Uploading $file")
                upload(auth, release.uploadFormat, file)
            }
        }

        val artifacts = project?.getAttachedArtifacts() ?: emptyList()
        artifacts.map { it.getFile() }.forEach { file ->
            getLog().info("Uploading $file")
            upload(auth, release.uploadFormat, file)
        }

        getLog().info("Upload for project ${project!!.getArtifactId()} completed. See ${release.htmlPage}")
    }

    private fun String.decryptIfNeeded() = "\\{.*\\}".toRegex().match(this)?.let { m ->
        security?.decrypt(m.value)
    } ?: this

    private val interactiveMode: Boolean
        get() = settings?.isInteractiveMode() ?: false

    private fun findServerSettings(): Server? = settings?.getServer(serverId)

    private fun getRepo(): Repo {
        val repos = listOf(project?.getScm()?.getConnection(), project?.getScm()?.getDeveloperConnection())
                .filterNotNull()
                .map { it.parseSCMUrl() }
                .filterNotNull()
                .distinct()

        val hosts = repos.map { it.serverEndpoint }.distinct()
        val owners = repos.map { it.user }.distinct()
        val repositories = repos.map { it.repoName }.distinct()

        val host = when {
            endpointURL != null -> endpointURL!!
            hosts.isEmpty() -> "https://api.github.com"
            hosts.size() > 1 -> throw MojoFailureException("Ambiguous github host specification: please correct connection/developerConnection or specify plugin endpointURL configuration parameter")
            else -> hosts.single()
        }

        val owner = when {
            this.owner != null -> this.owner!!
            owners.isEmpty() -> throw MojoFailureException("Couldn't detect github username: please configure SCM connection/developerConnection or specify plugin's owner configuration parameter")
            owners.size() > 1 -> throw MojoFailureException("Ambiguous github username: please fix SCM connection/developerConnection or specify plugin's owner configuration parameter")
            else -> owners.single()
        }

        val repoName = when {
            this.repository != null -> this.repository!!
            repositories.isEmpty() -> throw MojoFailureException("Couldn't detect github repository name: please configure SCM connection/developerConnection or specify plugin's repository configuration parameter")
            repositories.size() > 1 -> throw MojoFailureException("Ambiguous github repository name: please fix SCM connection/developerConnection or specify plugin's repository configuration parameter")
            else -> repositories.single()
        }

        return Repo(host, owner, repoName)
    }

}
