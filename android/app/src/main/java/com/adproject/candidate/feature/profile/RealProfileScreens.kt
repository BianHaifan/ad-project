package com.adproject.candidate.feature.profile

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.adproject.candidate.BuildConfig
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBottomBar
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdChip
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdTealSoft
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.AdTopBar
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.core.designsystem.PrimaryButton
import com.adproject.candidate.core.designsystem.SecondaryButton
import com.adproject.candidate.data.contract.ApplicationCounts
import com.adproject.candidate.data.contract.CandidateProfileDto
import com.adproject.candidate.data.contract.Experience
import com.adproject.candidate.data.contract.Gender
import com.adproject.candidate.data.contract.Resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

// Profile and Resume each open a dedicated edit screen
// (no inline forms here), My applications shows the three grouping totals, and Job preferences
// and Sign out are unchanged.
@Composable
fun RealProfileScreen(
    state: ProfileUiState,
    resumeState: ResumeUiState,
    counts: ApplicationCounts,
    onRetry: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenApplications: () -> Unit,
    onOpenResume: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenAgent: () -> Unit,
    onLogout: () -> Unit,
    onTab: (MainTab) -> Unit,
) {
    Scaffold(
        bottomBar = { AdBottomBar(MainTab.Me, onTab) },
        containerColor = AdBackground,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AdTeal)
                }
                state.data == null -> ProfileError(state.message, onRetry)
                else -> MeContent(
                    state = state,
                    resumeState = resumeState,
                    counts = counts,
                    onOpenProfile = onOpenProfile,
                    onOpenApplications = onOpenApplications,
                    onOpenResume = onOpenResume,
                    onOpenPreferences = onOpenPreferences,
                    onOpenSavedJobs = onOpenSavedJobs,
                    onOpenAgent = onOpenAgent,
                    onLogout = onLogout,
                )
            }
        }
    }
}

@Composable
private fun ProfileError(message: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AdTopBar("Me")
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(message ?: "Unable to load profile", color = AdMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Try again", onRetry)
        }
    }
}

