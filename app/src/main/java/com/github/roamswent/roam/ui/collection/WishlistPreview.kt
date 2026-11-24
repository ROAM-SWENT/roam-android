// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.collection

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.github.roamswent.roam.ui.guide.SavedGuidesUiState
import com.github.roamswent.roam.ui.theme.SampleAppTheme

private val previewFriends =
    listOf("Ada", "Grace", "Katherine", "Alice", "Bob").map { WishlistFriend(it) }

internal val previewWishlistMonuments =
    listOf(
        WishlistMonument(
            id = "sagrada",
            name = "Sagrada Familia",
            city = "Barcelona",
            country = "Spain",
            scannedBy = previewFriends.take(1),
        ),
        WishlistMonument(
            id = "chillon",
            name = "Château de Chillon",
            city = "Veytaux",
            country = "Switzerland",
            scannedBy = previewFriends.take(2),
        ),
        WishlistMonument(
            id = "colosseum",
            name = "Colosseum",
            city = "Rome",
            country = "Italy",
            scannedBy = previewFriends.take(3),
        ),
        WishlistMonument(
            id = "eiffel",
            name = "Eiffel Tower",
            city = "Paris",
            country = "France",
            scannedBy = previewFriends.take(5),
            isWishlisted = false,
        ),
    )

@Composable
private fun WishlistPreview(darkTheme: Boolean, monuments: List<WishlistMonument>) {
  SampleAppTheme(darkTheme = darkTheme, dynamicColor = false) {
    CollectionScreen(
        state = SavedGuidesUiState(),
        wishlistState = WishlistUiState(monuments),
        initialShowWishlist = true,
    )
  }
}

@Preview(name = "Wishlist - Empty - Light", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
internal fun PreviewWishlistEmptyLight() {
  WishlistPreview(darkTheme = false, monuments = emptyList())
}

@Preview(name = "Wishlist - Empty - Dark", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
internal fun PreviewWishlistEmptyDark() {
  WishlistPreview(darkTheme = true, monuments = emptyList())
}

@Preview(name = "Wishlist - Full - Light", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
internal fun PreviewWishlistFullLight() {
  WishlistPreview(darkTheme = false, monuments = previewWishlistMonuments)
}

@Preview(name = "Wishlist - Full - Dark", showBackground = true, widthDp = 400, heightDp = 900)
@Composable
internal fun PreviewWishlistFullDark() {
  WishlistPreview(darkTheme = true, monuments = previewWishlistMonuments)
}
