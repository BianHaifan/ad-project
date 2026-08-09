package com.adproject.candidate

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adproject.candidate.core.designsystem.MainTab
import com.adproject.candidate.feature.applications.ApplicationSubmittedScreen
import com.adproject.candidate.feature.applications.ApplyConfirmationScreen
import com.adproject.candidate.feature.applications.MyApplicationsScreen
import com.adproject.candidate.feature.auth.CreateAccountScreen
import com.adproject.candidate.feature.auth.SignInScreen
import com.adproject.candidate.feature.jobs.JobDetailScreen
import com.adproject.candidate.feature.jobs.JobFeedScreen
import com.adproject.candidate.feature.jobs.LearningScreen
import com.adproject.candidate.feature.jobs.MessagesScreen
import com.adproject.candidate.feature.jobs.ProfileScreen
import com.adproject.candidate.feature.jobs.ChatDetailScreen
import com.adproject.candidate.feature.profile.ResumeEditScreen
import com.adproject.candidate.data.api.CandidateApi
import com.adproject.candidate.data.api.FakeCandidateApi

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
fun AdCandidateApp(api: CandidateApi = FakeCandidateApi) {
    val navController = rememberNavController()

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
        NavHost(navController = navController, startDestination = Route.SignIn) {
            composable(Route.SignIn) {
                SignInScreen(
                    data = api.getSignInDefaults(),
                    onSignIn = { navController.navigate(Route.Jobs) { popUpTo(Route.SignIn) { inclusive = true } } },
                    onCreateAccount = { navController.navigate(Route.CreateAccount) },
                )
            }
            composable(Route.CreateAccount) {
                CreateAccountScreen(
                    data = api.getRegistrationDefaults(),
                    onCreate = { navController.navigate(Route.Jobs) { popUpTo(Route.SignIn) { inclusive = true } } },
                    onSignIn = { navController.popBackStack() },
                )
            }
            composable(Route.Jobs) { JobFeedScreen(api.getJobFeed(), ::openTab) { navController.navigate(Route.jobDetail(it)) } }
            composable(Route.Learning) { LearningScreen(api.getLearning(), ::openTab) }
            composable(Route.Messages) {
                MessagesScreen(api.getConversations(), ::openTab) { navController.navigate(Route.chatDetail(it)) }
            }
            composable(Route.ChatDetail) { entry ->
                val conversationId = entry.arguments?.getString("conversationId") ?: "mia"
                ChatDetailScreen(
                    thread = api.getChatThread(conversationId),
                    onBack = { navigateBack(Route.Messages) },
                    onViewJob = { navController.navigate(Route.jobDetail(it)) },
                    onSendMessage = { api.sendMessage(conversationId, it) },
                )
            }
            composable(Route.Profile) {
                ProfileScreen(
                    data = api.getProfile(),
                    onTab = ::openTab,
                    onApplications = { navController.navigate(Route.Applications) },
                    onResume = { navController.navigate(Route.ResumeEdit) },
                )
            }
            composable(Route.Applications) {
                MyApplicationsScreen(
                    data = api.getApplications(),
                    onTab = ::openTab,
                    onBack = { navigateBack(Route.Profile) },
                    onApplication = { navController.navigate(Route.jobDetail(it)) },
                )
            }
            composable(Route.JobDetail) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                JobDetailScreen(
                    data = api.getJobDetail(jobId),
                    onBack = { navigateBack(Route.Jobs) },
                    onApply = { navController.navigate(Route.apply(jobId)) },
                )
            }
            composable(Route.Apply) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                ApplyConfirmationScreen(
                    data = api.getApplyConfirmation(jobId),
                    onBack = { navigateBack(Route.jobDetail(jobId)) },
                    onSubmit = { navController.navigate(Route.submitted(jobId)) },
                )
            }
            composable(Route.Submitted) { entry ->
                val jobId = entry.arguments?.getString("jobId") ?: "moonshot"
                ApplicationSubmittedScreen(
                    data = api.submitApplication(jobId),
                    onApplications = { navController.navigate(Route.Applications) { popUpTo(Route.Jobs) } },
                    onJobs = { navController.navigate(Route.Jobs) { popUpTo(Route.Jobs) { inclusive = true } } },
                )
            }
            composable(Route.ResumeEdit) {
                ResumeEditScreen(
                    data = api.getResume(),
                    onBack = { navigateBack(Route.Profile) },
                    onSave = { api.saveResume(it) },
                )
            }
        }
    }
}
