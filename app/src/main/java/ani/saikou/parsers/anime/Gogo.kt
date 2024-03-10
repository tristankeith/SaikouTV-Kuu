package ani.saikou.parsers.anime

import android.net.Uri
import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.AnimeParser
import ani.saikou.parsers.Episode
import ani.saikou.parsers.ShowResponse
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.extractors.DoodStream
import ani.saikou.parsers.anime.extractors.GogoCDN
import ani.saikou.parsers.anime.extractors.Mp4Upload
import ani.saikou.parsers.anime.extractors.StreamSB

class Gogo : AnimeParser() {
    override val name = "AniTaku"
    override val saveName = "anitaku_to"
    override val hostUrl = "https://anitaku.to" //gogoanime3.net
    override val malSyncBackupName = "Gogoanime"
    override val isDubAvailableSeparately = true

    override suspend fun loadEpisodes(animeLink: String, extra: Map<String, String>?): List<Episode> {
        val list = mutableListOf<Episode>()

        val pageBody = client.get("$hostUrl/category/$animeLink").document
        val lastEpisode = pageBody.select("ul#episode_page > li:last-child > a").attr("ep_end").toString()
        val animeId = pageBody.select("input#movie_id").attr("value").toString()

        val epList = client
            .get("https://ajax.gogocdn.net/ajax/load-list-episode?ep_start=0&ep_end=$lastEpisode&id=$animeId").document
            .select("ul > li > a").reversed()
        epList.forEach {
            val num = it.select(".name").text().replace("EP", "").trim()
            list.add(Episode(num, hostUrl + it.attr("href").trim()))
        }

        return list
    }

    private fun httpsIfy(text: String): String {
        return if (text.take(2) == "//") "https:$text"
        else text
    }

    override suspend fun loadVideoServers(episodeLink: String, extra: Any?): List<VideoServer> {
        val list = mutableListOf<VideoServer>()
        client.get(episodeLink).document.select("div.anime_muti_link > ul > li").forEach {
            val name = it.select("a").text().replace("Choose this server", "")
            val url = httpsIfy(it.select("a").attr("data-video"))
            val embed = FileUrl(url, mapOf("referer" to hostUrl))

            list.add(VideoServer(name, embed))
        }
        return list
    }

    override suspend fun getVideoExtractor(server: VideoServer): VideoExtractor? {
        val domain = Uri.parse(server.embed.url).host ?: return null
        println("domain: $domain")
        val extractor: VideoExtractor? = when {
            "taku" in domain      -> GogoCDN(server)
            "sb" in domain        -> StreamSB(server)
            "dood" in domain      -> DoodStream(server)
            "mp4" in domain       -> Mp4Upload(server)
            else                  -> null
        }
        return extractor
    }

    override suspend fun search(query: String): List<ShowResponse> {
        val encoded = encode(query + if (selectDub) " (Dub)" else "")
        val list = mutableListOf<ShowResponse>()
        client.get("$hostUrl/search.html?keyword=$encoded").document
            .select(".last_episodes > ul > li div.img > a").forEach {
                val link = it.attr("href").toString().replace("/category/", "")
                val title = it.attr("title")
                val cover = it.select("img").attr("src")
                list.add(ShowResponse(title, link, cover))
            }
        return list
        /*
        return client.get("$hostUrl/filter.html?keyword=$encoded", referer = "$hostUrl/").document.select(".last_episodes > ul > li > div.img > a").map { x ->
            val link = x.attr("href").toString().replace("/category/", "")
            val title = x.attr("title")
            val cover = x.select("img").attr("src")
            ShowResponse(title, link, cover)
        }
        */
    }
}