@Composable
private fun MeContent(
    state: ProfileUiState,
    resumeState: ResumeUiState,
    counts: ApplicationCounts,
    onOpenProfile: () -> Unit,
    onOpenApplications: () -> Unit,
    onOpenResume: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenSavedJobs: () -> Unit,
    onOpenAgent: () -> Unit,
    onLogout: () -> Unit,
) {
    val data = state.data ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Me", color = AdText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        ProfileEntryCard(data, state.avatar.revision, onOpenProfile)
        ApplicationsEntryCard(counts, onOpenApplications)
        ResumeEntryCard(resumeState, onOpenResume)
        AdCard(Modifier.fillMaxWidth()) {
            ActionRow("Saved jobs", "Jobs you've saved for later", onOpenSavedJobs)
        }
        AdCard(Modifier.fillMaxWidth()) {
            ActionRow("Job preferences", "Tune titles, locations and salary", onOpenPreferences)
        }
        AdCard(Modifier.fillMaxWidth()) {
            ActionRow("AI Agent", "Review and confirm account changes", onOpenAgent)
        }
        SignOutCard(onLogout)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ProfileEntryCard(data: CandidateProfileDto, avatarRevision: Long, onOpenProfile: () -> Unit) {
    val resolvedAvatarUrl = remember(data.avatarUrl, avatarRevision) {
        resolveAvatarUrl(data.avatarUrl, avatarRevision, BuildConfig.API_BASE_URL)
    }
    AdCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenProfile)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CandidateAvatar(data.fullName, resolvedAvatarUrl, preview = null, onClick = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(data.fullName, color = AdText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (data.avatarUrl.isNullOrBlank()) "Add photo" else data.headline.ifBlank { "Tap to edit" },
                    color = AdMuted, fontSize = 12.sp,
                )
            }
            Text("›", color = AdMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun ApplicationsEntryCard(counts: ApplicationCounts, onOpenApplications: () -> Unit) {
    AdCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenApplications)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("My applications", color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Track applications and interviews", color = AdMuted, fontSize = 11.sp)
                }
                Text("›", color = AdMuted, fontSize = 20.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApplicationGroupTile("In progress", counts.active, Modifier.weight(1f))
                ApplicationGroupTile("Interview", counts.interview, Modifier.weight(1f))
                ApplicationGroupTile("Archived", counts.archived, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ApplicationGroupTile(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(AdChip).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(count.toString(), color = AdTealDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AdMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResumeEntryCard(resumeState: ResumeUiState, onOpenResume: () -> Unit) {
    AdCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenResume)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Resume", color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(resumeStatus(resumeState), color = AdMuted, fontSize = 11.sp)
            }
            Text("›", color = AdMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AdText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AdMuted, fontSize = 11.sp)
        }
        Text("›", color = AdMuted, fontSize = 20.sp)
    }
}

@Composable
private fun SignOutCard(onLogout: () -> Unit) {
    AdCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onLogout)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sign out", color = Color(0xFFD64545), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun RealProfileEditScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (String, Gender?, Int?, String, String, String, String) -> Unit,
    onSelectAvatar: (PendingAvatar?) -> Unit,
    onUploadAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onCancelAvatar: () -> Unit,
    onAvatarTooLarge: () -> Unit,
) {
    val data = state.data
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Edit profile", onBack = onBack)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdTeal)
            }
            data == null -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message ?: "Unable to load profile", color = AdMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", onRetry)
            }
            else -> ProfileEditForm(
                data = data, state = state, onBack = onBack, onSave = onSave,
                onSelectAvatar = onSelectAvatar, onUploadAvatar = onUploadAvatar,
                onDeleteAvatar = onDeleteAvatar, onCancelAvatar = onCancelAvatar,
                onAvatarTooLarge = onAvatarTooLarge,
            )
        }
    }
}

