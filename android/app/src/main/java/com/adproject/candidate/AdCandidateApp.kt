package com.adproject.candidate

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.adproject.candidate.feature.applications.MyApplicationsScreen
import com.adproject.candidate.feature.auth.CreateAccountScreen
import com.adproject.candidate.feature.auth.SignInScreen
import com.adproject.candidate.feature.auth.AuthViewModel
import com.adproject.candidate.feature.jobs.JobDetailScreen
import com.adproject.candidate.feature.jobs.JobFeedScreen
import com.adproject.candidate.feature.jobs.LearningScreen
import com.adproject.candidate.feature.jobs.MessagesScreen
import com.adproject.candidate.feature.jobs.ChatDetailScreen
import com.adproject.candidate.feature.jobs.JobFeedViewModel
import com.adproject.candidate.feature.jobs.JobDetailViewModel
import com.adproject.candidate.core.network.CandidateAppContainer
import com.adproject.candidate.feature.profile.RealProfileScreen
import com.adproject.candidate.feature.profile.RealResumeScreen
import com.adproject.candidate.feature.profile.CandidateProfileViewModel
import com.adproject.candidate.feature.profile.CandidateResumeViewModel
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
    const val JobDetail = "job-detail/{jobId}"
    const val Apply = "apply/{jobId}"
    const val Submitted = "submitted/{jobId}"
    const val ResumeEdit = "resume-edit"

    fun jobDetail(id: String) = "job-detail/$id"
    fun chatDetail(id: String) = "chat-detail/$id"
    fun apply(id: String) = "apply/$id"
    fun submitted(id: String) = "submitted/$id"
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
                    onEmploymentType = jobsViewModel::selectEmploymentType,
                    onRefresh = jobsViewModel::refresh,
                    onRetry = jobsViewModel::retry,
                    onTab = ::openTab,
                    onJob = { navController.navigate(Route.jobDetail(it)) },
                )
            }
            composable(Route.Learning) { LearningScreen(fakeCandidateFeatures.getLearning(), ::openTab) }
            composable(Route.Messages) {
                MessagesScreen(fakeCandidateFeatures.getConversations(), ::openTab) { navController.navigate(Route.chatDetail(it)) }
            }
            composable(Route.ChatDetail) { entry ->
                val conversationId = entry.arguments?.getString("conversationId") ?: "mia"
                ChatDetailScreen(
                    thread = fakeCandidateFeatures.getChatThread(conversationId),
                    onBack = { navigateBack(Route.Messages) },
                    onViewJob = { navController.navigate(Route.jobDetail(it)) },
                    onSendMessage = { fakeCandidateFeatures.sendMessage(conversationId, it) },
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
                    onLogout = authViewModel::logout,
                    onTab = ::openTab,
                )
            }
            composable(Route.Applications) {
                MyApplicationsScreen(
                    data = fakeCandidateFeatures.getApplications(),
                    onTab = ::openTab,
                    onBack = { navigateBack(Route.Profile) },
                    onApplication = { navController.navigate(Route.jobDetail(it)) },
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
                )
            }
            composable(Route.ResumeEdit) {
                val resumeViewModel: CandidateResumeViewModel = viewModel(factory = CandidateResumeViewModel.factory(container.candidateResumeRepository))
                val state by resumeViewModel.state.collectAsStateWithLifecycle()
                RealResumeScreen(
                    state = state,
                    onBack = { navigateBack(Route.Profile) },
                    onRetry = resumeViewModel::load,
                    onSave = resumeViewModel::save,
                )
            }
        }
    }
}
