package ani.saikou.parsers.anime

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.VideoType
import ani.saikou.parsers.anime.extractors.FileMoon
import ani.saikou.parsers.anime.extractors.Mp4Upload
import ani.saikou.parsers.anime.extractors.StreamTape
import ani.saikou.parsers.anime.extractors.VidSrc
import ani.saikou.tryWithSuspend
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.net.URL
// Aniwave utils
import android.util.Base64
import java.net.URLDecoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class AniWave : AnimeParser() {

    override val name = "AniWave"
    override val saveName = "aniwave_to"
    override val hostUrl = "https://aniwave.to"
    override val malSyncBackupName = "9anime"
    override val isDubAvailableSeparately = true

    private val utils by lazy { AniwaveUtils() }

    private val embedHeaders = mapOf("Referer" to "$hostUrl/")

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val animeId = client.get(animeLink, embedHeaders).document.select("#watch-main").attr("data-id")
        val vrfAnimeId = utils.vrfEncrypt(animeId)
        val body = client.get("$hostUrl/ajax/episode/list/$animeId?${vrfAnimeId}").parsed<Response>().result
        return Jsoup.parse(body).body().select("ul > li > a").mapNotNull {
            val id = it.attr("data-ids").split(",")
                .getOrNull(if (selectDub) 1 else 0) ?: return@mapNotNull null
            val num = it.attr("data-num")
            val title = it.selectFirst("span.d-title")?.text()
            val filler = it.hasClass("filler")
            Episode(num, id, title, isFiller = filler)
        }
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val vrfEpisodeLink = utils.vrfEncrypt(episodeLink)
        val body = client.get("$hostUrl/ajax/server/list/$episodeLink?${vrfEpisodeLink}", embedHeaders).parsed<Response>().result
        val document = Jsoup.parse(body)
        return document.select("li").mapNotNull {
            val name = it.text()
            val encodedStreamUrl = getEpisodeLinks(it.attr("data-link-id"))?.result?.url ?: return@mapNotNull null
            val realLink = FileUrl(utils.vrfDecrypt(encodedStreamUrl), embedHeaders)
            VideoServer(name, realLink)
        }
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val extractor: VideoExtractor? = when (server.name) {
            //"Vidstream"     -> Extractor(server)
            "Vidplay"       -> VidSrc(server)
            "MyCloud"       -> VidSrc(server) //i think it works?
            "Streamtape"    -> StreamTape(server)
            "Filemoon"      -> FileMoon(server)
            "Mp4upload"     -> Mp4Upload(server)
            else            -> null
        }
        return extractor
    }

    /* DEPRECATED - MOVED TO Vidplay.kt
    class Extractor(override val server: VideoServer) : VideoExtractor() {

        @Serializable
        data class Data(
            val result: Media? = null
        ) {
            @Serializable
            data class Media(
                val sources: List<Source>? = null
            ) {
                @Serializable
                data class Source(
                    val file: String? = null
                )
            }
        }
        @Serializable
        data class Response (
            val rawURL: String? = null
        )
        
        override suspend fun extract(): VideoContainer {
            val slug = URL(server.embed.url).path.substringAfter("e/")
            val isMyCloud = server.name == "MyCloud"
            val server = if (isMyCloud) "Mcloud" else "Vizcloud"
            val url = "https://9anime.eltik.net/raw$server?query=$slug&apikey=saikou" //<-- this is deprecated
            val apiUrl = client.get(url).parsed<Response>().rawURL
            var videos: List<Video> = emptyList()
            if(apiUrl != null) {
                val referer = if (isMyCloud) "https://mcloud.to/" else "https://9anime.to/"
                videos =  client.get(apiUrl, referer = referer).parsed<Data>().result?.sources?.mapNotNull { s ->
                    s.file?.let { Video(null,VideoType.M3U8,it) }
                } ?: emptyList()
            }
            return  VideoContainer(videos)
        }
    }
    */

    override suspend fun search(query: String): List<ShowResponse> {
        //val vrf = encodeVrf(query) <-- DEPRECATED
        val vrf = utils.vrfEncrypt(query)
        val searchLink =
            "$hostUrl/filter?language%5B%5D=${if (selectDub) "dub" else "sub"}&keyword=${encode(query)}&${vrf}&page=1"
        return client.get(searchLink, embedHeaders).document.select("#list-items div.ani.poster.tip > a").map {
            val link = hostUrl + it.attr("href")
            val img = it.select("img")
            val title = img.attr("alt")
            val cover = img.attr("src")
            ShowResponse(title, link, cover)
        }
    }

    class AniwaveUtils {

        fun vrfEncrypt(input: String): String {
            //val rc4Key = SecretKeySpec("ysJhV6U27FVIjjuk".toByteArray(), "RC4")
            val rc4Key = SecretKeySpec("tGn6kIpVXBEUmqjD".toByteArray(), "RC4") //new key from all.js (https://gist.github.com/tristankeith/68cdbd63618fe7489adc2de6c50b1932)
            val cipher = Cipher.getInstance("RC4")
            cipher.init(Cipher.DECRYPT_MODE, rc4Key, cipher.parameters)
            var vrf = cipher.doFinal(input.toByteArray())
            vrf = Base64.encode(vrf, Base64.URL_SAFE or Base64.NO_WRAP)
            vrf = Base64.encode(vrf, Base64.DEFAULT or Base64.NO_WRAP)
            vrf = vrfShift(vrf)
            vrf = Base64.encode(vrf, Base64.DEFAULT)
            //vrf = rot13(vrf) //Not needed anymore. I don't know but this wass replaced by return r = v(r = r.split("").reverse().join("")); <-- is this from vrfShift?
            val stringVrf = vrf.toString(Charsets.UTF_8)
            return "vrf=${java.net.URLEncoder.encode(stringVrf, "utf-8")}"
        }
    
        fun vrfDecrypt(input: String): String {
            var vrf = input.toByteArray()
            vrf = Base64.decode(vrf, Base64.URL_SAFE)
            //val rc4Key = SecretKeySpec("hlPeNwkncH0fq9so".toByteArray(), "RC4")
            val rc4Key = SecretKeySpec("LUyDrL4qIxtIxOGs".toByteArray(), "RC4") //new key from all.js (https://gist.github.com/tristankeith/68cdbd63618fe7489adc2de6c50b1932)
            val cipher = Cipher.getInstance("RC4")
            cipher.init(Cipher.DECRYPT_MODE, rc4Key, cipher.parameters)
            vrf = cipher.doFinal(vrf)
    
            return URLDecoder.decode(vrf.toString(Charsets.UTF_8), "utf-8")
        }
        
        /* Saving for future reference.
        private fun rot13(vrf: ByteArray): ByteArray {
            for (i in vrf.indices) {
                val byte = vrf[i]
                if (byte in 'A'.code..'Z'.code) {
                    vrf[i] = ((byte - 'A'.code + 13) % 26 + 'A'.code).toByte()
                } else if (byte in 'a'.code..'z'.code) {
                    vrf[i] = ((byte - 'a'.code + 13) % 26 + 'a'.code).toByte()
                }
            }
            return vrf
        }
        */
  
        private fun vrfShift(vrf: ByteArray): ByteArray {
            for (i in vrf.indices) {
                //val shift = arrayOf(-3, 3, -4, 2, -2, 5, 4, 5)[i % 8] //old
                val shift = arrayOf(-2, -4, -5, 6, 2, -3, 3, 6)[i % 8] //new from all.js
                vrf[i] = vrf[i].plus(shift).toByte()
            }
            //return vrf //need to find a way to reverse this since i need QytQTkhzall5NFJM which is = C+PNHsjYy4RL but i keep getting TFI0eVlqc0hOUCtD but this value is = LR4yYjsHNP+C which is the reverse of what i needed.
            return vrf.reversedArray() //lol why did i spent so much time creating those functions when I remember this exists.
        }
    }

