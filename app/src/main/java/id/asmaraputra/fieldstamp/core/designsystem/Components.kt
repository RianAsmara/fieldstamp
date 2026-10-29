package id.asmaraputra.fieldstamp.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.model.Formats
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FsTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

/** Empty state: what is missing, why it matters, and the one action that fixes it. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.size(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.size(Spacing.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.size(Spacing.lg))
            action()
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

enum class NoticeTone { INFO, WARNING, ERROR }

/** Inline status message. Tone is conveyed by icon and text, not color alone. */
@Composable
fun Notice(
    text: String,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val ext = FieldStampTheme.extended
    val (container, content, icon) = when (tone) {
        NoticeTone.INFO -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Outlined.Info)
        NoticeTone.WARNING -> Triple(ext.warningContainer, ext.onWarningContainer, Icons.Outlined.WarningAmber)
        NoticeTone.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Outlined.ErrorOutline)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (action != null) {
                    Spacer(Modifier.size(Spacing.xs))
                    action()
                }
            }
        }
    }
}

@Composable
fun ErrorState(title: String, body: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = title,
        body = body,
        modifier = modifier,
        action = onRetry?.let { { OutlinedButton(onClick = it) { Text(stringResource(R.string.action_retry)) } } },
    )
}

/** Small uppercase label above a group of rows. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(Formats.locale),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Label/value pair for metadata. Machine values (time, coordinates) use the mono face. */
@Composable
fun MetadataRow(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = false, muted: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp, max = 120.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            value,
            style = if (mono) FieldStampTheme.mono.medium else MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleMaxLines: Int = 2,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = titleMaxLines, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (badge != null) {
                    Spacer(Modifier.width(Spacing.sm))
                    badge()
                }
            }
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pro_badge)
    Row(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

/** Groups related rows or content one tonal step above the background. Never nest cards. */
@Composable
fun FsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = FieldStampTheme.extended.card,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** Shrinks the element while pressed and springs back on release, so taps feel physical. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press(), label = "pressScale")
    // Read in the layer block so the animation only redraws, never recomposes.
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun FsDivider(modifier: Modifier = Modifier, inset: Boolean = false) {
    HorizontalDivider(modifier.padding(start = if (inset) Spacing.gutter else 0.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Thumbnail from an app-owned file. Shows an explicit "file missing" tile instead of a blank. */
@Composable
fun PhotoThumbnail(file: File?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val shape = MaterialTheme.shapes.small
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (file == null || !file.isFile) {
            MissingImage(Modifier.fillMaxSize())
        } else {
            SubcomposeAsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                error = { MissingImage(Modifier.fillMaxSize()) },
            )
        }
    }
}

@Composable
fun MissingImage(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.photo_file_missing)
    Column(
        modifier.padding(Spacing.xs).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * Extended FAB whose label is always exposed to accessibility services. The Material component's merged
 * semantics can omit the text label, which leaves TalkBack (and UI tests) with an unlabeled button.
 */
@Composable
fun FsExtendedFab(label: String, icon: ImageVector, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (trailing != null) { Spacer(Modifier.width(Spacing.sm)); trailing() }
            }
        },
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/**
 * The capture action ("Ambil foto"). The only place besides the shutter that uses the signal yellow,
 * so it is always the most visible control on screen.
 */
@Composable
fun CaptureButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ext = FieldStampTheme.extended
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        colors = ButtonDefaults.buttonColors(containerColor = ext.capture, contentColor = ext.onCapture),
        border = BorderStroke(2.dp, ext.captureEdge),
        shape = MaterialTheme.shapes.medium,
        interactionSource = interaction,
        modifier = modifier.pressScale(interaction).heightIn(min = Spacing.capture),
    ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
