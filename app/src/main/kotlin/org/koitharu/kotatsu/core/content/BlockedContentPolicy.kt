package org.koitharu.kotatsu.core.content

import org.koitharu.kotatsu.core.model.unwrap
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.prefs.TriStateOption
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.MangaState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockedContentPolicy @Inject constructor() {

	val blockedContentRatings: Set<ContentRating> = setOf(ContentRating.ADULT, ContentRating.SUGGESTIVE)

	val blockedContentRatingNames: Set<String>
		get() = blockedContentRatings.mapTo(LinkedHashSet(blockedContentRatings.size)) { it.name }

	fun isBlocked(source: MangaSource): Boolean = when (val unwrapped = source.unwrap()) {
		is MangaParserSource -> unwrapped.contentType == ContentType.HENTAI
		else -> false
	}

	fun isBlocked(sourceName: String): Boolean = isBlocked(org.koitharu.kotatsu.core.model.MangaSource(sourceName))

	fun isBlocked(contentRating: ContentRating?): Boolean = contentRating in blockedContentRatings

	fun isBlocked(source: MangaSource, contentRating: ContentRating?): Boolean {
		return isBlocked(source) || isBlocked(contentRating)
	}

	fun isBlocked(contentRatingName: String?, isNsfwFallback: Boolean, sourceName: String): Boolean {
		val contentRating = runCatching {
			contentRatingName?.let(ContentRating::valueOf)
		}.getOrNull()
		return isBlocked(sourceName) || isBlocked(contentRating) || (contentRating == null && isNsfwFallback)
	}

	fun isBlocked(manga: Manga): Boolean = isBlocked(manga.source) || isBlocked(manga.contentRating)

	fun sanitize(filter: MangaListFilter?): MangaListFilter? = filter?.copy(
		contentRating = filter.contentRating - blockedContentRatings,
		types = filter.types - ContentType.HENTAI,
	)

	fun sanitize(options: MangaListFilterOptions): MangaListFilterOptions = options.copy(
		availableContentRating = options.availableContentRating - blockedContentRatings,
		availableContentTypes = options.availableContentTypes - ContentType.HENTAI,
	)

	fun sanitizeSettings(values: Map<String, *>): Map<String, Any?> = LinkedHashMap<String, Any?>(values.size + 5).apply {
		putAll(values)
		put(AppSettings.KEY_DISABLE_NSFW, true)
		put(AppSettings.KEY_TRACKER_NO_NSFW, true)
		put(AppSettings.KEY_SUGGESTIONS_EXCLUDE_NSFW, true)
		put(AppSettings.KEY_DISCORD_RPC_SKIP_NSFW, true)
		put(AppSettings.KEY_INCOGNITO_NSFW, TriStateOption.DISABLED.name)
	}

	fun toRestricted(manga: Manga): Manga = manga.copy(
		title = "Unavailable",
		altTitles = emptySet(),
		publicUrl = "",
		coverUrl = null,
		tags = emptySet(),
		state = MangaState.RESTRICTED,
		authors = emptySet(),
		largeCoverUrl = null,
		description = null,
		chapters = emptyList(),
	)
}
