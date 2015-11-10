package cy.github

import org.json.simple.*
import org.json.simple.parser.*
import java.io.*
import java.net.*

fun String.encodeURLComponent() = URLEncoder.encode(this, "UTF-8")
internal fun <T> URLConnection.withReader(block: (Reader) -> T): T = getInputStream().reader().use(block)
internal fun JSONObject.getAsLong(key: String) = (get(key) as? Number)?.toLong()
internal fun Reader.toJSONObject(): JSONObject? = JSONParser().parse(this) as? JSONObject

fun String.parseSCMUrl(): Repo? =
        "^scm:git:([^\\@]+@)?(https?://)?([^:]+):([^/]+)/([^/]+)\\.git".toRegex().match(this)?.let { match ->
            Repo(endpointOf(match.groups[2]?.value, match.groups[3]!!.value), match.groups[4]!!.value, match.groups[5]!!.value)
        }
