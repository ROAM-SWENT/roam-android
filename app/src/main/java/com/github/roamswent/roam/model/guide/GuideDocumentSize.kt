// Co-authored-by: Codex <codex@openai.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini 3.8 Flash <noreply@google.com>
package com.github.roamswent.roam.model.guide

/** Conservative size check for the Firestore 1 MiB document limit. */
internal object GuideDocumentSize {
  /** Firestore's maximum encoded document size in bytes. */
  const val FIRESTORE_DOCUMENT_LIMIT_BYTES = 1024L * 1024L

  // The relative document path omits the fixed Firestore resource-name prefix. Keep a margin for
  // that prefix and any serialization details not represented by this field-by-field estimate.
  private const val SAFETY_MARGIN_BYTES = 1024L
  private const val DOCUMENT_OVERHEAD_BYTES = 32L
  private const val DOCUMENT_NAME_OVERHEAD_BYTES = 16L
  private const val NUMBER_SIZE_BYTES = 8L
  private const val BOOLEAN_SIZE_BYTES = 1L

  /** Firestore strings include a trailing byte in addition to their UTF-8 content. */
  private fun String.firestoreStringSize(): Long = toByteArray(Charsets.UTF_8).size.toLong() + 1L

  private fun fieldSize(name: String, valueSize: Long): Long =
      name.firestoreStringSize() + valueSize

  /**
   * Estimates Firestore's document size from the fields written by [GuideFirestoreMapper].
   *
   * Blob values use their raw byte count; arrays add the sizes of their values, and maps add their
   * field sizes plus Firestore's 32-byte map overhead. The document name is estimated from the
   * slash-delimited relative path, with its fixed resource-name prefix covered by the safety
   * margin.
   */
  fun estimatedBytes(guide: Guide, documentPath: String): Long {
    val documentNameSize =
        documentPath.split('/').sumOf { it.firestoreStringSize() } + DOCUMENT_NAME_OVERHEAD_BYTES

    var fieldsSize = 0L

    fieldsSize += fieldSize("photo", guide.photoBytes.size.toLong())
    fieldsSize += fieldSize("monumentName", guide.monumentName.firestoreStringSize())
    fieldsSize += fieldSize("confidence", NUMBER_SIZE_BYTES)
    fieldsSize += fieldSize("history", guide.history.firestoreStringSize())
    fieldsSize += fieldSize("facts", guide.facts.sumOf { it.firestoreStringSize() })
    fieldsSize += fieldSize("culture", guide.culture.firestoreStringSize())
    fieldsSize +=
        fieldSize(
            "sections",
            guide.sections.sumOf {
              fieldSize("title", it.title.firestoreStringSize()) +
                  fieldSize("content", it.content.firestoreStringSize()) +
                  DOCUMENT_OVERHEAD_BYTES
            },
        )
    fieldsSize += fieldSize("interestCount", NUMBER_SIZE_BYTES)
    guide.overview?.let { fieldsSize += fieldSize("overview", it.firestoreStringSize()) }
    if (guide.timeline.isNotEmpty()) {
      fieldsSize +=
          fieldSize(
              "timeline",
              guide.timeline.sumOf {
                fieldSize("year", NUMBER_SIZE_BYTES) +
                    fieldSize("event", it.event.firestoreStringSize()) +
                    DOCUMENT_OVERHEAD_BYTES
              },
          )
    }

    guide.location?.let {
      val locationSize =
          fieldSize("latitude", NUMBER_SIZE_BYTES) +
              fieldSize("longitude", NUMBER_SIZE_BYTES) +
              DOCUMENT_OVERHEAD_BYTES

      fieldsSize += fieldSize("location", locationSize)
    }

    if (guide.discoveryDate != null) {
      fieldsSize += fieldSize("discoveryDate", NUMBER_SIZE_BYTES)
    }

    fieldsSize += fieldSize(GuideFirestoreMapper.FAVORITE_FIELD, BOOLEAN_SIZE_BYTES)
    fieldsSize +=
        fieldSize(
            GuideFirestoreMapper.SHARE_AUDIENCE_FIELD,
            guide.shareSettings.audience.name.firestoreStringSize(),
        )
    fieldsSize +=
        fieldSize(
            GuideFirestoreMapper.SHARING_ELIGIBILITY_FIELD,
            guide.sharingEligibility.name.firestoreStringSize(),
        )
    if (guide.shareSettings.sharedWithUids.isNotEmpty()) {
      fieldsSize +=
          fieldSize(
              GuideFirestoreMapper.SHARED_WITH_UIDS_FIELD,
              guide.shareSettings.sharedWithUids.sumOf { it.firestoreStringSize() },
          )
    }
    if (guide.shareSettings.dismissedByUids.isNotEmpty()) {
      fieldsSize +=
          fieldSize(
              GuideFirestoreMapper.DISMISSED_BY_UIDS_FIELD,
              guide.shareSettings.dismissedByUids.sumOf { it.firestoreStringSize() },
          )
    }

    return documentNameSize + fieldsSize + DOCUMENT_OVERHEAD_BYTES
  }

  /** Throws [IllegalArgumentException] when the estimate exceeds the safe document size. */
  fun requireWithinLimit(guide: Guide, documentPath: String) {
    val estimatedBytes = estimatedBytes(guide, documentPath)
    val maximumBytes = FIRESTORE_DOCUMENT_LIMIT_BYTES - SAFETY_MARGIN_BYTES

    require(estimatedBytes <= maximumBytes) {
      "Guide document is too large: estimated $estimatedBytes bytes; " +
          "maximum allowed is $maximumBytes bytes"
    }
  }
}
