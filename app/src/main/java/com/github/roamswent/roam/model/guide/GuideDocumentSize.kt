// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
