package com.mzanak.animedl

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Anime(val id: Int, val title: String, val cover: String, val episodes: Int?, val status: String)
data class DlFile(val itemTitle: String, val name: String, val url: String, val size: Long)

object Api {
    private val VIDEO = listOf(".mp4", ".mkv", ".avi", ".ogv", ".webm", ".m4v")

    private suspend fun http(url: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.setRequestProperty("User-Agent", "AnimeDL/1.0")
        if (body != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        c.inputStream.bufferedReader().use { it.readText() }
    }

    // Anime list + cover art from AniList's public API (no account needed)
    suspend fun anilist(search: String?): List<Anime> {
        val fields = "id title{romaji english} coverImage{medium} episodes status"
        val query = if (search == null)
            "query{Page(perPage:30){media(type:ANIME,status:RELEASING,sort:POPULARITY_DESC){$fields}}}"
        else
            "query(\$s:String){Page(perPage:30){media(type:ANIME,search:\$s,sort:SEARCH_MATCH){$fields}}}"
        val body = JSONObject().put("query", query).apply {
            if (search != null) put("variables", JSONObject().put("s", search))
        }.toString()
        val arr = JSONObject(http("https://graphql.anilist.co", body))
            .getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        return (0 until arr.length()).map {
            val m = arr.getJSONObject(it)
            val t = m.getJSONObject("title")
            val en = t.optString("english")
            Anime(
                m.getInt("id"),
                if (en.isNotBlank() && en != "null") en else t.optString("romaji"),
                m.getJSONObject("coverImage").optString("medium"),
                if (m.isNull("episodes")) null else m.getInt("episodes"),
                m.optString("status").replace("_", " ").lowercase()
            )
        }
    }

    // Legal, freely downloadable videos from the Internet Archive
    suspend fun archive(title: String): List<DlFile> {
        val clean = title.replace("\"", "").replace(":", " ")
        val q = URLEncoder.encode("title:(\"$clean\") AND mediatype:movies", "UTF-8")
        val docs = JSONObject(
            http("https://archive.org/advancedsearch.php?q=$q&fl%5B%5D=identifier&fl%5B%5D=title&rows=4&output=json")
        ).getJSONObject("response").getJSONArray("docs")
        val out = mutableListOf<DlFile>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.getString("identifier")
            val itemTitle = d.optString("title", id)
            val files = try {
                JSONObject(http("https://archive.org/metadata/$id")).getJSONArray("files")
            } catch (e: Exception) {
                continue
            }
            for (j in 0 until files.length()) {
                val f = files.getJSONObject(j)
                val n = f.getString("name")
                if (VIDEO.any { n.endsWith(it, true) }) {
                    val enc = n.split("/").joinToString("/") { s -> URLEncoder.encode(s, "UTF-8").replace("+", "%20") }
                    out += DlFile(itemTitle, n, "https://archive.org/download/$id/$enc", f.optString("size", "0").toLongOrNull() ?: 0L)
                }
            }
        }
        return out.take(200)
    }

    // audio: filenames/titles containing "dub" count as dubbed. quality: 0 saver, 1 standard, 2 high = file size caps
    fun filter(all: List<DlFile>, dub: Boolean, quality: Int): List<DlFile> {
        val byAudio = all.filter {
            val d = (it.name + it.itemTitle).lowercase().contains("dub")
            if (dub) d else !d
        }
        val cap = longArrayOf(150_000_000L, 600_000_000L, Long.MAX_VALUE)[quality.coerceIn(0, 2)]
        val capped = byAudio.filter { it.size <= cap }
        return (capped.ifEmpty { byAudio }).sortedBy { it.name }
    }

    fun fmt(b: Long): String = when {
        b <= 0 -> ""
        b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
        else -> "%.0f MB".format(b / 1e6)
    }

    fun enqueue(ctx: Context, f: DlFile, wifiOnly: Boolean) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = f.name.substringAfterLast('/')
        val r = DownloadManager.Request(Uri.parse(f.url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Anime/$fileName")
        if (wifiOnly) r.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        dm.enqueue(r)
    }
}
