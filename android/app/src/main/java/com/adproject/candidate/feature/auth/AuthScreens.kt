package com.adproject.candidate.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adproject.candidate.core.designsystem.AdBackground
import com.adproject.candidate.core.designsystem.AdBorder
import com.adproject.candidate.core.designsystem.AdCard
import com.adproject.candidate.core.designsystem.AdMuted
import com.adproject.candidate.core.designsystem.AdTeal
import com.adproject.candidate.core.designsystem.AdTealDark
import com.adproject.candidate.core.designsystem.AdText
import com.adproject.candidate.core.designsystem.Logo
import com.adproject.candidate.core.designsystem.PrimaryButton

@Composable
fun SignInScreen(
    state: SignInUiState,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(AdBackground).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo()
        Spacer(Modifier.height(12.dp))
        Text("Welcome back", color = AdText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Sign in to continue your career journey", color = AdMuted, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AuthField("EMAIL", state.email, onEmail, error = state.fieldErrors["email"])
                AuthField("PASSWORD", state.password, onPassword, password = true, error = state.fieldErrors["password"])
                state.message?.let { Text(it, color = Color(0xFFB42318), fontSize = 11.sp) }
                Text("Forgot password?", Modifier.fillMaxWidth(), color = AdTealDark, fontSize = 10.sp, textAlign = TextAlign.End)
                PrimaryButton(if (state.submitting) "Signing in…" else "Sign in", onSignIn,
                    Modifier.fillMaxWidth(), enabled = !state.submitting)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE1E6E9)))
                    Text("Or", Modifier.padding(horizontal = 10.dp), color = Color(0xFF929AA2), fontSize = 10.sp)
                    Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE1E6E9)))
                }
                Text("New here?  Create an account", color = AdTealDark, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onCreateAccount))
                Text("Your password is encrypted and never shared with employers.", color = Color(0xFF8B949E), fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun CreateAccountScreen(
    state: RegisterUiState,
    onFullName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirmPassword: (String) -> Unit,
    onAgreed: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(AdBackground).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Logo(44)
        Spacer(Modifier.height(8.dp))
        Text("Create your account", color = AdText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Start applying or hiring with AD", color = AdMuted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        AdCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthField("FULL NAME", state.fullName, onFullName, error = state.fieldErrors["fullName"])
                AuthField("EMAIL", state.email, onEmail, error = state.fieldErrors["email"])
                AuthField("PASSWORD", state.password, onPassword, password = true, error = state.fieldErrors["password"])
                AuthField("CONFIRM PASSWORD", state.confirmPassword, onConfirmPassword, password = true,
                    error = state.fieldErrors["confirmPassword"])
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.agreed, onAgreed, enabled = !state.submitting,
                        colors = CheckboxDefaults.colors(checkedColor = AdTeal))
                    Text("I agree to the Terms of Service and Privacy Policy.", color = AdMuted, fontSize = 9.sp)
                }
                state.fieldErrors["agreed"]?.let { Text(it, color = Color(0xFFB42318), fontSize = 10.sp) }
                state.message?.let { Text(it, color = Color(0xFFB42318), fontSize = 11.sp) }
                PrimaryButton(if (state.submitting) "Creating account…" else "Create account", onCreate,
                    Modifier.fillMaxWidth(), enabled = !state.submitting)
                Text("Already have an account?  Sign in", Modifier.fillMaxWidth().clickable(onClick = onSignIn), color = AdTealDark, fontSize = 10.sp, textAlign = TextAlign.Center)
                Text("We'll send a verification email before your account becomes active.", color = Color(0xFF8B949E), fontSize = 8.sp)
                Spacer(Modifier.height(84.dp))
            }
        }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    trailing: String? = null,
    onTrailing: () -> Unit = {},
    error: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 9.sp) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing?.let { { Text(it, Modifier.clickable(onClick = onTrailing), color = AdTealDark, fontSize = 10.sp) } },
        isError = error != null,
        supportingText = error?.let { { Text(it, fontSize = 10.sp) } },
        shape = RoundedCornerShape(11.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AdTeal,
            unfocusedBorderColor = AdBorder,
            focusedContainerColor = Color(0xFFFAFBFB),
            unfocusedContainerColor = Color(0xFFFAFBFB),
        ),
    )
}
