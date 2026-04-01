package org.koitharu.kotatsu.parsers.site.ar

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.core.PagedMangaParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.util.attrAsRelativeUrlOrNull
import org.koitharu.kotatsu.parsers.util.attrOrNull
import org.koitharu.kotatsu.parsers.util.generateUid
import org.koitharu.kotatsu.parsers.util.mapToSet
import org.koitharu.kotatsu.parsers.util.nullIfEmpty
import org.koitharu.kotatsu.parsers.util.oneOrThrowIfMany
import org.koitharu.kotatsu.parsers.util.parseHtml
import org.koitharu.kotatsu.parsers.util.requireElementById
import org.koitharu.kotatsu.parsers.util.src
import org.koitharu.kotatsu.parsers.util.textOrNull
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import org.koitharu.kotatsu.parsers.util.toRelativeUrl
import org.koitharu.kotatsu.parsers.util.toTitleCase
import org.koitharu.kotatsu.parsers.util.urlBuilder
import java.util.EnumSet

@MangaSourceParser("TEAMXNOVEL", "TeamXNovel", "ar")
internal class TeamXNovel(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.TEAMXNOVEL, 10) {

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(SortOrder.UPDATED, SortOrder.POPULARITY)

	override val configKeyDomain = ConfigKey.Domain("olympustaff.com")

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED, MangaState.ABANDONED),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val root = if (filter.query.isNullOrEmpty()) {
			loadCatalogPage(page = page, order = order, filter = filter)
		} else {
			loadSearchPage(page = page, query = filter.query)
		}
		return parseMangaList(root)
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val doc = webClient.httpGet("https://$domain/series").parseHtml()
		return doc.requireElementById("select_genre")
			.select("option")
			.mapNotNullTo(mutableSetOf()) { option ->
				val key = option.attr("value").trim()
				if (key.isEmpty()) {
					return@mapNotNullTo null
				}
				MangaTag(
					key = key,
					title = option.text().toTitleCase(sourceLocale),
					source = source,
				)
			}
	}

	private suspend fun loadCatalogPage(page: Int, order: SortOrder, filter: MangaListFilter): Element {
		val builder = if (order == SortOrder.UPDATED) {
			if (
				filter.tags.isNotEmpty() ||
				filter.demographics.isNotEmpty() ||
				filter.types.isNotEmpty() ||
				filter.states.isNotEmpty()
			) {
				throw IllegalArgumentException("Updated sorting does not support other sorting filters")
			}
			urlBuilder()
		} else {
			urlBuilder().addPathSegment("series")
		}
		builder.addQueryParameter("page", page.toString())
		filter.tags.oneOrThrowIfMany()?.let {
			builder.addQueryParameter("genre", it.key)
		}
		filter.types.oneOrThrowIfMany()?.let {
			builder.addQueryParameter("type", contentTypeToFilterValue(it))
		}
		filter.states.oneOrThrowIfMany()?.let {
			builder.addQueryParameter("status", stateToFilterValue(it))
		}
		return webClient.httpGet(builder.build()).parseHtml().body()
	}

	private suspend fun loadSearchPage(page: Int, query: String): Element {
		if (page == 1) {
			val ajaxRoot = webClient.httpGet(
				urlBuilder()
					.addPathSegments("ajax/search")
					.addQueryParameter("keyword", query)
					.build(),
			).parseHtml().body()
			if (findMangaEntries(ajaxRoot).isNotEmpty()) {
				return ajaxRoot
			}
		}
		return webClient.httpGet(
			urlBuilder()
				.addQueryParameter("search", query)
				.addQueryParameter("page", page.toString())
				.build(),
		).parseHtml().body()
	}

	private fun parseMangaList(root: Element): List<Manga> {
		return findMangaEntries(root).mapNotNull { element ->
			val link = if (element.tagName() == "a") {
				element
			} else {
				element.selectFirst("a")
			} ?: return@mapNotNull null
			val href = link.attrAsRelativeUrlOrNull("href")
				?.takeIf(::isMangaUrl)
				?: return@mapNotNull null
			val title = link.select(".tt, h3, h4").text().nullIfEmpty()
				?: link.attrOrNull("title")
				?: link.selectFirst("img")?.attrOrNull("alt")
				?: return@mapNotNull null
			Manga(
				id = generateUid(href),
				title = title,
				altTitles = emptySet(),
				url = href,
				publicUrl = href.toAbsoluteUrl(domain),
				rating = RATING_UNKNOWN,
				contentRating = null,
				coverUrl = link.selectFirst("img")?.src()?.replace("thumbnail_", ""),
				tags = emptySet(),
				state = parseState(link.selectFirst(".status")?.text()),
				authors = emptySet(),
				source = source,
			)
		}.distinctBy { it.url }
	}

	private fun findMangaEntries(root: Element): List<Element> {
		return root.select("div.listupd .bs .bsx").ifEmpty {
			root.select("div.post-body .box")
		}.ifEmpty {
			root.select("a[href*=\\/series\\/]:has(h4)")
		}
	}

	private fun isMangaUrl(url: String): Boolean {
		return SERIES_URL_REGEX.matches(url)
	}

	private fun contentTypeToFilterValue(type: ContentType): String = when (type) {
		ContentType.MANGA -> "مانجا ياباني"
		ContentType.MANHWA -> "مانهوا كورية"
		ContentType.MANHUA -> "مانها صيني"
		else -> ""
	}

	private fun stateToFilterValue(state: MangaState): String = when (state) {
		MangaState.ONGOING -> "مستمرة"
		MangaState.FINISHED -> "مكتمل"
		MangaState.ABANDONED -> "متوقف"
		else -> "مستمرة"
	}

	private fun parseState(raw: String?): MangaState? = when (raw?.trim()) {
		"مستمرة" -> MangaState.ONGOING
		"مكتمل" -> MangaState.FINISHED
		"متوقف" -> MangaState.ABANDONED
		else -> null
	}

	private fun parseInfoValue(doc: Element, label: String): String? {
		return doc.select(".full-list-info")
			.firstOrNull { it.text().contains(label) }
			?.selectFirst("a, span")
			?.textOrNull()
	}

	private fun normalizeChapterTimestamp(value: Long?): Long {
		val timestamp = value ?: return 0L
		return if (timestamp in 1..9_999_999_999L) {
			timestamp * 1000L
		} else {
			timestamp
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val doc = webClient.httpGet(manga.url.toAbsoluteUrl(domain)).parseHtml()
		val mangaUrl = manga.url.toAbsoluteUrl(domain)
		val maxPageChapter = doc.select(".pagination .page-item a")
			.mapNotNull { it.attr("href").substringAfterLast("=").toIntOrNull() }
			.maxOrNull() ?: 1
		val chapters = if (maxPageChapter == 1) {
			parseChapters(doc)
		} else {
			coroutineScope {
				val extraPages = (2..maxPageChapter).map { page ->
					async {
						loadChapters(mangaUrl, page)
					}
				}.awaitAll()
				buildList {
					addAll(parseChapters(doc))
					extraPages.forEach { addAll(it) }
				}
			}
		}.distinctBy { it.url }
			.sortedWith(
				compareBy<MangaChapter>(
					{ if (it.number > 0f) 0 else 1 },
					{ if (it.number > 0f) it.number else Float.MAX_VALUE },
					{ if (it.uploadDate > 0L) it.uploadDate else Long.MAX_VALUE },
					{ it.url },
				),
			)
		return manga.copy(
			title = doc.selectFirst("h1")?.textOrNull() ?: manga.title,
			coverUrl = doc.selectFirst(".whitebox img")?.src() ?: manga.coverUrl,
			state = parseState(parseInfoValue(doc, "الحالة")),
			tags = doc.select(".review-author-info a").mapToSet { a ->
				MangaTag(
					key = a.attr("href").substringAfterLast("="),
					title = a.text(),
					source = source,
				)
			},
			description = doc.selectFirst(".review-content")?.html()?.nullIfEmpty() ?: manga.description,
			chapters = chapters,
		)
	}

	private suspend fun loadChapters(baseUrl: String, page: Int): List<MangaChapter> {
		return parseChapters(webClient.httpGet("$baseUrl?page=$page").parseHtml().body())
	}

	private fun parseChapters(root: Element): List<MangaChapter> {
		val chapterRoot = root.requireElementById("chapter-contact")
		val modernCards = chapterRoot.select(".chapter-card")
		if (modernCards.isNotEmpty()) {
			return modernCards.mapNotNull(::parseModernChapter)
		}
		val modernLinks = chapterRoot.select("a.chapter-link")
		if (modernLinks.isNotEmpty()) {
			return modernLinks.mapNotNull { parseModernChapter(link = it, container = it) }
		}
		return chapterRoot.select(".eplister ul li").mapNotNull { li ->
			val url = li.selectFirst("a")
				?.attrAsRelativeUrlOrNull("href")
				?.takeIf {
					it != "#" && it != "/#" && CHAPTER_URL_REGEX.matches(it)
				}
				?: return@mapNotNull null
			MangaChapter(
				id = generateUid(url),
				title = li.selectFirst(".epl-title")?.textOrNull()
					?: li.selectFirst("a")?.textOrNull()
					?: url.substringAfterLast('/'),
				number = url.substringAfterLast('/').toFloatOrNull() ?: 0f,
				volume = 0,
				url = url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}
	}

	private fun parseModernChapter(card: Element): MangaChapter? {
		val link = card.selectFirst("a.chapter-link, a") ?: return null
		return parseModernChapter(link = link, container = card)
	}

	private fun parseModernChapter(link: Element, container: Element): MangaChapter? {
		val url = link.attrAsRelativeUrlOrNull("href")
			?.takeIf {
				it != "#" && it != "/#" && CHAPTER_URL_REGEX.matches(it)
			}
			?: return null
		val numberLabel = container.selectFirst(".chapter-number")?.textOrNull()
			?: link.selectFirst(".epcur, .chapter-number")?.textOrNull()
		val titleLabel = container.selectFirst(".chapter-title")?.textOrNull()
			?: link.selectFirst(".chapter-title")?.textOrNull()
		return MangaChapter(
			id = generateUid(url),
			title = buildChapterTitle(
				numberLabel = numberLabel,
				titleLabel = titleLabel,
				fallback = url.substringAfterLast('/'),
			),
			number = container.attrOrNull("data-number")?.toFloatOrNull()
				?: url.substringAfterLast('/').toFloatOrNull()
				?: 0f,
			volume = 0,
			url = url,
			scanlator = null,
			uploadDate = normalizeChapterTimestamp(container.attrOrNull("data-date")?.toLongOrNull()),
			branch = null,
			source = source,
		)
	}

	private fun buildChapterTitle(numberLabel: String?, titleLabel: String?, fallback: String): String {
		val normalizedNumber = numberLabel?.trim().orEmpty()
		val normalizedTitle = titleLabel?.trim().orEmpty()
		val chapterDigits = normalizedNumber.substringAfterLast(' ', normalizedNumber).trim()
		return when {
			normalizedNumber.isEmpty() && normalizedTitle.isEmpty() -> fallback
			normalizedNumber.isEmpty() -> normalizedTitle
			normalizedTitle.isEmpty() -> normalizedNumber
			normalizedTitle == chapterDigits || normalizedTitle == normalizedNumber -> normalizedNumber
			else -> "$normalizedNumber - $normalizedTitle"
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val doc = webClient.httpGet(chapter.url.toAbsoluteUrl(domain)).parseHtml()
		return doc.select(".manga-chapter-img").ifEmpty {
			doc.select(".image_list img, .image_list canvas")
		}.mapNotNull { image ->
			val url = image.src()?.toRelativeUrl(domain)
				?: image.attrAsRelativeUrlOrNull("data-src")
				?: return@mapNotNull null
			MangaPage(
				id = generateUid(url),
				url = url,
				preview = null,
				source = source,
			)
		}.distinctBy { it.url }
	}

	private companion object {
		private val SERIES_URL_REGEX = Regex("^/series/[^/]+/?$")
		private val CHAPTER_URL_REGEX = Regex("^/series/[^/]+/[^/]+/?$")
	}
}
