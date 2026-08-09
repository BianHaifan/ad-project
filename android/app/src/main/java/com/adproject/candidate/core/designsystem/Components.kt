package com.adproject.candidate.core.designsystem

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.adproject.candidate.R

enum class MainTab(val label: String) { Jobs("Jobs"), Learn("Learn"), Messages("Messages"), Me("Me") }

@Composable
fun FigmaSvg(@RawRes resource: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    AsyncImage(model = resource, contentDescription = contentDescription, modifier = modifier)
}

@Composable
fun AdCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { Column(content = content) },
    )
}

@Composable
fun AdTopBar(title: String, onBack: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.White).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                FigmaSvg(R.raw.icon_back, "Back", Modifier.size(24.dp))
            }
        } else Spacer(Modifier.width(48.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            color = AdText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Box(Modifier.width(48.dp), contentAlignment = Alignment.CenterEnd) { action?.invoke() }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AdTeal, disabledContainerColor = Color(0xFF9AD7D6)),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp).border(1.dp, AdBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF47515C)),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun TagChip(text: String, accent: Boolean = false) {
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(if (accent) Color(0xFFE4F8F6) else AdChip)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (accent) AdTealDark else Color(0xFF687385), fontSize = 11.sp)
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    height: Int = 58,
) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFFAFBFB)).border(1.dp, AdBorder, RoundedCornerShape(10.dp))
            .height(height.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), color = Color(0xFF89929B), fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, modifier = Modifier.weight(1f), color = Color(0xFF27313B), fontSize = 11.sp)
            trailing?.let { Text(it, color = AdTealDark, fontSize = 10.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun Logo(size: Int = 56) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(AdTeal),
        contentAlignment = Alignment.Center,
    ) { Text("AD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * .32f).sp) }
}

@Composable
fun AdBottomBar(selected: MainTab, onSelected: (MainTab) -> Unit) {
    val icons = mapOf(
        MainTab.Jobs to (R.raw.nav_jobs to R.raw.nav_jobs_inactive),
        MainTab.Learn to (R.raw.nav_learning to R.raw.nav_learning_inactive),
        MainTab.Messages to (R.raw.nav_messages to R.raw.nav_messages_inactive),
        MainTab.Me to (R.raw.nav_profile to R.raw.nav_jobs_inactive),
    )
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        HorizontalDivider(color = Color(0xFFF0F2F4))
        Row(
            modifier = Modifier.fillMaxWidth().height(73.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MainTab.entries.forEach { tab ->
                val active = selected == tab
                Column(
                    Modifier.width(80.dp).clickable { onSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FigmaSvg(if (active) icons.getValue(tab).first else icons.getValue(tab).second, tab.label, Modifier.size(24.dp))
                    Text(tab.label, color = if (active) AdTeal else Color(0xFF6E7781), fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun StatusDot(text: String, active: Boolean) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(AdChip).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        FigmaSvg(if (active) R.raw.status_dot_active else R.raw.status_dot_inactive, null, Modifier.size(7.dp))
        Text(text, fontSize = 9.sp, color = Color(0xFF697582))
    }
}
