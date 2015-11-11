package cy.github

import cy.rfc6570.*
import org.json.simple.*
import java.io.*
import java.net.*
import javax.net.ssl.*

data class Repo(val serverEndpoint: String, val user: String, val repoName: String)
data class Release(val tagName: String, val releaseId: Long, val releasesFormat: String, val uploadFormat: String, val htmlPage: String)

fun endpointOf(protoSpec: String?, hostSpec: String) = "${protoSpec ?: "https://"}api.$hostSpec"

fun connectionOf(url: String, method: String = "GET", token: String? = null): HttpURLConnection {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.setRequestProperty("User-Agent", "Kotlin")
    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

    connection.instanceFollowRedirects = false
    connection.allowUserInteraction = false
    connection.defaultUseCaches = false
    connection.useCaches = false
    connection.doInput = true
    connection.requestMethod = method

    connection.connectTimeout = 15000
    connection.readTimeout = 30000

    if (token != null) {
        connection.setRequestProperty("Authorization", "token " + token)
    }

    if (connection is HttpsURLConnection) {
//        connection.setHostnameVerifier { host, sslsession ->
//            if (host in unsafeHosts) {
//            } else DefaultHostVerifier...
//        }
    }

    return connection
}

fun jsonOf(url: String, method: String = "GET", token: String? = null, body: JSONObject? = null): JSONObject? {
    val connection = connectionOf(url, method, token)

    try {
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                body.writeJSONString(writer)
            }
        }

        return connection.withReader {
            JSONValue.parse(it) as JSONObject
        }
    } catch (ffn: FileNotFoundException) {
        return null
    } catch (e: IOException) {
        throw IOException("Failed due to ${connection.errorStream?.bufferedReader()?.readText()}", e)
    } finally {
        connection.disconnect()
    }
}

fun createRelease(token: String, releasesFormat: String, tagName: String, releaseTitle: String, description: String, preRelease: Boolean): Release? {
    val request = JSONObject()
    request["tag_name"] = tagName
    request["name"] = releaseTitle
    request["body"] = description
    request["draft"] = false
    request["prerelease"] = preRelease

    return jsonOf(releasesFormat.expandURLFormat(emptyMap<String, String>()), "POST", token, body = request)?.parseRelease(releasesFormat)
}

fun findRelease(releasesFormat: String, tagName: String, token: String? = null): Release? =
        jsonOf("${releasesFormat.expandURLFormat(emptyMap<String, String>())}/tags/${tagName.encodeURLComponent()}", token = token).parseRelease(releasesFormat)

fun JSONObject?.parseRelease(releasesFormat: String): Release? {
    val id = this?.getAsLong("id")

    return if (this == null || id == null) {
        null
    } else {
        Release(this.getRaw("tag_name")!!.toString(), this.getAsLong("id")!!, releasesFormat, this.getRaw("upload_url")!!.toString(), this.getRaw("html_url")!!.toString())
    }
}

fun upload(token: String, uploadFormat: String, source: File, name: String = source.name) {
    val connection = connectionOf(uploadFormat.expandURLFormat(mapOf("name" to name)), "POST", token)
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

fun probeGitHubRepositoryFormat(base: String = "https://api.github.com", token: String? = null): String =
        connectionOf(base, token = token).withReader {
            it.toJSONObject()?.getRaw("repository_url")?.toString() ?: throw IllegalArgumentException("No repository_url endpoint found for $base")
        }

data class RepositoryUrlFormats(val releasesFormat: String, val tagsFormat: String)
fun probeGitHubReleasesFormat(repositoryFormat: String, repo: Repo, token: String? = null): RepositoryUrlFormats =
        connectionOf(repositoryFormat.expandURLFormat(mapOf("owner" to repo.user, "repo" to repo.repoName)), token = token).withReader {
            it.toJSONObject()?.let { json ->
                RepositoryUrlFormats(
                    releasesFormat = json.required("releases_url"),
                    tagsFormat = json.required("tags_url")
                )
            } ?: throw IllegalArgumentException("No response found")
        }

fun JSONObject.required(name: String) = getRaw(name)?.toString() ?: throw IllegalArgumentException("No $name found")