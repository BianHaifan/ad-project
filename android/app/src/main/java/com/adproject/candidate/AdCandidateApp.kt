package com.adproject.candidate

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.feature.applications.RealApplicationSubmittedScreen
import com.adproject.candidate.feature.applications.RealApplyConfirmationScreen
import com.adproject.candidate.feature.applications.ApplicationViewModel
import com.adproject.candidate.feature.applications.ApplicationListViewModel
import com.adproject.candidate.feature.applications.ApplicationDetailViewModel
import com.adproject.candidate.feature.applications.RealMyApplicationsScreen
import com.adproject.candidate.feature.applications.RealApplicationDetailScreen
import com.adproject.candidate.feature.auth.CreateAccountScreen
import com.adproject.candidate.feature.auth.SignInScreen
import com.adproject.candidate.feature.auth.AuthViewModel
import com.adproject.candidate.feature.jobs.JobDetailScreen
import com.adproject.candidate.feature.jobs.JobFeedScreen
import com.adproject.candidate.feature.jobs.LearningScreen
import com.adproject.candidate.feature.jobs.JobFeedViewModel
import com.adproject.candidate.feature.jobs.JobDetailViewModel
import com.adproject.candidate.feature.messages.MessagesScreen
import com.adproject.candidate.feature.messages.ChatScreen
import com.adproject.candidate.feature.messages.MessagesViewModel
import com.adproject.candidate.feature.messages.ChatViewModel
import com.adproject.candidate.core.network.CandidateAppContainer
import com.adproject.candidate.feature.profile.RealProfileScreen
import com.adproject.candidate.feature.profile.RealResumeScreen
import com.adproject.candidate.feature.profile.CandidateProfileViewModel
import com.adproject.candidate.feature.profile.CandidateResumeViewModel
import com.adproject.candidate.feature.profile.JobPreferenceViewModel
import com.adproject.candidate.feature.profile.JobPreferencesScreen
import com.adproject.candidate.feature.profile.RecruiterPublicProfileScreen
import com.adproject.candidate.feature.profile.CompanyPublicProfileScreen
import com.adproject.candidate.feature.profile.RecruiterPublicProfileViewModel
import com.adproject.candidate.feature.profile.CompanyPublicProfileViewModel
import com.adproject.candidate.data.api.CandidateRepository
import com.adproject.candidate.data.api.FakeCandidateRepository

private object Route {
    const val SignIn = "sign-in"
    const val CreateAccount = "create-account"
    const val Jobs = "jobs"
    const val Learning = "learning"
    const val Messages = "messages"
    const val ChatDetail = "chat-detail/{conversationId}"
    const val Profile = "profile"
    const val Applications = "applications"
    const val ApplicationDetail = "application-detail/{applicationId}"
    const val JobDetail = "job-detail/{jobId}"
    const val Apply = "apply/{jobId}"
    const val Submitted = "submitted/{jobId}"
    const val ResumeEdit = "resume-edit"
    const val JobPreferences = "job-preferences"
    const val RecruiterProfile = "recruiter/{recruiterId}"
    const val CompanyProfile = "company/{companyId}"

    fun jobDetail(id: String) = "job-detail/$id"
    fun chatDetail(id: String) = "chat-detail/$id"
    fun apply(id: String) = "apply/$id"
    fun submitted(id: String) = "submitted/$id"
    fun applicationDetail(id: String) = "application-detail/$id"
    fun recruiterProfile(id: String) = "recruiter/$id"
    fun companyProfile(id: String) = "company/$id"
}

/**
 * Bridges the Compose lifecycle (foreground + route visibility) into a ViewModel's
 * start/stop callbacks without firing any network request from the Composable layer.
 *
 * `LocalLifecycleOwner` inside a NavHost destination is that destination's
 * `NavBackStackEntry`, whose lifecycle is capped by the host activity. ON_START/ON_STOP
 * therefore fire when the app backgrounds or the destination is left, and again when the
 * destination becomes visible or the app returns to the foreground.
 */