//    override suspend fun loadByVideoServers(episodeUrl: String, extra: Map<String, String>?, callback: (VideoExtractor) -> Unit) {
//        tryWithSuspend {
//            val servers = loadVideoServers(episodeUrl, extra).map { getVideoExtractor(it) }
//            val mutex = Mutex()
//            servers.asyncMap {
//                tryWithSuspend {
//                    it?.apply {
//                        if (this is VizCloud) {
//                            mutex.withLock {
//                                load()
//                                callback.invoke(this)
//                            }
//                        } else {
//                            load()
//                            callback.invoke(this)
//                        }
//                    }
//                }
//            }
//        }
//    }

    @Serializable
    private data class Links(val result: Url?) {
        @Serializable
        data class Url(val url: String?)
    }

    @Serializable
    data class Response(val result: String)

    private suspend fun getEpisodeLinks(id: String): Links? {
        val vrfId = utils.vrfEncrypt(id)
        //return tryWithSuspend { client.get("$hostUrl/ajax/server/$id?vrf=${encodeVrf(id)}").parsed() } <-- DEPRECATED
        return tryWithSuspend { client.get("$hostUrl/ajax/server/$id?$vrfId}").parsed() }
    }

    /* DEPRECATED
    @Serializable
    data class SearchData (
        val url: String
    )
    private suspend fun encodeVrf(text: String): String {
        return client.get("https://9anime.eltik.net/vrf?query=$text&apikey=saikou").parsed<SearchData>().url
    }

    private suspend fun decodeVrf(text: String): String {
        return client.get("https://9anime.eltik.net/decrypt?query=$text&apikey=saikou").parsed<SearchData>().url
    }
    */
}
