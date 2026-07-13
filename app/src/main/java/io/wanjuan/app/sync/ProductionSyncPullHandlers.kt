package io.wanjuan.app.sync

import io.wanjuan.app.sync.model.SyncBookGroupPayload
import io.wanjuan.app.sync.model.SyncBookPayload
import io.wanjuan.app.sync.model.SyncBookSourcePayload
import io.wanjuan.app.sync.model.SyncObjectType
import io.wanjuan.app.sync.model.SyncOrderPayload
import io.wanjuan.app.sync.model.SyncRssSourcePayload
import io.wanjuan.app.sync.model.SyncRuleSubPayload
import io.wanjuan.app.sync.model.SyncTombstonePayload
import io.wanjuan.app.sync.model.SyncVersion
import io.wanjuan.app.sync.remote.SyncRemoteFile
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonObject

fun productionSyncPullHandlers(
    groupCoordinator: BookGroupSyncCoordinator,
    bookshelfCoordinator: BookshelfSyncCoordinator,
    bookSourceCoordinator: BookSourceSyncCoordinator,
    rssSourceCoordinator: RssSourceSyncCoordinator,
    ruleSubCoordinator: RuleSubSyncCoordinator
): List<SyncPullHandler> {
    val objects = listOf(
        entityHandler<SyncBookGroupPayload>(
            directory = "bookGroups",
            objectType = SyncObjectType.BookGroup,
            objectId = { it.groupSyncId },
            version = { SyncVersion(it.updatedAt, it.updatedByDeviceId) },
            contentHash = SyncPayloadHash::bookGroup,
            apply = {
                groupCoordinator.applyRemote(it)
                SyncApplyOutcome.Updated
            }
        ),
        entityHandler<SyncBookSourcePayload>(
            directory = "bookSources",
            objectType = SyncObjectType.BookSource,
            objectId = { it.sourceHash },
            version = { SyncVersion(it.sourceUpdatedAt, it.updatedByDeviceId) },
            contentHash = SyncPayloadHash::bookSource,
            apply = {
                bookSourceCoordinator.applyRemoteSource(it)
                SyncApplyOutcome.Updated
            }
        ),
        entityHandler<SyncRssSourcePayload>(
            directory = "rssSources",
            objectType = SyncObjectType.RssSource,
            objectId = { it.sourceHash },
            version = { SyncVersion(it.sourceUpdatedAt, it.updatedByDeviceId) },
            contentHash = SyncPayloadHash::rssSource,
            apply = rssSourceCoordinator::applyRemoteSource
        ),
        entityHandler<SyncRuleSubPayload>(
            directory = "ruleSubs",
            objectType = SyncObjectType.RuleSub,
            objectId = { it.ruleSubHash },
            version = { SyncVersion(it.subscriptionUpdatedAt, it.updatedByDeviceId) },
            contentHash = SyncPayloadHash::ruleSub,
            apply = {
                ruleSubCoordinator.applyRemoteSub(it)
                SyncApplyOutcome.Updated
            }
        ),
        entityHandler<SyncBookPayload>(
            directory = "books",
            objectType = SyncObjectType.Book,
            objectId = { it.bookSyncId },
            version = {
                SyncVersion(maxOf(it.shelfUpdatedAt, it.catalogUpdatedAt), it.updatedByDeviceId)
            },
            contentHash = SyncPayloadHash::book,
            apply = {
                bookshelfCoordinator.applyRemoteBook(it)
                SyncApplyOutcome.Updated
            }
        ),
        SyncOrderPullHandler(
            groupCoordinator,
            bookshelfCoordinator,
            bookSourceCoordinator,
            rssSourceCoordinator,
            ruleSubCoordinator
        )
    )
    val tombstones = listOf(
        tombstoneHandler("tombstones/bookGroups", SyncObjectType.BookGroup) {
            if (groupCoordinator.applyRemoteDelete(it.objectId)) {
                SyncApplyOutcome.Deleted
            } else {
                SyncApplyOutcome.Skipped
            }
        },
        tombstoneHandler("tombstones/bookSources", SyncObjectType.BookSource) {
            bookSourceCoordinator.applyRemoteDelete(it)
            SyncApplyOutcome.Deleted
        },
        tombstoneHandler("tombstones/rssSources", SyncObjectType.RssSource) {
            rssSourceCoordinator.applyRemoteDelete(it)
        },
        tombstoneHandler("tombstones/ruleSubs", SyncObjectType.RuleSub) {
            ruleSubCoordinator.applyRemoteDelete(it)
            SyncApplyOutcome.Deleted
        },
        tombstoneHandler("tombstones/books", SyncObjectType.Book) {
            if (bookshelfCoordinator.applyRemoteDelete(it.objectId)) {
                SyncApplyOutcome.Deleted
            } else {
                SyncApplyOutcome.Skipped
            }
        }
    )
    return objects + tombstones
}

