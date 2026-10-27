// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.guide

/** Conservative size check for the Firestore 1 MiB document limit. */
internal object GuideDocumentSize {
  /** Firestore's maximum encoded document size in bytes. */
  const val FIRESTORE_DOCUMENT_LIMIT_BYTES = 1024 * 1024
  private const val SAFETY_MARGIN_BYTES = 1024
  private const val DOCUMENT_OVERHEAD_BYTES = 32

  /** Estimates the encoded size from field names, UTF-8 values, and conservative overhead. */
  fun estimatedBytes(guide: Guide): Long {
    var size = DOCUMENT_OVERHEAD_BYTES.toLong()
    size += guide.id.utf8Size() + 1
    size += "photo".utf8Size() + 1 + guide.photoBytes.size + 1
    size += "monumentName".utf8Size() + 1 + guide.monumentName.utf8Size() + 1
    size += "confidence".utf8Size() + 1 + java.lang.Double.BYTES
    size += "history".utf8Size() + 1 + guide.history.utf8Size() + 1
    size += "facts".utf8Size() + 1 + 1
    size += guide.facts.sumOf { it.utf8Size() + 1 }
    size += "culture".utf8Size() + 1 + guide.culture.utf8Size() + 1
    guide.location?.let {
      size += "location".utf8Size() + 1 + "latitude".utf8Size() + 1 + java.lang.Double.BYTES
      size += "longitude".utf8Size() + 1 + java.lang.Double.BYTES
    }
    guide.discoveryDate?.let { size += "discoveryDate".utf8Size() + 1 + java.lang.Long.BYTES }
    return size
  }

  /** Throws [IllegalArgumentException] when the estimate exceeds the safe document size. */
  fun requireWithinLimit(guide: Guide) {
    val estimatedBytes = estimatedBytes(guide)
    val maximumBytes = FIRESTORE_DOCUMENT_LIMIT_BYTES - SAFETY_MARGIN_BYTES
    require(estimatedBytes <= maximumBytes) {
      "Guide document is too large: estimated $estimatedBytes bytes; " +
          "maximum allowed is $maximumBytes bytes"
    }
  }

  private fun String.utf8Size(): Long = toByteArray(Charsets.UTF_8).size.toLong()
}
