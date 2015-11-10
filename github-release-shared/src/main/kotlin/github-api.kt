package cy.github

import cy.rfc6570.*
import org.json.simple.*
import java.io.*
import java.net.*
import java.util.*
import javax.net.ssl.*


data class Auth(val userName: String, val personalAccessToken: String)
data class Repo(val serverEndpoint: String, val user: String, val repoName: String)
data class Release(val tagName: String, val releaseId: Long, val releasesFormat: String, val uploadFormat: String, val htmlPage: String)

fun endpointOf(protoSpec: String?, hostSpec: String) = "${protoSpec ?: "https://"}api.$hostSpec"

fun connectionOf(url: String, method: String = "GET", auth: Auth? = null): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", "Kotlin")
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
    connection.instanceFollowRedirects = true
    connection.allowUserInteraction = false
    connection.defaultUseCaches = false
    connection.doInput = true
    connection.requestMethod = method
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
    request["tag_name"] = tagName
    request["target_commitish"] = null
    request["name"] = releaseTitle
    request["body"] = description
    request["draft"] = false
    request["prerelease"] = preRelease

    val connection = connectionOf(releasesFormat.expandURLFormat(emptyMap<String, String>()), "POST", auth)
    connection.doOutput = true
    connection.outputStream.bufferedWriter().use {
        request.writeJSONString(it)
    }

    //println("${connection.getResponseCode()} ${connection.getResponseMessage()}")
    connection.errorStream?.let { error ->
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
        Release(this.getRaw("tag_name")!!.toString(), this.getAsLong("id")!!, releasesFormat, this.getRaw("upload_url")!!.toString(), this.getRaw("html_url")!!.toString())
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
    connection.doOutput = true

    connection.outputStream.use { out ->
        source.inputStream().use { ins ->
            ins.copyTo(out)
        }
    }

    connection.errorStream?.let { error ->
        error.use {
            it.copyTo(System.out)
        }
        throw IOException("${connection.responseCode} ${connection.responseMessage}")
    }

    connection.withReader {
        it.toJSONObject()
    }
}

fun probeGitHubRepositoryFormat(base: String = "https://api.github.com", auth: Auth? = null): String =
        connectionOf(base, auth = auth).withReader {
            it.toJSONObject()?.getRaw("repository_url")?.toString() ?: throw IllegalArgumentException("No repository_url endpoint found for $base")
        }

fun probeGitHubReleasesFormat(repositoryFormat: String, repo: Repo, auth: Auth? = null): String =
        connectionOf(repositoryFormat.expandURLFormat(mapOf("owner" to repo.user, "repo" to repo.repoName)), auth = auth).withReader {
            it.toJSONObject()?.getRaw("releases_url")?.toString() ?: throw IllegalArgumentException("No releases_url found for $repositoryFormat ($repo)")
        }
