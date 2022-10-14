package eu.kanade.tachiyomi.extension.vi.hentaivnvip

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG1 = "popularManga"
private const val TAG2 = "latestMangas"
private const val TAG3 = "searchManga"
private const val TAG4 = "mangaDetail"
private const val TAG5 = "mangaChapters"

class HentaiVNVip : ParsedHttpSource() {

    override val name = "HentaiVNVip"

    override val baseUrl = "https://hentaivnvip.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient
//        .newBuilder()
//        .connectTimeout(30, TimeUnit.SECONDS)
//        .readTimeout(1, TimeUnit.MINUTES)
//        .rateLimit(2)
//        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "*/*")
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

//    private val newHeaders = headersBuilder().build()
    private val newHeaders = Headers.Builder().apply {
        // User-Agent required for authorization through third-party accounts (mobile version for correct display in WebView)
        add("Accept", "*/*")
//        add("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G980F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36")
        add("User-Agent", "Mozilla")
//        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
        add("Referer", baseUrl)
    }.build()

    private val json: Json by injectLazy()

//    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)

    override fun popularMangaSelector() = "div.comics-grid > div.comics >  div.form-row div.entry > a:first-child"

    override fun latestUpdatesSelector() = "div.comics-grid > div.form-row div.entry > a:first-child"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/truyen-hot/", newHeaders)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/truyen-hentai-moi/page/$page", newHeaders)
    }

    private fun listMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.attr("title")
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    override fun popularMangaFromElement(element: Element): SManga {
//        Log.d(TAG1, "Element: $element")
//        Log.d(TAG1, element.attr("href"))
//        Log.d(TAG1, element.attr("title"))
//        Log.d(TAG1, element.select("img").toString())
        return listMangaFromElement(element)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
//        Log.d(TAG2, "Element: $element")
        return listMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun latestUpdatesNextPageSelector() = "div.z-pagination > a.next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val temp = "$baseUrl/truyen-hentai-moi/"
        val url = temp.toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("q", query)
        return GET(url.toString().replace("m.", ""), newHeaders)
    }

    override fun searchMangaSelector() = "div.comics-grid > div.form-row div.entry > a:first-child"

    override fun searchMangaFromElement(element: Element): SManga {
        // Log.d(TAG3, "Element: $element")
        return listMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("section.comic-info > div.row").first()
        Log.d(TAG4, "manga detail element: $infoElement")
        val manga = SManga.create()
        manga.description = infoElement.select("div.info > div.comic-description > div.inner").first()?.text()
        manga.genre = infoElement.select("div.info > div.meta-data > div.genre a").joinToString { it.text() }
        manga.author = document.select("div.info > div.meta-data > div.author i a")?.text()
        manga.status = infoElement.select("div.col-sm-auto > div.tsinfo > div.imptdt > i").text()
            .orEmpty()
            .let { parseStatus(it) }
//        manga.thumbnail_url = document.select("div.thumbnail > img").first()?.attr("src")
        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("Đang Cập Nhật") -> SManga.ONGOING
        status.contains("Đã hoàn thành") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.chap-list > div.d-flex > a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        Log.d(TAG5, "chapter: ${element.toString().slice(0..100)}")
        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.select("span").first().text() ?: ""
        chapter.date_upload = element.select("span")[1]?.text()?.let {
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).parse(it)?.time ?: 0
        } ?: 0
        Log.d(TAG5, "chapter url: ${chapter.url}")
        return chapter
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, newHeaders)

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
//        val pageUrl = document.select("div.chapter-content > div.content-text > div.text-center > img").first().attr("src")
        document.select("div.chapter-content > div.content-text > div.text-center > img").forEachIndexed { i, e ->
            val imageUrl = e.attr("src")
            pages.add(Page(i, imageUrl, imageUrl))
        }

        // Some chapters use js script to render images
//        val script = document.select("article#content > script").lastOrNull()
//        if (script != null && script.data().contains("listImageCaption")) {
//            val imagesStr = script.data().split(";")[0].split("=").last().trim()
//            val imageArr = json.parseToJsonElement(imagesStr).jsonArray
//            for (image in imageArr) {
//                val imageUrl = image.jsonObject["url"]!!.jsonPrimitive.content
//                pages.add(Page(pages.size, pageUrl, imageUrl))
//            }
//        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    private class Status : Filter.Select<String>("Status", arrayOf("Sao cũng được", "Đang tiến hành", "Đã hoàn thành", "Tạm ngưng"))
    private class Author : Filter.Text("Tác giả")
    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

//    override fun getFilterList() = FilterList(
//        Status(),
//        GenreList(getGenreList()),
//        Author()
//    )
//    override fun getFilterList(): FilterList {
//        throw UnsupportedOperationException("Not used")
//    }

    private fun getGenreList() = listOf(
        Genre("16+", 54),
        Genre("18+", 45),
        Genre("Action", 1),
        Genre("Adult", 2),
        Genre("Adventure", 3),
        Genre("Anime", 4),
        Genre("Comedy", 5),
        Genre("Comic", 6),
        Genre("Doujinshi", 7),
        Genre("Drama", 49),
        Genre("Ecchi", 48),
        Genre("Even BT", 60),
        Genre("Fantasy", 50),
        Genre("Game", 61),
        Genre("Gender Bender", 51),
        Genre("Harem", 12),
        Genre("Historical", 13),
        Genre("Horror", 14),
        Genre("Isekai/Dị Giới", 63),
        Genre("Josei", 15),
        Genre("Live Action", 16),
        Genre("Magic", 46),
        Genre("Manga", 55),
        Genre("Manhua", 17),
        Genre("Manhwa", 18),
        Genre("Martial Arts", 19),
        Genre("Mature", 20),
        Genre("Mecha", 21),
        Genre("Mystery", 22),
        Genre("Nấu ăn", 56),
        Genre("NTR", 62),
        Genre("One shot", 23),
        Genre("Psychological", 24),
        Genre("Romance", 25),
        Genre("School Life", 26),
        Genre("Sci-fi", 27),
        Genre("Seinen", 28),
        Genre("Shoujo", 29),
        Genre("Shoujo Ai", 30),
        Genre("Shounen", 31),
        Genre("Shounen Ai", 32),
        Genre("Slice of Life", 33),
        Genre("Smut", 34),
        Genre("Soft Yaoi", 35),
        Genre("Soft Yuri", 36),
        Genre("Sports", 37),
        Genre("Supernatural", 38),
        Genre("Tạp chí truyện tranh", 39),
        Genre("Tragedy", 40),
        Genre("Trap", 58),
        Genre("Trinh thám", 57),
        Genre("Truyện scan", 41),
        Genre("Video clip", 53),
        Genre("VnComic", 42),
        Genre("Webtoon", 52),
        Genre("Yuri", 59)
    )
}