@Composable
private fun ScreenLifecyclePolling(onStarted: () -> Unit, onStopped: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStarted()
                Lifecycle.Event.ON_STOP -> onStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStarted()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            onStopped()
        }
    }
}

@Composable
fun AdCandidateApp(
    fakeCandidateFeatures: CandidateRepository = FakeCandidateRepository,
    providedContainer: CandidateAppContainer? = null,
) {
    val context = LocalContext.current
    val container = remember(providedContainer) { providedContainer ?: CandidateAppContainer(context) }
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(container.authRepository))
    val applicationViewModel: ApplicationViewModel = viewModel(factory = ApplicationViewModel.factory(
        container.candidateJobRepository, container.candidateProfileRepository,
        container.candidateResumeRepository, container.candidateApplicationRepository,
    ))
    val sessionActive by container.sessionManager.sessionActive.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { container.sessionManager.load() }

    if (sessionActive == null) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {}
        return
    }

    fun navigateBack(fallbackRoute: String) {
        if (!navController.navigateUp()) {
            navController.navigate(fallbackRoute) { launchSingleTop = true }
        }
    }

    fun openTab(tab: MainTab) {
        val route = when (tab) {
            MainTab.Jobs -> Route.Jobs
            MainTab.Learn -> Route.Learning
            MainTab.Messages -> Route.Messages
            MainTab.Me -> Route.Profile
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Color.White) {
        LaunchedEffect(sessionActive) {
            if (sessionActive == true) {
                navController.navigate(Route.Jobs) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Route.SignIn) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        NavHost(navController = navController, startDestination = if (sessionActive == true) Route.Jobs else Route.SignIn) {
            composable(Route.SignIn) {
                val state by authViewModel.signIn.collectAsStateWithLifecycle()
                SignInScreen(
                    state = state,
                    onEmail = authViewModel::updateSignInEmail,
                    onPassword = authViewModel::updateSignInPassword,
                    onSignIn = authViewModel::signIn,
                    onCreateAccount = { navController.navigate(Route.CreateAccount) },
                )
            }
            composable(Route.CreateAccount) {
                val state by authViewModel.register.collectAsStateWithLifecycle()
                CreateAccountScreen(
                    state = state,
                    onFullName = authViewModel::updateFullName,
                    onEmail = authViewModel::updateRegisterEmail,
                    onPassword = authViewModel::updateRegisterPassword,
                    onConfirmPassword = authViewModel::updateConfirmPassword,
                    onAgreed = authViewModel::updateAgreed,
                    onCreate = authViewModel::register,
                    onSignIn = { navController.popBackStack() },
                )
            }
            composable(Route.Jobs) {
                val jobsViewModel: JobFeedViewModel = viewModel(
                    factory = JobFeedViewModel.factory(container.candidateJobRepository),
                )
                val state by jobsViewModel.state.collectAsStateWithLifecycle()
                JobFeedScreen(
                    state = state,
                    onQuery = jobsViewModel::updateQuery,
                    onSearch = jobsViewModel::search,
                    onRecommended = jobsViewModel::showRecommended,
                    onEmploymentType = jobsViewModel::selectEmploymentType,
                    onRefresh = jobsViewModel::refresh,
                    onRetry = jobsViewModel::retry,
                    onTab = ::openTab,
                    onJob = { navController.navigate(Route.jobDetail(it)) },
                )
            }
            composable(Route.Learning) { LearningScreen(fakeCandidateFeatures.getLearning(), ::openTab) }
            composable(Route.Messages) {
                val messagesViewModel: MessagesViewModel = viewModel(
                    factory = MessagesViewModel.factory(container.candidateConversationRepository),
                )
                val state by messagesViewModel.state.collectAsStateWithLifecycle()
                ScreenLifecyclePolling(
                    onStarted = messagesViewModel::onScreenStarted,
                    onStopped = messagesViewModel::onScreenStopped,
                )
                MessagesScreen(
                    state = state,
                    onRetry = messagesViewModel::retry,
                    onRefresh = messagesViewModel::refresh,
                    onTab = ::openTab,
                    onConversation = { navController.navigate(Route.chatDetail(it)) },
                )
            }
            composable(Route.ChatDetail) { entry ->
                val conversationId = entry.arguments?.getString("conversationId").orEmpty()
                val chatViewModel: ChatViewModel = viewModel(
                    key = "chat-$conversationId",
                    factory = ChatViewModel.factory(conversationId, container.candidateConversationRepository),
                )
                val state by chatViewModel.state.collectAsStateWithLifecycle()
                ScreenLifecyclePolling(
                    onStarted = chatViewModel::onScreenStarted,
                    onStopped = chatViewModel::onScreenStopped,
                )
                ChatScreen(
                    state = state,
                    onBack = { navigateBack(Route.Messages) },
                    onRetry = chatViewModel::retry,
                    onDraft = chatViewModel::updateDraft,
                    onSend = chatViewModel::send,
                    onSelectAttachment = chatViewModel::selectAttachment,
                    onRemoveAttachment = chatViewModel::removeAttachment,
                    onDownloadAttachment = chatViewModel::download,
                    onConsumeDownload = chatViewModel::consumeDownload,
                    onViewJob = { navController.navigate(Route.jobDetail(it)) },
                    onViewRecruiter = { navController.navigate(Route.recruiterProfile(it)) },
                )
            }
            composable(Route.Profile) {
                val profileViewModel: CandidateProfileViewModel = viewModel(factory = CandidateProfileViewModel.factory(container.candidateProfileRepository))
                val state by profileViewModel.state.collectAsStateWithLifecycle()
                RealProfileScreen(
                    state = state,
                    onRetry = profileViewModel::load,
                    onEdit = profileViewModel::edit,
                    onSave = profileViewModel::save,
                    onResume = { navController.navigate(Route.ResumeEdit) },
                    onApplications = { navController.navigate(Route.Applications) },
                    onPreferences = { navController.navigate(Route.JobPreferences) },
                    onLogout = authViewModel::logout,
                    onTab = ::openTab,
                )
            }
            composable(Route.Applications) { entry ->
                val applicationsViewModel: ApplicationListViewModel = viewModel(
                    factory = ApplicationListViewModel.factory(container.candidateApplicationRepository),
                )
                val state by applicationsViewModel.state.collectAsStateWithLifecycle()
                val refreshKey by entry.savedStateHandle.getStateFlow("applications-refresh", 0L)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(refreshKey) {
                    if (refreshKey == 0L) applicationsViewModel.load() else applicationsViewModel.refresh()
                }
                RealMyApplicationsScreen(
                    state = state,
                    onTab = ::openTab,
                    onBack = { navigateBack(Route.Profile) },
                    onRefresh = applicationsViewModel::refresh,
                    onRetry = applicationsViewModel::retry,
                    onFilter = applicationsViewModel::selectFilter,
                    onLoadMore = applicationsViewModel::loadMore,
                    onApplication = { navController.navigate(Route.applicationDetail(it)) },
                )
            }
            composable(Route.ApplicationDetail) { entry ->
                val applicationId = entry.arguments?.getString("applicationId").orEmpty()
                val detailViewModel: ApplicationDetailViewModel = viewModel(
                    key = "application-detail-$applicationId",
                    factory = ApplicationDetailViewModel.factory(applicationId, container.candidateApplicationRepository),
                )
                val state by detailViewModel.state.collectAsStateWithLifecycle()
                RealApplicationDetailScreen(
                    state = state,
                    onBack = {
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set("applications-refresh", System.nanoTime())
                        navigateBack(Route.Applications)
                    },
                    onRetry = detailViewModel::load,
                    onRequestWithdraw = detailViewModel::requestWithdraw,
                    onDismissWithdraw = detailViewModel::dismissWithdraw,
                    onWithdrawReason = detailViewModel::updateWithdrawReason,
                    onConfirmWithdraw = detailViewModel::confirmWithdraw,
                )
            }
            composable(Route.JobDetail) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                val detailViewModel: JobDetailViewModel = viewModel(
                    key = "job-detail-$jobId",
                    factory = JobDetailViewModel.factory(jobId, container.candidateJobRepository),
                )
                val state by detailViewModel.state.collectAsStateWithLifecycle()
                JobDetailScreen(
                    state = state,
                    onBack = { navigateBack(Route.Jobs) },
                    onRetry = detailViewModel::retry,
                    onApply = { navController.navigate(Route.apply(it)) },
                    onViewCompany = { navController.navigate(Route.companyProfile(it)) },
                    onViewRecruiter = { navController.navigate(Route.recruiterProfile(it)) },
                )
            }
            composable(Route.RecruiterProfile) { entry ->
                val recruiterId = entry.arguments?.getString("recruiterId").orEmpty()
                val recruiterViewModel: RecruiterPublicProfileViewModel = viewModel(
                    key = "recruiter-$recruiterId",
                    factory = RecruiterPublicProfileViewModel.factory(recruiterId, container.candidatePublicProfileRepository),
                )
                val state by recruiterViewModel.state.collectAsStateWithLifecycle()
                RecruiterPublicProfileScreen(
                    state = state,
                    onBack = { navigateBack(Route.Jobs) },
                    onRetry = recruiterViewModel::retry,
                )
            }
            composable(Route.CompanyProfile) { entry ->
                val companyId = entry.arguments?.getString("companyId").orEmpty()
                val companyViewModel: CompanyPublicProfileViewModel = viewModel(
                    key = "company-$companyId",
                    factory = CompanyPublicProfileViewModel.factory(companyId, container.candidatePublicProfileRepository),
                )
                val state by companyViewModel.state.collectAsStateWithLifecycle()
                CompanyPublicProfileScreen(
                    state = state,
                    onBack = { navigateBack(Route.Jobs) },
                    onRetry = companyViewModel::retry,
                )
            }
            composable(Route.Apply) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                LaunchedEffect(jobId) { applicationViewModel.start(jobId) }
                val state by applicationViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.result?.applicationId) {
                    if (state.result != null) navController.navigate(Route.submitted(jobId)) { launchSingleTop = true }
                }
                RealApplyConfirmationScreen(
                    state = state,
                    onBack = { navigateBack(Route.jobDetail(jobId)) },
                    onRetry = applicationViewModel::retryLoad,
                    onCreateResume = { navController.navigate(Route.ResumeEdit) },
                    onShareProfile = applicationViewModel::setShareProfile,
                    onSubmit = applicationViewModel::submit,
                )
            }
            composable(Route.Submitted) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                val state by applicationViewModel.state.collectAsStateWithLifecycle()
                RealApplicationSubmittedScreen(
                    application = state.result,
                    onJobs = {
                        applicationViewModel.clear()
                        navController.navigate(Route.Jobs) { popUpTo(Route.Jobs) { inclusive = true } }
                    },
                    onApplications = {
                        applicationViewModel.clear()
                        navController.navigate(Route.Applications)
                    },
                )
            }
            composable(Route.ResumeEdit) {
                val resumeViewModel: CandidateResumeViewModel = viewModel(factory = CandidateResumeViewModel.factory(container.candidateResumeRepository))
                val state by resumeViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.saved) {
                    if (state.saved) {
                        if (applicationViewModel.state.value.jobId != null) applicationViewModel.retryLoad()
                        navigateBack(Route.Profile)
                    }
                }
                RealResumeScreen(
                    state = state,
                    onBack = { navigateBack(Route.Profile) },
                    onRetry = resumeViewModel::load,
                    onSave = resumeViewModel::save,
                )
            }
            composable(Route.JobPreferences) {
                val preferenceViewModel: JobPreferenceViewModel = viewModel(
                    factory = JobPreferenceViewModel.factory(container.candidateRecommendationRepository),
                )
                val state by preferenceViewModel.state.collectAsStateWithLifecycle()
                JobPreferencesScreen(
                    state = state,
                    onRetry = preferenceViewModel::load,
                    onBack = { navigateBack(Route.Profile) },
                    onSave = preferenceViewModel::save,
                )
            }
        }
    }
}
