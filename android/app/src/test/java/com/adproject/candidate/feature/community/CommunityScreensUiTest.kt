package com.adproject.candidate.feature.community

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h800dp")
class CommunityScreensUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun createPostUsesSingleSelectCategoryDropdown() {
        var selected: CommunityCategory? = null
        composeRule.setContent {
            CommunityCreatePostScreen(
                state = CommunityUiState(),
                onBack = {},
                onDraft = {},
                onCategory = { selected = it },
                onImages = {},
                onPublish = {},
            )
        }

        composeRule.onNodeWithText("General").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Tech discussion").assertIsDisplayed().performClick()
        assertEquals(CommunityCategory.TECH_DISCUSSION, selected)
    }

    @Test
    fun detailGroupsMessageAndLikeActionsOnNarrowScreen() {
        var messages = 0
        var likes = 0
        composeRule.setContent {
            CommunityDetailScreen(
                state = CommunityDetailUiState(post = post(), loading = false),
                onBack = {},
                onRetry = {},
                onToggleLike = { likes++ },
                onComment = {},
                onPublishComment = {},
                onLoadMore = {},
                onRetryComments = {},
                onMessageAuthor = { messages++ },
            )
        }

        composeRule.onNodeWithText("Message author").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Like (2)").assertIsDisplayed().performClick()
        assertEquals(1, messages)
        assertEquals(1, likes)
    }

    private fun post() = CommunityPost(
        id = "post-1",
        author = CommunityAuthor("user-1", "Alex", null, "CANDIDATE", null),
        body = "Looking for a role",
        likeCount = 2,
        commentCount = 0,
        likedByCurrentUser = false,
        createdAt = "2026-08-18T10:30:00Z",
        updatedAt = "2026-08-18T10:30:00Z",
    )
}
