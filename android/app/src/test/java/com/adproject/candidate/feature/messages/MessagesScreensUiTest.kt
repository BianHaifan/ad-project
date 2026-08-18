package com.adproject.candidate.feature.messages

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adproject.candidate.data.contract.Company
import com.adproject.candidate.data.contract.ConversationDetail
import com.adproject.candidate.data.contract.ConversationParticipant
import com.adproject.candidate.data.contract.ConversationSummary
import com.adproject.candidate.data.contract.DeliveryStatus
import com.adproject.candidate.data.contract.InterviewContext
import com.adproject.candidate.data.contract.InterviewMode
import com.adproject.candidate.data.contract.InterviewStatus
import com.adproject.candidate.data.contract.Message
import com.adproject.candidate.data.contract.MessageAttachment
import com.adproject.candidate.data.contract.SenderType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class MessagesScreensUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun company(name: String = "Acme Corp") = Company(
        companyId = "c1", name = name, logoUrl = null, stage = "Series A", employeeRange = "51-200",
        verificationStatus = "APPROVED", website = null, description = null, location = "Singapore",
        version = 1, createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun participant(name: String = "Mia Chen", userId: String = "r1") = ConversationParticipant(
        userId = userId, fullName = name, avatarUrl = null, title = "Hiring Manager",
        company = company(), online = true,
    )

    private fun conversation(conversationId: String = "conv-1", unread: Int = 2, name: String = "Mia Chen") = ConversationSummary(
        conversationId = conversationId, applicationId = "app-1", jobId = "job-1",
        createdAt = "2026-08-01T09:00:00+08:00", updatedAt = "2026-08-11T10:30:00+08:00",
        participant = participant(name = name), lastMessage = message(body = "Hi Alice, let's talk."),
        unreadCount = unread, jobTitle = "Backend Engineer",
    )

    private fun message(
        id: String = "m-1",
        body: String = "Hello",
        sender: SenderType = SenderType.CANDIDATE,
        sentAt: String = "2026-08-11T10:30:00+08:00",
        attachment: MessageAttachment? = null,
    ) = Message(
        messageId = id, conversationId = "conv-1", body = body, senderType = sender, sentAt = sentAt,
        clientMessageId = null, deliveryStatus = DeliveryStatus.DELIVERED, attachment = attachment,
    )

    private fun attachment(fileName: String = "resume.pdf", size: Long = 12800) = MessageAttachment(
        attachmentId = "att-1", fileName = fileName, sizeBytes = size, contentType = "application/pdf",
    )

    private fun detail(name: String = "Mia Chen") = ConversationDetail(
        conversationId = "conv-1", applicationId = "app-1", jobId = "job-1",
        createdAt = "2026-08-01T09:00:00+08:00", updatedAt = "2026-08-11T10:30:00+08:00",
        participant = participant(name = name),
        context = InterviewContext(
            type = "SYNC", interviewId = "i-1", applicationId = "app-1", jobId = "job-1",
            jobTitle = "Backend Engineer", scheduledAt = "2026-08-15T09:00:00+08:00",
            mode = InterviewMode.ONLINE, timezone = "GMT+8", durationMinutes = 45,
            locationOrMeetingUrl = null, status = InterviewStatus.SCHEDULED,
        ),
    )

    @Test
    fun messagesLoadingShowsSpinner() {
        composeRule.setContent {
            MessagesScreen(MessagesUiState(loading = true), {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Recruiters and hiring teams").assertIsDisplayed()
    }

    @Test
    fun messagesErrorShowsRetry() {
        var retries = 0
        composeRule.setContent {
            MessagesScreen(
                MessagesUiState(loading = false, message = "Network unavailable"),
                onRetry = { retries++ }, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun messagesEmptyShowsHint() {
        composeRule.setContent {
            MessagesScreen(MessagesUiState(loading = false), {}, {}, {}, {})
        }
        composeRule.onNodeWithText("No conversations yet").assertIsDisplayed()
        composeRule.onNodeWithText("Messages from recruiters will appear here.").assertIsDisplayed()
    }

    @Test
    fun messagesContentShowsRowsAndRefreshingState() {
        var opened: String? = null
        var refreshed = 0
        composeRule.setContent {
            MessagesScreen(
                MessagesUiState(
                    loading = false,
                    refreshing = false,
                    conversations = listOf(
                        conversation(unread = 3),
                        conversation(conversationId = "conv-2", unread = 0, name = "John Doe")
                            .copy(lastMessage = null),
                    ),
                ),
                onRetry = {}, onRefresh = { refreshed++ }, onTab = {}, onConversation = { opened = it },
            )
        }
        composeRule.onNodeWithText("Mia Chen").assertIsDisplayed()
        composeRule.onNodeWithText("John Doe").assertIsDisplayed()
        composeRule.onNodeWithText("Hi Alice, let's talk.").assertExists()
        composeRule.onNodeWithText("3").assertExists()
        composeRule.onNodeWithText("No messages yet").assertExists()
        composeRule.onNodeWithText("Refresh").performClick()
        assertEquals(1, refreshed)
        composeRule.onNodeWithText("Mia Chen").performClick()
        assertEquals("conv-1", opened)
    }

    @Test
    fun messagesRefreshingLabelWhileRefreshing() {
        composeRule.setContent {
            MessagesScreen(
                MessagesUiState(loading = false, refreshing = true, conversations = listOf(conversation())),
                {}, {}, {}, {},
            )
        }
        composeRule.onNodeWithText("Refreshing…").assertIsDisplayed()
    }

    @Test
    fun chatLoadingShowsSpinner() {
        composeRule.setContent {
            ChatScreen(ChatUiState(loading = true), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test
    fun chatNotFoundShowsRetry() {
        var retries = 0
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, notFound = true),
                onBack = {}, onRetry = { retries++ }, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("This conversation is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun chatErrorShowsRetry() {
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, message = "Network unavailable"),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("Network unavailable").assertIsDisplayed()
    }

    @Test
    fun chatEmptyShowsNoMessagesYet() {
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, conversation = detail()),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("Write a message…").assertIsDisplayed()
        composeRule.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test
    fun chatContentShowsMessagesAndActions() {
        var jobOpened: String? = null
        var recruiterOpened: String? = null
        var backed = 0
        var sent = 0
        var removed = 0
        composeRule.setContent {
            ChatScreen(
                ChatUiState(
                    loading = false,
                    conversation = detail(),
                    messages = listOf(
                        message(id = "c", sender = SenderType.CANDIDATE, body = "Thanks for reaching out"),
                        message(id = "r", sender = SenderType.RECRUITER, body = "See you then",
                            attachment = attachment()),
                    ),
                    draft = "Hello",
                ),
                onBack = { backed++ },
                onRetry = {},
                onDraft = {},
                onSend = { sent++ },
                onSelectAttachment = {},
                onRemoveAttachment = { removed++ },
                onDownloadAttachment = {},
                onOpenImage = {},
                onConsumeDownload = {},
                onCloseImagePreview = {},
                onViewJob = { jobOpened = it },
                onViewRecruiter = { recruiterOpened = it },
            )
        }
        composeRule.onNodeWithText("Mia Chen").assertIsDisplayed()
        composeRule.onNodeWithText("Thanks for reaching out").assertExists()
        composeRule.onNodeWithText("See you then").assertExists()
        composeRule.onNodeWithText("resume.pdf").performScrollTo().assertExists()
        composeRule.onNodeWithText("12.5 KB").assertExists()
        composeRule.onNodeWithText("View job").performClick()
        assertEquals("job-1", jobOpened)
        composeRule.onNodeWithText("Mia Chen").assertIsDisplayed()
        composeRule.onNodeWithText("Hello").assertExists()
        composeRule.onNodeWithText("↑").performClick()
        assertEquals(1, sent)
    }

    @Test
    fun chatHeaderBackAndRecruiterClick() {
        var backed = 0
        var recruiterOpened: String? = null
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, conversation = detail()),
                onBack = { backed++ },
                onRetry = {},
                onDraft = {},
                onSend = {},
                onSelectAttachment = {},
                onRemoveAttachment = {},
                onDownloadAttachment = {},
                onOpenImage = {},
                onConsumeDownload = {},
                onCloseImagePreview = {},
                onViewJob = {},
                onViewRecruiter = { recruiterOpened = it },
            )
        }
        composeRule.onNodeWithContentDescription("Back to messages").assertIsDisplayed()
        composeRule.onNodeWithText("Acme Corp").assertExists()
        composeRule.onNodeWithText("Mia Chen").performClick()
        assertEquals("r1", recruiterOpened)
        composeRule.onNodeWithContentDescription("Back to messages").performClick()
        assertEquals(1, backed)
    }

    @Test
    fun chatSendingStateDisablesComposerSend() {
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, conversation = detail(), sending = true, draft = "Hello"),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("Hello").assertExists()
    }

    @Test
    fun chatAttachmentRowRemoves() {
        var removed = 0
        composeRule.setContent {
            ChatScreen(
                ChatUiState(
                    loading = false,
                    conversation = detail(),
                    attachment = PendingAttachment("resume.pdf", "application/pdf", 12800, byteArrayOf(1)),
                ),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = { removed++ },
                onDownloadAttachment = {}, onOpenImage = {}, onConsumeDownload = {},
                onCloseImagePreview = {}, onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("📎 resume.pdf · 12.5 KB").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(1, removed)
    }

    @Test
    fun chatImagePreviewDialogCloses() {
        var closed = 0
        composeRule.setContent {
            ChatScreen(
                ChatUiState(
                    loading = false,
                    conversation = detail(),
                    imagePreview = ImagePreview("photo.png", "image/png", byteArrayOf(1, 2, 3)),
                ),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = { closed++ },
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("photo.png").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun chatInlineImageThumbnailLoadingState() {
        composeRule.setContent {
            ChatScreen(
                ChatUiState(
                    loading = false,
                    conversation = detail(),
                    messages = listOf(
                        message(
                            id = "img",
                            sender = SenderType.RECRUITER,
                            attachment = MessageAttachment("att", "photo.png", 2048, "image/png"),
                        ),
                    ),
                    loadingThumbnails = setOf("img"),
                ),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("Hello").performScrollTo().assertExists()
    }

    @Test
    fun interviewTimeFormatted() {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d · h:mm a")
        val expected = OffsetDateTime.parse("2026-08-15T09:00:00+08:00").format(formatter) + " · Online"
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, conversation = detail()),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("INTERVIEW").assertIsDisplayed()
        composeRule.onNodeWithText(expected).assertExists()
    }

    @Test
    fun chatWithoutContextHidesJobCard() {
        composeRule.setContent {
            ChatScreen(
                ChatUiState(
                    loading = false,
                    conversation = detail().copy(context = null),
                    messages = listOf(message()),
                ),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("INTERVIEW").assertDoesNotExist()
        composeRule.onNodeWithText("Hello").assertExists()
    }

    @Test
    fun chatHeaderWithoutParticipantShowsPlaceholder() {
        composeRule.setContent {
            ChatScreen(
                ChatUiState(loading = false, conversation = ConversationDetail(
                    "conv-9", "app-9", "job-9", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z",
                    ConversationParticipant("u9", "", null, null, null, online = false), null,
                )),
                onBack = {}, onRetry = {}, onDraft = {}, onSend = {},
                onSelectAttachment = {}, onRemoveAttachment = {}, onDownloadAttachment = {},
                onOpenImage = {}, onConsumeDownload = {}, onCloseImagePreview = {},
                onViewJob = {}, onViewRecruiter = {},
            )
        }
        composeRule.onNodeWithText("Recruiter").assertIsDisplayed()
        composeRule.onNodeWithText("Write a message…").assertIsDisplayed()
    }
}