@Composable
private fun ProfileEditForm(
    data: CandidateProfileDto,
    state: ProfileUiState,
    onBack: () -> Unit,
    onSave: (String, Gender?, Int?, String, String, String, String) -> Unit,
    onSelectAvatar: (PendingAvatar?) -> Unit,
    onUploadAvatar: () -> Unit,
    onDeleteAvatar: () -> Unit,
    onCancelAvatar: () -> Unit,
    onAvatarTooLarge: () -> Unit,
) {
    var name by remember(data) { mutableStateOf(data.fullName) }
    var gender by remember(data) { mutableStateOf(data.gender) }
    var age by remember(data) { mutableStateOf(data.age) }
    var location by remember(data) { mutableStateOf(data.location) }
    var headline by remember(data) { mutableStateOf(data.headline) }
    var phone by remember(data) { mutableStateOf(data.phone.orEmpty()) }
    var birthplace by remember(data) { mutableStateOf(data.birthplace.orEmpty()) }
    var showGender by remember { mutableStateOf(false) }
    var showAge by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }

    val pickAvatar = rememberAvatarPicker(onSelectAvatar, onAvatarTooLarge)
    val resolvedAvatarUrl = remember(data.avatarUrl, state.avatar.revision) {
        resolveAvatarUrl(data.avatarUrl, state.avatar.revision, BuildConfig.API_BASE_URL)
    }
    val previewBitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, state.avatar.pending) {
        val bytes = state.avatar.pending?.bytes
        value = if (bytes == null) null else withContext(Dispatchers.Default) {
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CandidateAvatar(data.fullName, resolvedAvatarUrl, previewBitmap, onClick = pickAvatar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(data.fullName, color = AdText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("PNG or JPEG, up to 5 MB", color = AdMuted, fontSize = 12.sp)
            }
        }
        AvatarActions(
            hasAvatar = !data.avatarUrl.isNullOrBlank(),
            avatar = state.avatar,
            onPick = pickAvatar,
            onUpload = onUploadAvatar,
            onDelete = onDeleteAvatar,
            onCancel = onCancelAvatar,
        )
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Full name") }, singleLine = true, isError = "fullName" in state.fieldErrors,
            supportingText = { state.fieldErrors["fullName"]?.let { Text(it) } })
        SelectorField(
            label = "Gender",
            value = gender?.let(::genderLabel),
            placeholder = "Not specified",
            onClick = { showGender = true },
        )
        SelectorField(
            label = "Age",
            value = age?.toString(),
            placeholder = "Not specified",
            isError = "age" in state.fieldErrors,
            errorText = state.fieldErrors["age"],
            onClick = { showAge = true },
        )
        SelectorField(
            label = "Location",
            value = location.ifBlank { null },
            placeholder = "Select a location",
            isError = "location" in state.fieldErrors,
            errorText = state.fieldErrors["location"],
            onClick = { showLocation = true },
        )
        OutlinedTextField(headline, { headline = it }, Modifier.fillMaxWidth(),
            label = { Text("Headline") }, singleLine = true, isError = "headline" in state.fieldErrors,
            supportingText = { state.fieldErrors["headline"]?.let { Text(it) } })
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(),
            label = { Text("Phone") }, singleLine = true, isError = "phone" in state.fieldErrors,
            supportingText = { state.fieldErrors["phone"]?.let { Text(it) } })
        OutlinedTextField(birthplace, { birthplace = it }, Modifier.fillMaxWidth(),
            label = { Text("Birthplace") }, singleLine = true, isError = "birthplace" in state.fieldErrors,
            supportingText = { state.fieldErrors["birthplace"]?.let { Text(it) } })
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(if (state.submitting) "Saving…" else "Save",
                { onSave(name, gender, age, location, headline, phone, birthplace) }, Modifier.weight(1f), enabled = !state.submitting)
            SecondaryButton("Cancel", onBack, Modifier.weight(1f))
        }
    }

    if (showGender) {
        SingleSelectSheet(
            title = "Gender",
            options = Gender.entries,
            optionLabel = ::genderLabel,
            initialSelected = gender,
            clearLabel = "Not specified",
            onConfirm = { gender = it; showGender = false },
            onDismiss = { showGender = false },
        )
    }
    if (showAge) {
        NumberWheelSheet(
            title = "Age",
            values = AGE_OPTIONS,
            labelOf = { it.toString() },
            initialValue = age,
            clearLabel = "Not specified",
            onConfirm = { age = it; showAge = false },
            onDismiss = { showAge = false },
        )
    }
    if (showLocation) {
        LocationSelectSheet(
            title = "Location",
            options = COMMON_LOCATIONS,
            initialSelected = location.ifBlank { null },
            onConfirm = { location = it.orEmpty(); showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
}

private fun genderLabel(gender: Gender): String = when (gender) {
    Gender.MALE -> "Male"
    Gender.FEMALE -> "Female"
    Gender.OTHER -> "Other"
    Gender.PREFER_NOT_TO_SAY -> "Prefer not to say"
}

@Composable
private fun rememberAvatarPicker(onSelect: (PendingAvatar?) -> Unit, onTooLarge: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) { onSelect(null); return@rememberLauncherForActivityResult }
        // Read the selected image off the main thread, capped at 5 MiB + 1 byte, so a large file
        // never janks the UI and is never fully buffered in memory.
        scope.launch(Dispatchers.IO) {
            var tooLarge = false
            val pending = runCatching {
                val resolver = appContext.contentResolver
                val type = resolver.getType(uri) ?: return@runCatching null
                val stream = resolver.openInputStream(uri) ?: return@runCatching null
                val read = stream.use { readAvatarBytes(it, MAX_AVATAR_BYTES) }
                when (read) {
                    is AvatarReadResult.TooLarge -> { tooLarge = true; null }
                    is AvatarReadResult.Ok -> PendingAvatar(
                        fileName = if (type == "image/png") "avatar.png" else "avatar.jpg",
                        contentType = type, bytes = read.bytes,
                    )
                }
            }.getOrNull()
            if (tooLarge) onTooLarge() else onSelect(pending)
        }
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

@Composable
private fun AvatarActions(
    hasAvatar: Boolean,
    avatar: AvatarUiState,
    onPick: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        avatar.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        if (avatar.pending != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    if (avatar.uploading) "Uploading…" else "Upload photo",
                    onUpload, Modifier.weight(1f), enabled = !avatar.uploading,
                )
                SecondaryButton("Cancel", onCancel, Modifier.weight(1f), enabled = !avatar.deleting)
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryButton(
                    if (hasAvatar) "Change photo" else "Add photo",
                    onPick, Modifier.weight(1f), enabled = !avatar.uploading && !avatar.deleting,
                )
                if (hasAvatar) {
                    Text(
                        if (avatar.deleting) "Removing…" else "Remove photo",
                        color = Color(0xFFD64545), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !avatar.deleting, onClick = onDelete)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateAvatar(name: String, avatarUrl: String?, preview: ImageBitmap?, onClick: (() -> Unit)?, size: Int = 64) {
    val modifier = Modifier.size(size.dp).clip(CircleShape).clickable(enabled = onClick != null) { onClick?.invoke() }
    when {
        preview != null -> Image(preview, "$name's avatar", modifier, contentScale = ContentScale.Crop)
        !avatarUrl.isNullOrBlank() -> AvatarImage(avatarUrl, name, modifier)
        else -> Box(modifier.background(AdTealSoft), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = AdTeal, fontSize = (size * 0.38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AvatarImage(url: String, name: String, modifier: Modifier) {
    val platformContext = LocalPlatformContext.current
    AsyncImage(
        model = ImageRequest.Builder(platformContext)
            .data(url)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build(),
        contentDescription = "$name's avatar",
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun RealResumeEditScreen(
    state: ResumeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (String, List<String>, List<Experience>) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AdBackground)) {
        AdTopBar("Resume", onBack = onBack)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AdTeal)
            }
            state.notCreated || state.data != null -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (state.notCreated) {
                    Text("Create your resume", color = AdText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add your summary, skills and experience so recruiters can see your profile when you apply.",
                        color = AdMuted, fontSize = 12.sp, lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                }
                ResumeEditForm(state, onBack, onSave)
            }
            else -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message ?: "Couldn't load your resume.", color = AdMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Try again", onRetry)
            }
        }
    }
}

@Composable
private fun ResumeEditForm(
    state: ResumeUiState,
    onCancelEdit: () -> Unit,
    onSave: (String, List<String>, List<Experience>) -> Unit,
) {
    val data = state.data
    var summary by remember(data) { mutableStateOf(data?.summary.orEmpty()) }
    val skills = remember(data) { mutableStateListOf<String>().apply { addAll(data?.skills.orEmpty()) } }
    var showSkills by remember { mutableStateOf(false) }
    val experiences = remember(data) { mutableStateListOf<Experience>().apply { addAll(data?.experiences.orEmpty()) } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(summary, { summary = it }, Modifier.fillMaxWidth(),
            label = { Text("Summary") }, minLines = 3, isError = "summary" in state.fieldErrors,
            supportingText = { state.fieldErrors["summary"]?.let { Text(it) } })
        SelectorField(
            label = "Skills",
            value = if (skills.isEmpty()) null else "${skills.size} selected",
            placeholder = "Select skills",
            isError = "skills" in state.fieldErrors,
            errorText = state.fieldErrors["skills"],
            onClick = { showSkills = true },
        )

        SectionLabel("Experience")
        experiences.forEachIndexed { index, experience ->
            ExperienceEditCard(
                index = index,
                experience = experience,
                fieldErrors = state.fieldErrors,
                onUpdate = { updated -> experiences[index] = updated },
                onRemove = { experiences.removeAt(index) },
            )
        }
        TextButton(onClick = { experiences.add(Experience(null, "", "", "", "2026-01", null)) }) { Text("+ Add experience") }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(if (state.submitting) "Saving…" else "Save resume",
                { onSave(summary, skills.toList(), experiences.toList()) },
                Modifier.weight(1f), enabled = !state.submitting)
            SecondaryButton("Cancel", onCancelEdit, Modifier.weight(1f))
        }
    }

    if (showSkills) {
        SearchableMultiSelectSheet(
            title = "Skills",
            options = COMMON_SKILLS,
            initialSelected = skills.toList(),
            searchPlaceholder = "Search skills",
            addPlaceholder = "Add a skill",
            onConfirm = { result -> skills.clear(); skills.addAll(result); showSkills = false },
            onDismiss = { showSkills = false },
        )
    }
}

@Composable
private fun ExperienceEditCard(
    index: Int,
    experience: Experience,
    fieldErrors: Map<String, String>,
    onUpdate: (Experience) -> Unit,
    onRemove: () -> Unit,
) {
    val prefix = "experiences[$index]"
    AdCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Experience ${index + 1}", color = AdText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(experience.title, { onUpdate(experience.copy(title = it)) }, Modifier.fillMaxWidth(),
                label = { Text("Title") }, singleLine = true, isError = "$prefix.title" in fieldErrors,
                supportingText = { fieldErrors["$prefix.title"]?.let { Text(it) } })
            OutlinedTextField(experience.company, { onUpdate(experience.copy(company = it)) }, Modifier.fillMaxWidth(),
                label = { Text("Company") }, singleLine = true, isError = "$prefix.company" in fieldErrors,
                supportingText = { fieldErrors["$prefix.company"]?.let { Text(it) } })
            OutlinedTextField(experience.description, { onUpdate(experience.copy(description = it)) }, Modifier.fillMaxWidth(),
                label = { Text("Description") }, minLines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MonthPickerField(
                    value = experience.startDate,
                    label = "Start",
                    emptyText = "",
                    isError = "$prefix.startDate" in fieldErrors,
                    errorText = fieldErrors["$prefix.startDate"],
                    onPick = { onUpdate(experience.copy(startDate = it)) },
                    modifier = Modifier.weight(1f),
                )
                MonthPickerField(
                    value = experience.endDate,
                    label = "End",
                    emptyText = "Present",
                    isError = "$prefix.endDate" in fieldErrors,
                    errorText = fieldErrors["$prefix.endDate"],
                    onPick = { onUpdate(experience.copy(endDate = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
            FilterChip(
                selected = experience.endDate == null,
                onClick = { onUpdate(experience.copy(endDate = null)) },
                label = { Text("Present") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AdTealSoft,
                    selectedLabelColor = AdTealDark,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRemove) { Text("Remove", color = Color(0xFFD64545)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerField(
    value: String?,
    label: String,
    emptyText: String,
    isError: Boolean,
    errorText: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value ?: emptyText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            supportingText = { errorText?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay captures taps so the read-only field opens the picker.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value?.let(::yearMonthToMillis))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(millisToYearMonth(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun yearMonthToMillis(yearMonth: String): Long? =
    runCatching { YearMonth.parse(yearMonth).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        .getOrNull()

private fun millisToYearMonth(millis: Long): String =
    YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)).toString()

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = AdText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

private fun resumeStatus(resume: ResumeUiState): String = when {
    resume.loading -> "Loading…"
    resume.notCreated -> "No resume yet"
    resume.data == null -> "Unavailable"
    else -> {
        val missing = buildList {
            if (resume.data.summary.isBlank()) add("summary")
            if (resume.data.skills.isEmpty()) add("skills")
            if (resume.data.experiences.isEmpty()) add("experience")
        }
        if (missing.isEmpty()) "Complete · ready to apply" else "Add ${missing.joinToString(", ")}"
    }
}
