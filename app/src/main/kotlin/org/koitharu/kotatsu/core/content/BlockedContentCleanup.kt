package org.koitharu.kotatsu.core.content

import androidx.room.withTransaction
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.filter.data.SavedFiltersRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockedContentCleanup @Inject constructor(
	private val database: MangaDatabase,
	private val settings: AppSettings,
	private val blockedContentPolicy: BlockedContentPolicy,
	private val mangaSourcesRepository: MangaSourcesRepository,
	private val savedFiltersRepository: SavedFiltersRepository,
) {

	suspend fun runIfNeeded() {
		settings.enforceBlockedContentDefaults()
		if (settings.blockedContentPolicyVersion >= POLICY_VERSION) {
			return
		}
		val blockedSources = mangaSourcesRepository.allMangaSources
			.filter(blockedContentPolicy::isBlocked)
			.mapTo(LinkedHashSet()) { it.name }
		val filterSources = buildList {
			add(LocalMangaSource)
			addAll(mangaSourcesRepository.allMangaSources)
			addAll(mangaSourcesRepository.getExternalSources())
		}
		database.withTransaction {
			val blockedMangaIds = database.getMangaDao().findBlockedIds(
				blockedSources = blockedSources,
				blockedRatings = blockedContentPolicy.blockedContentRatingNames,
			)
			if (blockedMangaIds.isNotEmpty()) {
				database.getScrobblingDao().deleteByMangaIds(blockedMangaIds)
			}
			database.getMangaDao().deleteBlocked(
				blockedSources = blockedSources,
				blockedRatings = blockedContentPolicy.blockedContentRatingNames,
			)
			if (blockedSources.isNotEmpty()) {
				database.getSourcesDao().deleteBySources(blockedSources)
			}
		}
		savedFiltersRepository.sanitizeAll(filterSources)
		settings.enforceBlockedContentDefaults()
		settings.blockedContentPolicyVersion = POLICY_VERSION
	}

	private companion object {

		const val POLICY_VERSION = 1
	}
}
