package ani.saikou.parsers.anime.extractors

//ani
import ani.saikou.parsers.*
import ani.saikou.client
import ani.saikou.Mapper
import kotlinx.serialization.Serializable
//decrypt
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.net.URL
import org.json.JSONArray

import android.util.Log


class VidSrc(override val server: VideoServer) : VideoExtractor() {

    /*
        Code References: 
        -> https://github.com/aniyomiorg/aniyomi-extensions/blob/master/lib/vidsrc-extractor/src/main/java/eu/kanade/tachiyomi/lib/vidsrcextractor/VidSrcExtractor.kt
        -> https://github.com/recloudstream/cloudstream/blob/master/app/src/main/java/com/lagradost/cloudstream3/extractors/Vidplay.kt
        -> https://github.com/movie-web/providers/tree/dev/src/providers/embeds/vidplay
        -> https://github.com/Ciarands/vidsrc-to-resolver/blob/main/vidsrc.py

        Keys:
        -> https://raw.githubusercontent.com/KillerDogeEmpire/vidplay-keys/keys/keys
        -> https://github.com/Ciarands/vidsrc-keys/blob/main/keys.json / https://raw.githubusercontent.com/Ciarands/vidsrc-keys/main/keys.json

        Notes:
        -> I didn't know kotlin until 3 days ago so if there's any spaghetti codes here plz don't insult + 80% of codes here are copy pasted.
        -> I don't know if there's bugs but i think it works.
        -> I've only tried to fix this source just so I can watch pripara xD, since allanime only have 38 of it and animepahe seems to be broken
            + DantotsuTV seems like going to be much longer than expected. :/
                      
    */

    private val utils by lazy { VidplayUtils() }

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

    // need to override extract() and return a videocontainer().
    override suspend fun extract(): VideoContainer {
        val id = URL(server.embed.url).path.substringAfter("e/")
        val encodedId = utils.encodeID(id, utils.GetKey())
        val mediaUrl = utils.callFutoken(encodedId,  server.embed.url)
        val isMyCloud = server.name == "MyCloud"
        val referer = if (isMyCloud) "https://mcloud.bz/" else "https://aniwave.to"
        videos =  client.get("$mediaUrl", referer = referer).parsed<Data>().result?.sources?.mapNotNull { s ->
            s.file?.let { Video(null,VideoType.M3U8,it) }
        } ?: emptyList()
        return VideoContainer(videos)
    }


    class VidplayUtils {

        //I'm gonna be needing keys. 02/12-CURRENTLY BROKEN IF I FIX THIS ENCODEID AND CALLFUTOKEN, WILL WORK. 02/13-FIXED.
        suspend fun GetKey(): List<String> {
            val key1 = "https://raw.githubusercontent.com/KillerDogeEmpire/vidplay-keys/keys/keys.json"
            val key2 = "https://raw.githubusercontent.com/Ciarands/vidsrc-keys/main/keys.json"

            var resp = client.get(key1).text
            if (resp == "404: Not Found") {
                resp = client.get(key2).text
            }
            val jsonArray = JSONArray(resp)
            val keyList = mutableListOf<String>()

            for (i in 0 until jsonArray.length()) {
                val key = jsonArray.getString(i)
                keyList.add(key)
            }
            return keyList
        }

        // Got from aniyomi || TRIED TO USE LIST<KEYS> IT WORKED TEMPORARILY. THE CURRENT GETKEY() WORKS WELL. DON'T CHANGE THIS ME.
        suspend fun encodeID(videoID: String, keyList: List<String>): String {
            val rc4Key1 = SecretKeySpec(keyList[0].toByteArray(), "RC4")
            val rc4Key2 = SecretKeySpec(keyList[1].toByteArray(), "RC4")
            val cipher1 = Cipher.getInstance("RC4")
            val cipher2 = Cipher.getInstance("RC4")
            cipher1.init(Cipher.DECRYPT_MODE, rc4Key1, cipher1.parameters)
            cipher2.init(Cipher.DECRYPT_MODE, rc4Key2, cipher2.parameters)
            var encoded = videoID.toByteArray()
            encoded = cipher1.doFinal(encoded)
            encoded = cipher2.doFinal(encoded)
            encoded = Base64.encode(encoded, Base64.DEFAULT)
            return encoded.toString(Charsets.UTF_8).replace("/", "_").trim()
        }

        // Got from cloudstream3
        suspend fun callFutoken(id: String, url: String): String? {
            //get the baseurl. I think this will now work with mycloud?
            val slug = url.substringBefore("/e/")
            val script = client.get("$slug/futoken").text
            val k = "k='(\\S+)'".toRegex().find(script)?.groupValues?.get(1) ?: return null
            val a = mutableListOf(k)
            for (i in id.indices) {
                a.add((k[i % k.length].code + id[i].code).toString())
            }
            return "$slug/mediainfo/${a.joinToString(",")}?${url.substringAfter("?")}"
        }

        
    }
}
