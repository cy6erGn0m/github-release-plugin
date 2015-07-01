package cy.github

import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.Reader
import java.net.URLConnection
import java.net.URLEncoder

fun String.encodeURLComponent() = URLEncoder.encode(this, "UTF-8")
private fun <T> URLConnection.withReader(block: (Reader) -> T): T = getInputStream().reader().use(block)
private fun JSONObject.getAsLong(key: String) = (get(key) as? Number)?.toLong()
private fun Reader.toJSONObject(): JSONObject? = JSONParser().parse(this) as? JSONObject

fun String.parseSCMUrl(): Repo? =
        "^scm:git:([^\\@]+@)?(https?://)?([^:]+):([^/]+)/([^/]+)\\.git".toRegex().match(this)?.let { match ->
            Repo(endpointOf(match.groups[2]?.value, match.groups[3]!!.value), match.groups[4]!!.value, match.groups[5]!!.value)
        }
