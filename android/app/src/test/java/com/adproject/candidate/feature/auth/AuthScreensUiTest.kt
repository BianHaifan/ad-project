package com.adproject.candidate.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class AuthScreensUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun signInRendersDefaultAndSubmittingStates() {
        composeRule.setContent {
            SignInScreen(SignInUiState(), {}, {}, {}, {}, {})
        }
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in to continue your career journey").assertIsDisplayed()
        composeRule.onNodeWithText("New here?  Create an account").assertIsDisplayed()
        composeRule.onNodeWithText("Your password is encrypted and never shared with employers.")
            .assertIsDisplayed()
    }

    @Test
    fun signInShowsSubmittingMessageAndErrors() {
        composeRule.setContent {
            SignInScreen(
                state = SignInUiState(
                    submitting = true,
                    message = "Unable to sign in safely",
                    fieldErrors = mapOf("email" to "invalid", "password" to "Password is required"),
                ),
                onEmail = {},
                onPassword = {},
                onSignIn = {},
                onCreateAccount = {},
                onForgotPassword = {},
            )
        }

        composeRule.onNodeWithText("Signing in…").assertIsDisplayed()
        composeRule.onNodeWithText("Unable to sign in safely").assertIsDisplayed()
        composeRule.onNodeWithText("invalid").assertIsDisplayed()
        composeRule.onNodeWithText("Password is required").assertIsDisplayed()
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
    }

    @Test
    fun signInInvokesCallbacks() {
        var signInClicks = 0
        var createAccountClicks = 0
        var forgotPasswordClicks = 0
        composeRule.setContent {
            SignInScreen(
                state = SignInUiState(email = "a@b.com", password = "abc"),
                onEmail = {},
                onPassword = {},
                onSignIn = { signInClicks++ },
                onCreateAccount = { createAccountClicks++ },
                onForgotPassword = { forgotPasswordClicks++ },
            )
        }

        composeRule.onNodeWithText("a@b.com").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in").performClick()
        composeRule.onNodeWithText("New here?  Create an account").performClick()
        composeRule.onNodeWithText("Forgot password?").performClick()

        assertEquals(1, signInClicks)
        assertEquals(1, createAccountClicks)
        assertEquals(1, forgotPasswordClicks)
    }

    @Test
    fun createAccountRendersAllSections() {
        composeRule.setContent {
            CreateAccountScreen(
                state = RegisterUiState(),
                onFullName = {},
                onEmail = {},
                onPassword = {},
                onConfirmPassword = {},
                onAgreed = {},
                onCreate = {},
                onSignIn = {},
            )
        }

        composeRule.onNodeWithText("Create your account").assertIsDisplayed()
        composeRule.onNodeWithText("I agree to the Terms of Service and Privacy Policy.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Create account").assertIsDisplayed()
        composeRule.onNodeWithText("Already have an account?  Sign in").assertIsDisplayed()
    }

    @Test
    fun createAccountRendersPrefilledValuesAndInvokesCallbacks() {
        var createClicks = 0
        composeRule.setContent {
            CreateAccountScreen(
                state = RegisterUiState(
                    fullName = "Candidate",
                    email = "c@d.com",
                    password = "pw",
                    confirmPassword = "pw",
                    agreed = true,
                ),
                onFullName = {},
                onEmail = {},
                onPassword = {},
                onConfirmPassword = {},
                onAgreed = {},
                onCreate = { createClicks++ },
                onSignIn = {},
            )
        }

        composeRule.onNodeWithText("Candidate").assertIsDisplayed()
        composeRule.onNodeWithText("c@d.com").assertIsDisplayed()
        composeRule.onNodeWithText("Create account").performClick()

        assertEquals(1, createClicks)
    }

    @Test
    fun createAccountShowsValidationErrors() {
        composeRule.setContent {
            CreateAccountScreen(
                state = RegisterUiState(
                    fieldErrors = mapOf(
                        "fullName" to "Full name is required",
                        "agreed" to "You must accept the terms",
                    ),
                    message = "Email already registered",
                ),
                onFullName = {},
                onEmail = {},
                onPassword = {},
                onConfirmPassword = {},
                onAgreed = {},
                onCreate = {},
                onSignIn = {},
            )
        }

        composeRule.onNodeWithText("Full name is required").assertIsDisplayed()
        composeRule.onNodeWithText("You must accept the terms").assertIsDisplayed()
        composeRule.onNodeWithText("Email already registered").assertIsDisplayed()
    }
}