private inline fun <reified T> entityHandler(
    directory: String,
    objectType: String,
    crossinline objectId: (T) -> String,
    crossinline version: (T) -> SyncVersion,
    crossinline contentHash: (T) -> String,
    crossinline apply: (T) -> SyncApplyOutcome
): SyncPullHandler = object : SyncPullHandler {
    override val directories: List<String> = listOf(directory)

    override fun identity(file: SyncRemoteFile): SyncIdentity? =
        file.jsonId()?.let { SyncIdentity(objectType, it) }

    override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
        val payload = GSON.fromJsonObject<T>(json).getOrThrow()
        val identity = requireNotNull(identity(file))
        require(objectId(payload) == identity.objectId) { "Remote object ID mismatch: ${file.path}" }
        return SyncRemoteCandidate(
            identity = identity,
            path = file.path,
            contentHash = contentHash(payload),
            objectVersion = version(payload),
            deleteVersion = null,
            payloadJson = json,
            lastModifiedAt = file.lastModifiedAt
        )
    }

    override fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome =
        apply(GSON.fromJsonObject<T>(candidate.payloadJson).getOrThrow())
}

private fun tombstoneHandler(
    directory: String,
    objectType: String,
    apply: (SyncTombstonePayload) -> SyncApplyOutcome
): SyncPullHandler = object : SyncPullHandler {
    override val directories: List<String> = listOf(directory)
    override val usesModifiedTimeMarker: Boolean = false

    override fun identity(file: SyncRemoteFile): SyncIdentity? =
        file.jsonId()?.let { SyncIdentity(objectType, it) }

    override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
        val payload = GSON.fromJsonObject<SyncTombstonePayload>(json).getOrThrow()
        val identity = requireNotNull(identity(file))
        require(payload.objectType == objectType && payload.objectId == identity.objectId) {
            "Remote tombstone identity mismatch: ${file.path}"
        }
        return SyncRemoteCandidate(
            identity = identity,
            path = file.path,
            contentHash = SyncCanonicalJson.hash(payload),
            objectVersion = null,
            deleteVersion = SyncVersion(payload.deletedAt, payload.deletedByDeviceId),
            payloadJson = json,
            // Object and tombstone share one metadata row. Do not persist a tombstone
            // file marker into the object's marker slot, otherwise equal/coarse WebDAV
            // mtimes could hide a later object restoration.
            lastModifiedAt = 0L
        )
    }

    override fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome =
        apply(GSON.fromJsonObject<SyncTombstonePayload>(candidate.payloadJson).getOrThrow())
}

private class SyncOrderPullHandler(
    private val groups: BookGroupSyncCoordinator,
    private val books: BookshelfSyncCoordinator,
    private val bookSources: BookSourceSyncCoordinator,
    private val rssSources: RssSourceSyncCoordinator,
    private val ruleSubs: RuleSubSyncCoordinator
) : SyncPullHandler {
    override val directories: List<String> = listOf("order")

    override fun identity(file: SyncRemoteFile): SyncIdentity? = when (file.displayName) {
        "bookGroups.json" -> SyncIdentity(SyncObjectType.BookGroupOrder, "bookGroups")
        "bookshelf.json" -> SyncIdentity(SyncObjectType.BookshelfOrder, "bookshelf")
        "bookSources.json" -> SyncIdentity(SyncObjectType.BookSourceOrder, "bookSources")
        "rssSources.json" -> SyncIdentity(SyncObjectType.RssSourceOrder, "rssSources")
        "ruleSubs.json" -> SyncIdentity(SyncObjectType.RuleSubOrder, "ruleSubs")
        else -> null
    }

    override fun parse(file: SyncRemoteFile, json: String): SyncRemoteCandidate {
        val identity = requireNotNull(identity(file))
        val payload = GSON.fromJsonObject<SyncOrderPayload>(json).getOrThrow()
        return SyncRemoteCandidate(
            identity = identity,
            path = file.path,
            contentHash = SyncPayloadHash.order(payload),
            objectVersion = SyncVersion(payload.updatedAt, payload.updatedByDeviceId),
            deleteVersion = null,
            payloadJson = json,
            lastModifiedAt = file.lastModifiedAt
        )
    }

    override fun applyRemote(candidate: SyncRemoteCandidate): SyncApplyOutcome {
        val payload = GSON.fromJsonObject<SyncOrderPayload>(candidate.payloadJson).getOrThrow()
        return when (candidate.identity.objectType) {
            SyncObjectType.BookGroupOrder -> groups.applyRemoteOrder(payload).let { SyncApplyOutcome.Updated }
            SyncObjectType.BookshelfOrder -> books.applyRemoteOrder(payload).let { SyncApplyOutcome.Updated }
            SyncObjectType.BookSourceOrder -> bookSources.applyRemoteOrder(payload).let { SyncApplyOutcome.Updated }
            SyncObjectType.RssSourceOrder -> rssSources.applyRemoteOrder(payload)
            SyncObjectType.RuleSubOrder -> ruleSubs.applyRemoteOrder(payload).let { SyncApplyOutcome.Updated }
            else -> SyncApplyOutcome.Skipped
        }
    }
}

private fun SyncRemoteFile.jsonId(): String? =
    displayName.takeIf { it.endsWith(".json") }
        ?.removeSuffix(".json")
        ?.takeIf { it.isNotBlank() }
