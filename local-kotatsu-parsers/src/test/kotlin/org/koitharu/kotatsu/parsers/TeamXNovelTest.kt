package org.koitharu.kotatsu.parsers

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.koitharu.kotatsu.parsers.model.search.MangaSearchQuery
import org.koitharu.kotatsu.parsers.model.search.QueryCriteria
import org.koitharu.kotatsu.parsers.model.search.SearchableField.TITLE_NAME
import org.koitharu.kotatsu.test_util.isDistinctBy
import kotlin.time.Duration.Companion.minutes

internal class TeamXNovelTest {

	private val context = MangaLoaderContextMock
	private val timeout = 2.minutes
	private val source = MangaParserSource.TEAMXNOVEL

	@Test
	fun list() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val list = parser.getList(MangaSearchQuery.EMPTY)
		assert(list.isNotEmpty()) { "Manga list is empty" }
		assert(list.isDistinctBy { it.id }) { "Manga list contains duplicated ids" }
		assert(list.all { it.source == source }) { "Unexpected source in list response" }
		assert(list.all { it.publicUrl.contains("olympustaff.com") }) { "Public URLs are not from Olympustaff" }
		checkImageRequest(list.first().coverUrl, source)
	}

	@Test
	fun pagination() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val page1 = parser.getList(MangaSearchQuery.EMPTY)
		val page2 = parser.getList(MangaSearchQuery.Builder().offset(page1.size).build())
		assert(page1.isNotEmpty()) { "Page 1 is empty" }
		assert(page2.isNotEmpty()) { "Page 2 is empty" }
		assert(page1 != page2) { "Pages are equal" }
		assert(page1.intersect(page2.toSet()).isEmpty()) { "Pages intersect" }
	}

	@Test
	fun searchByTitleName() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val subject = parser.getList(MangaSearchQuery.EMPTY).minByOrNull { it.title.length }
			?: error("No manga found")
		val results = parser.getList(
			MangaSearchQuery.Builder()
				.order(SortOrder.RELEVANCE)
				.criterion(QueryCriteria.Match(TITLE_NAME, subject.title))
				.build(),
		)
		assert(results.isNotEmpty()) { "Search results are empty for ${subject.title}" }
		assert(results.any { it.id == subject.id && it.url == subject.url }) {
			"Search did not return the original subject ${subject.publicUrl}"
		}
	}

	@Test
	fun details() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val manga = parser.getList(MangaSearchQuery.EMPTY).first()
		val details = parser.getDetails(manga)
		checkDetails(manga, details)
	}

	@Test
	fun pages() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val manga = parser.getList(MangaSearchQuery.EMPTY).first()
		val details = parser.getDetails(manga)
		val chapter = details.chapters?.firstOrNull() ?: error("No chapter found for ${details.publicUrl}")
		checkChapter(chapter)
		val pages = parser.getPages(chapter)
		assert(pages.isNotEmpty()) { "Pages list is empty for ${chapter.url}" }
		assert(pages.isDistinctBy { it.id }) { "Pages are not distinct by id" }
		assert(pages.all { it.source == source }) { "Unexpected source in pages response" }
		checkPage(parser.getPageUrl(pages.first()), pages.first())
		checkPage(parser.getPageUrl(pages[pages.lastIndex / 2]), pages[pages.lastIndex / 2])
	}

	@Test
	fun domain() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val defaultDomain = parser.domain
		val url = HttpUrl.Builder().host(defaultDomain).scheme("https").toString()
		val response = context.doRequest(url, source)
		val realUrl = response.request.url
		val realDomain = realUrl.topPrivateDomain()
		val realHost = realUrl.host
		assert(defaultDomain == realHost || defaultDomain == realDomain) {
			"Domain mismatch. Required: $defaultDomain, actual: $realDomain, host: $realHost"
		}
	}

	@Test
	fun link() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(source)
		val manga = parser.getList(MangaSearchQuery.EMPTY).first()
		val resolved = context.newLinkResolver(manga.publicUrl).getManga()
		assertNotNull(resolved)
		resolved ?: return@runTest
		assertEquals(manga.id, resolved.id)
		assertEquals(manga.publicUrl, resolved.publicUrl)
		assertEquals(manga.url, resolved.url)
		assertEquals(manga.title, resolved.title)
	}

	private suspend fun checkDetails(manga: Manga, details: Manga) {
		assert(!details.chapters.isNullOrEmpty()) { "Chapters are empty for ${details.publicUrl}" }
		assert(details.description != null) { "Description is missing for ${details.publicUrl}" }
		assert(details.title.startsWith(manga.title)) {
			"Titles mismatch: '${details.title}' vs '${manga.title}'"
		}
		assert(details.chapters!!.isDistinctBy { it.id }) { "Chapters are not distinct by id" }
		assert(details.chapters!!.isDistinctBy { it.url }) { "Chapters are not distinct by url" }
		checkImageRequest(details.coverUrl, source)
	}

	private fun checkChapter(chapter: MangaChapter) {
		assert(chapter.url.startsWith("/series/")) { "Chapter URL is not relative: ${chapter.url}" }
		assert(chapter.source == source) { "Unexpected chapter source" }
	}

	private suspend fun checkPage(pageUrl: String, page: MangaPage) {
		assert(pageUrl.isNotEmpty()) { "Page URL is empty" }
		assert(pageUrl.startsWith("https://")) { "Page URL is not absolute: $pageUrl" }
		assert(page.source == source) { "Unexpected page source" }
		checkImageRequest(pageUrl, page.source)
	}

	private suspend fun checkImageRequest(url: String?, mangaSource: org.koitharu.kotatsu.parsers.model.MangaSource) {
		if (url == null) {
			return
		}
		context.doRequest(url, mangaSource).use {
			assert(it.isSuccessful) { "Request failed: ${it.code}(${it.message}): $url" }
			val mimeType = it.header("content-type")?.substringBefore(';')?.trim()?.lowercase()
			assert(mimeType?.startsWith("image/") == true) { "Wrong mime type: $mimeType for $url" }
		}
	}
}
