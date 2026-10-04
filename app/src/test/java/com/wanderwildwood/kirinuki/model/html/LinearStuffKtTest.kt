package com.wanderwildwood.kirinuki.model.html

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearStuffKtTest {
    @Test
    fun imageUrlsIncludesNestedImagesAndAllSourceCandidates() {
        val directImage =
            LinearImage(
                ids = emptySet(),
                sources =
                    listOf(
                        LinearImageSource(
                            imgUri = "https://example.com/direct.jpg",
                            widthPx = 1200,
                            heightPx = 800,
                            pixelDensity = null,
                            screenWidth = null,
                        ),
                        LinearImageSource(
                            imgUri = "https://example.com/direct-600.jpg",
                            widthPx = 600,
                            heightPx = 400,
                            pixelDensity = null,
                            screenWidth = null,
                        ),
                    ),
                caption = null,
                link = null,
            )
        val nestedImage =
            LinearImage(
                ids = emptySet(),
                sources =
                    listOf(
                        LinearImageSource(
                            imgUri = "https://example.com/nested.jpg",
                            widthPx = 900,
                            heightPx = 600,
                            pixelDensity = null,
                            screenWidth = null,
                        ),
                    ),
                caption = null,
                link = null,
            )
        val tableImage =
            LinearImage(
                ids = emptySet(),
                sources =
                    listOf(
                        LinearImageSource(
                            imgUri = "https://example.com/table.jpg",
                            widthPx = 700,
                            heightPx = 500,
                            pixelDensity = null,
                            screenWidth = null,
                        ),
                    ),
                caption = null,
                link = null,
            )

        val article =
            LinearArticle(
                elements =
                    listOf(
                        directImage,
                        LinearListItem(ids = emptySet(), orderedIndex = null, content = listOf(nestedImage)),
                        LinearTable(
                            ids = emptySet(),
                            rowCount = 1,
                            colCount = 1,
                            cells =
                                listOf(
                                    LinearTableCellItem(
                                        type = LinearTableCellItemType.DATA,
                                        colSpan = 1,
                                        rowSpan = 1,
                                        content = listOf(tableImage),
                                    ),
                                ),
                            leftToRight = true,
                        ),
                    ),
            )

        assertEquals(
            setOf(
                "https://example.com/direct.jpg",
                "https://example.com/direct-600.jpg",
                "https://example.com/nested.jpg",
                "https://example.com/table.jpg",
            ),
            article.imageUrls,
        )
    }

    @Test
    fun containsImageUrlMatchesNestedUrls() {
        val article =
            LinearArticle(
                elements =
                    listOf(
                        LinearBlockQuote(
                            ids = emptySet(),
                            cite = null,
                            content =
                                listOf(
                                    LinearImage(
                                        ids = emptySet(),
                                        sources =
                                            listOf(
                                                LinearImageSource(
                                                    imgUri = "https://example.com/hero.jpg",
                                                    widthPx = 1200,
                                                    heightPx = 800,
                                                    pixelDensity = null,
                                                    screenWidth = null,
                                                ),
                                            ),
                                        caption = null,
                                        link = null,
                                    ),
                                ),
                        ),
                    ),
            )

        assertTrue(article.containsImageUrl("https://example.com/hero.jpg"))
        assertFalse(article.containsImageUrl("https://example.com/missing.jpg"))
    }

    private fun text(value: String) = LinearText(ids = emptySet(), text = value, blockStyle = LinearTextBlockStyle.TEXT)

    @Test
    fun withoutRepeatedTitleDropsAHeadingThatRepeatsTheTitle() {
        val article =
            LinearArticle(
                elements = listOf(text("Amazon Launches  Early\nPrime Day"), text("The body.")),
            )

        assertEquals(
            listOf(text("The body.")),
            article.withoutRepeatedTitle("amazon launches early prime day").elements,
        )
    }

    @Test
    fun withoutRepeatedTitleKeepsTextThatOnlyStartsLikeTheTitle() {
        val article = LinearArticle(elements = listOf(text("Prime Day, and what it costs"), text("The body.")))

        assertEquals(article.elements, article.withoutRepeatedTitle("Prime Day").elements)
    }

    @Test
    fun withoutRepeatedTitleOnlyLooksAtTheFirstText() {
        val article = LinearArticle(elements = listOf(text("Intro"), text("Prime Day")))

        assertEquals(article.elements, article.withoutRepeatedTitle("Prime Day").elements)
        assertEquals(article.elements, article.withoutRepeatedTitle(null).elements)
        assertEquals(article.elements, article.withoutRepeatedTitle("  ").elements)
    }
}
