package id.asmaraputra.fieldstamp.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.container
import id.asmaraputra.fieldstamp.core.designsystem.FieldStampTheme
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import id.asmaraputra.fieldstamp.core.model.CaptureTime
import id.asmaraputra.fieldstamp.core.model.Formats
import kotlinx.coroutines.launch
import java.time.YearMonth

private data class OnboardingPage(@StringRes val title: Int, @StringRes val body: Int, val illustration: @Composable () -> Unit)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_stamp_title, R.string.onboarding_stamp_body) { StampIllustration() },
    OnboardingPage(R.string.onboarding_report_title, R.string.onboarding_report_body) { ReportIllustration() },
    OnboardingPage(R.string.onboarding_private_title, R.string.onboarding_private_body) { PrivacyIllustration() },
)

/** First-run introduction. Finishing or skipping it is remembered, so it shows once per install. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { pages.size }
    val last = pager.currentPage == pages.lastIndex
    val finish: () -> Unit = {
        // Leave immediately; the flag is written on the app scope because this screen's scope ends with it.
        val container = context.container
        container.appScope.launch { container.settings.setLaunchedBefore(true) }
        onDone()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.systemBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(start = Spacing.gutter, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.onboarding_step, pager.currentPage + 1, pages.size),
                    style = FieldStampTheme.mono.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!last) TextButton(onClick = finish) { Text(stringResource(R.string.onboarding_skip)) }
            }

            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
                val page = pages[index]
                Column(Modifier.fillMaxSize().padding(horizontal = Spacing.gutter)) {
                    Box(Modifier.weight(3f).fillMaxWidth().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                        page.illustration()
                    }
                    // Fixed share of the height so the title starts at the same place on every page;
                    // scrolls instead of clipping at large font scales.
                    Column(Modifier.weight(2f).verticalScroll(rememberScrollState()).padding(top = Spacing.xl)) {
                        Text(
                            stringResource(page.title),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(Spacing.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageIndicator(pager.currentPage, pages.size, Modifier.weight(1f))
                Button(
                    onClick = { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.height(Spacing.touch),
                ) {
                    Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next))
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    // Decorative: the "1 / 3" counter in the header carries the position for screen readers.
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .height(4.dp)
                    .width(if (i == current) 24.dp else 12.dp)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

/** A photo with the stamp the app burns in: time, coordinates, place. */
@Composable
private fun StampIllustration() {
    Box(
        Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            Modifier
                .padding(Spacing.md)
                .clip(MaterialTheme.shapes.small)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            val stamp = FieldStampTheme.mono.small
            Text(Formats.stampDateTime(CaptureTime.now()), color = Color.White, style = stamp)
            Text("-6.175392, 106.827152", color = Color.White, style = stamp)
            Text(stringResource(R.string.onboarding_sample_place), color = Color.White, style = stamp)
        }
    }
}

/** Project and activity rows flowing into a monthly PDF. */
@Composable
private fun ReportIllustration() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        IllustrationRow(Icons.Outlined.Folder, stringResource(R.string.onboarding_sample_project), stringResource(R.string.onboarding_sample_activity))
        Icon(
            Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.xs),
        )
        IllustrationRow(
            Icons.Outlined.PictureAsPdf,
            stringResource(R.string.onboarding_sample_report, Formats.monthYear(YearMonth.now())),
            "PDF",
            accent = true,
        )
    }
}

@Composable
private fun IllustrationRow(icon: ImageVector, title: String, subtitle: String, accent: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = FieldStampTheme.mono.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The phone as the only place data lives, with the three promises as chips. */
@Composable
private fun PrivacyIllustration() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            PromiseChip(Icons.Outlined.NoAccounts, R.string.onboarding_no_account)
            PromiseChip(Icons.Outlined.CloudOff, R.string.onboarding_no_upload)
            PromiseChip(Icons.Outlined.WifiOff, R.string.onboarding_offline)
        }
    }
}

@Composable
private fun PromiseChip(icon: ImageVector, @StringRes label: Int) {
    Row(
        Modifier
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}
