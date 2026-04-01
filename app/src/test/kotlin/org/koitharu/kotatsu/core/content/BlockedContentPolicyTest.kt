package org.koitharu.kotatsu.core.content

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource

class BlockedContentPolicyTest {

	private val policy = BlockedContentPolicy()
	private val hentaiSource = MangaParserSource.entries.first { it.contentType == ContentType.HENTAI }
	private val safeSource = MangaParserSource.entries.first { it.contentType != ContentType.HENTAI }

	@Test
	fun hentaiSourceIsBlocked() {
		assertTrue(policy.isBlocked(hentaiSource))
	}

	@Test
	fun adultMangaIsBlocked() {
		assertTrue(policy.isBlocked(manga(ContentRating.ADULT)))
	}

	@Test
	fun suggestiveMangaIsBlocked() {
		assertTrue(policy.isBlocked(manga(ContentRating.SUGGESTIVE)))
	}

	@Test
	fun safeMangaIsAllowed() {
		assertFalse(policy.isBlocked(manga(ContentRating.SAFE)))
	}

	@Test
	fun filterSanitizationRemovesBlockedContent() {
		val filter = MangaListFilter(
			contentRating = setOf(ContentRating.SAFE, ContentRating.SUGGESTIVE, ContentRating.ADULT),
			types = setOf(ContentType.MANGA, ContentType.HENTAI),
		)
		val sanitized = checkNotNull(policy.sanitize(filter))
		assertTrue(ContentRating.SAFE in sanitized.contentRating)
		assertFalse(ContentRating.SUGGESTIVE in sanitized.contentRating)
		assertFalse(ContentRating.ADULT in sanitized.contentRating)
		assertTrue(ContentType.MANGA in sanitized.types)
		assertFalse(ContentType.HENTAI in sanitized.types)
	}

	@Test
	fun optionsSanitizationRemovesBlockedContent() {
		val options = MangaListFilterOptions(
			availableContentRating = setOf(ContentRating.SAFE, ContentRating.SUGGESTIVE, ContentRating.ADULT),
			availableContentTypes = setOf(ContentType.MANGA, ContentType.HENTAI),
		)
		val sanitized = policy.sanitize(options)
		assertTrue(ContentRating.SAFE in sanitized.availableContentRating)
		assertFalse(ContentRating.SUGGESTIVE in sanitized.availableContentRating)
		assertFalse(ContentRating.ADULT in sanitized.availableContentRating)
		assertTrue(ContentType.MANGA in sanitized.availableContentTypes)
		assertFalse(ContentType.HENTAI in sanitized.availableContentTypes)
	}

	private fun manga(contentRating: ContentRating?, source: MangaSource = safeSource) = Manga(
		id = 1L,
		title = "Test",
		altTitles = emptySet(),
		url = "/test",
		publicUrl = "https://example.org/test",
		rating = 0.0f,
		contentRating = contentRating,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		largeCoverUrl = null,
		description = null,
		chapters = null,
		source = source,
	)
}
