package com.lagradost.cloudstream3.animeproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*
import kotlin.collections.ArrayList


class MundoDonghuaProvider : MainAPI() {

    override val mainUrl = "https://www.mundodonghua.com"
    override val name = "MundoDonghua"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/lista-donghuas", "Donghuas"),
        )

        val items = ArrayList<HomePageList>()
        items.add(
            HomePageList(
                "Últimos episodios",
                app.get(mainUrl, timeout = 120).document.select("div.row .col-xs-4").map {
                    val title = it.selectFirst("h5").text()
                    val poster = it.selectFirst(".fit-1 img").attr("src")
                    val epRegex = Regex("(\\/(\\d+)\$)")
                    val url = it.selectFirst("a").attr("href").replace(epRegex,"").replace("/ver/","/donghua/")
                    val epnumRegex = Regex("(\\d+)\$|((\\d+) \$)")
                    AnimeSearchResponse(
                        title,
                        fixUrl(url),
                        this.name,
                        TvType.Anime,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),

                    )
                })
        )
        for (i in urls) {
            try {
                val home = app.get(i.first, timeout = 120).document.select(".col-xs-4").map {
                    val title = it.selectFirst(".fs-14").text()
                    val poster = it.selectFirst(".fit-1 img").attr("src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Anime,
                        fixUrl(poster),
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val search =
            app.get("$mainUrl/busquedas/$query", timeout = 120).document.select(".col-xs-4").map {
                val title = it.selectFirst(".fs-14").text()
                val href = fixUrl(it.selectFirst("a").attr("href"))
                val image = it.selectFirst(".fit-1 img").attr("src")
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    fixUrl(image),
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
        return ArrayList(search)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("head meta[property=og:image]").attr("content")
        val title = doc.selectFirst(".ls-title-serie").text()
        val description = doc.selectFirst("p.text-justify.fc-dark").text()
        val genres = doc.select("span.label.label-primary.f-bold").map { it.text() }
        val status = when (doc.selectFirst("div.col-md-6.col-xs-6.align-center.bg-white.pt-10.pr-15.pb-0.pl-15 p span.badge.bg-default")?.text()) {
            "En Emisión" -> ShowStatus.Ongoing
            "Finalizada" -> ShowStatus.Completed
            else -> null
        }
        val episodes = doc.select("ul.donghua-list a").map {
            val name = it.selectFirst(".fs-16").text()
            val link = it.attr("href")
            AnimeEpisode(fixUrl(link), name)
        }.reversed()
        return newAnimeLoadResponse(title, url, TvType.Donghua) {
            posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).document.select("script").apmap { script ->
            if (script.data().contains("eval(function(p,a,c,k,e,d)")) {
               val fembed = script.toString().substringAfter("append|src|https|")
                   .substringBefore("|width|height|").replace("com|","")
                   .replace("diasfem|this|","").replace("fembed_tab|","")
                   .replace("|","-")
                val fembed2 = script.toString().substringAfter("|nemonic|addClass|view_counter|html5|")
                    .substringBefore("width|height|frameborder")
                    .replace("|","-")

                val fembed3 = script.toString().substringAfter("|nemonic|addClass|view_counter|html5|")
                    .substringBefore("|width|height|")

                val fembed4 = script.toString().substringAfter("|iframe|var|false|femplay|diasfem|this||remove|fembed_play|append|src|https|fembed_tab|com|")
                    .substringBefore("|width|height|frameborder|")

                val fembed5 = script.toString().substringAfter("https|console|allowfullscreen|once|frameborder|height||width|")
                    .substringBefore("|split|com|").split('|')
               val link = "https://www.fembed.com/v/${fembed}"
               val link2 = "https://www.fembed.com/v/${fembed2}"
               val link3 = "https://www.fembed.com/v/${fembed3}"
               val link4 = "https://www.fembed.com/v/${fembed4}"
               val link5 = "https://www.fembed.com/v/${fembed5.asReversed()}".replace("[","").replace("]","")
                   .replace(", ","-")
               val test = listOf(link, link2, link3, link4, link5)
                for (url in test){
                    for (extractor in extractorApis) {
                        if (url.startsWith(extractor.mainUrl)) {
                            extractor.getSafeUrl(url, data)?.apmap {
                                callback(it)
                            }
                        }
                    }
                }
            }
            if (script.data().contains("asura")) {
               val asura = script.toString().substringAfter("|thumbnail|image|hls|type|").substringBefore("|replace|file|sources|")
               val extractorLink = "https://www.mdplayer.xyz/nemonicplayer/redirector.php?slug=$asura"
               val testlink = app.get(extractorLink).text
               if (testlink.contains("#EXTM3U"))
                   M3u8Helper().m3u8Generation(
                       M3u8Helper.M3u8Stream(
                           extractorLink,
                           headers = app.get(data).headers.toMap()
                       ), true
                   )
                       .map { stream ->
                           val qualityString = if ((stream.quality ?: 0) == 0) "" else "${stream.quality}p"
                           callback( ExtractorLink(
                               "Asura",
                               "Asura $qualityString",
                               stream.streamUrl,
                               extractorLink,
                               getQualityFromName(stream.quality.toString()),
                               true
                           ))
                       }
            }
        }
        return true
    }
}