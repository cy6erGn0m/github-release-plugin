package cy.github

import cy.rfc6570.expandURLFormat
import org.json.simple.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import javax.net.ssl.HttpsURLConnection


data class Auth(val userName: String, val personalAccessToken: String)
data class Repo(val serverEndpoint: String, val user: String, val repoName: String)
data class Release(val tagName: String, val releaseId: Long, val releasesFormat: String, val uploadFormat: String, val htmlPage: String)

fun endpointOf(protoSpec: String?, hostSpec: String) = "${protoSpec ?: "https://"}api.$hostSpec"

fun connectionOf(url: String, method: String = "GET", auth: Auth? = null): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", "Kotlin")
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.setInstanceFollowRedirects(true)
    connection.setAllowUserInteraction(false)
    connection.setDefaultUseCaches(false)
    connection.setDoInput(true)
    connection.setRequestMethod(method)
    if (auth != null) {
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("${auth.userName}:${auth.personalAccessToken}".toByteArray()))
    }
    if (connection is HttpsURLConnection) {
//        connection.setHostnameVerifier { host, sslsession ->
//            if (host in unsafeHosts) {
//            } else DefaultHostVerifier...
//        }
    }

    return connection
}

fun createRelease(auth: Auth, releasesFormat: String, tagName: String, releaseTitle: String, description: String, preRelease: Boolean): Release? {
    val request = JSONObject()
    request.set("tag_name", tagName)
    request.set("target_commitish", null)
    request.set("name", releaseTitle)
    request.set("body", description)
    request.set("draft", false)
    request.set("prerelease", preRelease)

    val connection = connectionOf(releasesFormat.expandURLFormat(emptyMap<String, String>()), "POST", auth)
    connection.setDoOutput(true)
    connection.getOutputStream().bufferedWriter().use {
        request.writeJSONString(it)
    }

    //println("${connection.getResponseCode()} ${connection.getResponseMessage()}")
    connection.getErrorStream()?.let { error ->
        error.use {
            it.copyTo(System.out)
        }
        return null
    }

    return connection.withReader {
        it.toJSONObject().parseRelease(releasesFormat)
    }
}

fun findRelease(releasesFormat: String, tagName: String, auth: Auth? = null): Release? =
        connectionOf("${releasesFormat.expandURLFormat(emptyMap<String, String>())}/tags/${tagName.encodeURLComponent()}", auth = auth).withReader { stream ->
            stream.toJSONObject().parseRelease(releasesFormat)
        }

fun JSONObject?.parseRelease(releasesFormat: String): Release? {
    val id = this?.getAsLong("id")

    return if (this == null || id == null) {
        null
    } else {
        Release(this.get("tag_name")!!.toString(), this.getAsLong("id")!!, releasesFormat, this.get("upload_url")!!.toString(), this.get("html_url")!!.toString())
    }
}

fun upload(auth: Auth, uploadFormat: String, source: File, name: String = source.name) {
    val connection = connectionOf(uploadFormat.expandURLFormat(mapOf("name" to name)), "POST", auth)
    connection.setRequestProperty("Content-Type", when (source.extension.toLowerCase()) {
        "txt" -> "text/plain"
        "html", "htm", "xhtml" -> "text/html"
        "md" -> "text/x-markdown"
        "adoc", "asciidoc" -> "text/x-asciidoc"
        "zip", "war" -> "application/x-zip"
        "rar" -> "application/x-rar-compressed"
        "js" -> "application/javascript"
        "gzip", "gz", "tgz" -> "application/x-gzip"
        "bzip2", "bz2", "tbz", "tbz2" -> "application/bzip2"
        "xz", "txz" -> "application/x-xz"
        "jar", "ear", "aar" -> "application/java-archive"
        "tar" -> "application/x-tar"
        "svg", "pom", "xml" -> "text/xml"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "md5", "sha", "sha1", "sha256", "asc" -> "text/plain"

        else -> "binary/octet-stream"
    })
    connection.setDoOutput(true)

    connection.getOutputStream().use { out ->
        source.inputStream().use { ins ->
            ins.copyTo(out)
        }
    }

    connection.getErrorStream()?.let { error ->
        error.use {
            it.copyTo(System.out)
        }
        throw IOException("${connection.getResponseCode()} ${connection.getResponseMessage()}")
    }

    connection.withReader {
        it.toJSONObject()
    }
}

fun probeGitHubRepositoryFormat(base: String = "https://api.github.com", auth: Auth? = null): String =
        connectionOf(base, auth = auth).withReader {
            it.toJSONObject()?.get("repository_url")?.toString() ?: throw IllegalArgumentException("No repository_url endpoint found for $base")
        }

fun probeGitHubReleasesFormat(repositoryFormat: String, repo: Repo, auth: Auth? = null): String =
        connectionOf(repositoryFormat.expandURLFormat(mapOf("owner" to repo.user, "repo" to repo.repoName)), auth = auth).withReader {
            it.toJSONObject()?.get("releases_url")?.toString() ?: throw IllegalArgumentException("No releases_url found for $repositoryFormat ($repo)")
        }
