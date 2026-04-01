package org.koitharu.kotatsu.core.parser

import org.koitharu.kotatsu.core.content.BlockedContentPolicy
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.model.SortOrder

internal interface WrappedMangaRepository : MangaRepository {

	val delegate: MangaRepository
}

internal class BlockedContentRepository(
	override val delegate: MangaRepository,
	private val policy: BlockedContentPolicy,
) : WrappedMangaRepository {

	override val source: MangaSource
		get() = delegate.source

	override val sortOrders: Set<SortOrder>
		get() = delegate.sortOrders

	override var defaultSortOrder: SortOrder
		get() = delegate.defaultSortOrder
		set(value) {
			delegate.defaultSortOrder = value
		}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = delegate.filterCapabilities

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		if (policy.isBlocked(source)) {
			return emptyList()
		}
		return delegate.getList(offset, order, policy.sanitize(filter))
			.filterNot(policy::isBlocked)
	}

	override suspend fun getDetails(manga: Manga): Manga {
		if (policy.isBlocked(source) || policy.isBlocked(manga)) {
			return policy.toRestricted(manga)
		}
		return delegate.getDetails(manga).let { details ->
			if (policy.isBlocked(details)) {
				policy.toRestricted(details)
			} else {
				details
			}
		}
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		return if (policy.isBlocked(chapter.source)) {
			emptyList()
		} else {
			delegate.getPages(chapter)
		}
	}

	override suspend fun getPageUrl(page: MangaPage): String {
		return if (policy.isBlocked(page.source)) {
			""
		} else {
			delegate.getPageUrl(page)
		}
	}

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return policy.sanitize(delegate.getFilterOptions())
	}

	override suspend fun getRelated(seed: Manga): List<Manga> {
		if (policy.isBlocked(seed) || policy.isBlocked(source)) {
			return emptyList()
		}
		return delegate.getRelated(seed).filterNot(policy::isBlocked)
	}

	override suspend fun find(manga: Manga): Manga? {
		if (policy.isBlocked(manga) || policy.isBlocked(source)) {
			return policy.toRestricted(manga)
		}
		return delegate.find(manga)?.let {
			if (policy.isBlocked(it)) {
				policy.toRestricted(it)
			} else {
				it
			}
		}
	}
}

internal tailrec fun MangaRepository.unwrap(): MangaRepository = when (this) {
	is WrappedMangaRepository -> delegate.unwrap()
	else -> this
}

internal fun MangaRepository.asCachingRepository(): CachingMangaRepository? = unwrap() as? CachingMangaRepository

internal fun MangaRepository.asParserRepository(): ParserMangaRepository? = unwrap() as? ParserMangaRepository
