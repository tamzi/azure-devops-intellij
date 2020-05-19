// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.tfs

import com.microsoft.tfs.core.clients.versioncontrol.VersionControlConstants
import com.microsoft.tfs.core.clients.versioncontrol.path.LocalPath
import com.microsoft.tfs.core.clients.versioncontrol.soapextensions.*
import com.microsoft.tfs.core.clients.versioncontrol.specs.ItemSpec
import com.microsoft.tfs.core.util.FileEncoding
import com.microsoft.tfs.model.host.*
import java.text.SimpleDateFormat

private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

private val pendingChangeTypeMap = mapOf(
    ChangeType.ADD to TfsServerStatusType.ADD,
    ChangeType.EDIT to TfsServerStatusType.EDIT,
    ChangeType.ENCODING to TfsServerStatusType.UNKNOWN,
    ChangeType.RENAME to TfsServerStatusType.RENAME,
    ChangeType.DELETE to TfsServerStatusType.DELETE,
    ChangeType.UNDELETE to TfsServerStatusType.UNDELETE,
    ChangeType.BRANCH to TfsServerStatusType.BRANCH,
    ChangeType.MERGE to TfsServerStatusType.MERGE,
    ChangeType.LOCK to TfsServerStatusType.LOCK,
    ChangeType.ROLLBACK to TfsServerStatusType.UNKNOWN,
    ChangeType.SOURCE_RENAME to TfsServerStatusType.RENAME,
    ChangeType.TARGET_RENAME to TfsServerStatusType.UNKNOWN,
    ChangeType.PROPERTY to TfsServerStatusType.EDIT
)

private fun toChangeTypes(changeType: ChangeType): List<TfsServerStatusType> =
    pendingChangeTypeMap.entries.mapNotNull { (k, v) -> if (changeType.contains(k)) v else null }

private fun toPendingChange(pendingSet: PendingSet, pc: PendingChange) = TfsPendingChange(
    pc.serverItem,
    pc.localItem,
    pc.version,
    pc.pendingSetOwner,
    isoDateFormat.format(pc.creationDate.time),
    pc.lockLevelName,
    toChangeTypes(pc.changeType),
    pendingSet.name,
    pendingSet.computer,
    pc.isCandidate,
    pc.sourceServerItem
)

fun toPendingChanges(pendingSet: PendingSet): Iterable<TfsPendingChange> =
    (pendingSet.pendingChanges.asSequence().map { toPendingChange(pendingSet, it) }
            + pendingSet.candidatePendingChanges.orEmpty().map { toPendingChange(pendingSet, it) }).asIterable()

fun TfsPath.toCanonicalPathString(): String = when(this) {
    is TfsLocalPath -> LocalPath.canonicalize(path)
    is TfsServerPath -> path
    else -> throw Exception("Unknown path type: $this")
}

fun TfsPath.toCanonicalPathItemSpec(recursionType: RecursionType): ItemSpec =
    ItemSpec(toCanonicalPathString(), recursionType)

fun ExtendedItem.toTfsItemInfo(): TfsItemInfo {
    val change =
        if (pendingChange == ChangeType.NONE) "none"
        else pendingChange.toUIString(false, this)
    val itemTypeName = itemType.toUIString()
    val lockStatus = lockLevel.toUIString()
    val checkInDate = checkinDate?.time?.let(isoDateFormat::format)
    val encodingName =
        if (encoding == FileEncoding(VersionControlConstants.ENCODING_UNCHANGED)) null
        else encoding?.name
    val fileEncodingName = if (itemType == ItemType.FILE) encodingName else null

    return TfsItemInfo(
        targetServerItem,
        localItem,
        localVersion,
        latestVersion,
        change,
        itemTypeName,
        lockStatus,
        lockOwner,
        deletionID,
        checkInDate,
        fileEncodingName
    )
}
