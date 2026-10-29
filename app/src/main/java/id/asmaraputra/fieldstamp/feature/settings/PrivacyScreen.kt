package id.asmaraputra.fieldstamp.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.Spacing

/** In-app summary of data handling. Mirrors docs/PRIVACY_POLICY.md; keep both in sync. */
@Composable
fun PrivacyScreen(nav: NavController) {
    val sections = listOf(
        R.string.privacy_local_title to R.string.privacy_local_body,
        R.string.privacy_permissions_title to R.string.privacy_permissions_body,
        R.string.privacy_location_title to R.string.privacy_location_body,
        R.string.privacy_sharing_title to R.string.privacy_sharing_body,
        R.string.privacy_billing_title to R.string.privacy_billing_body,
        R.string.privacy_integrity_title to R.string.privacy_integrity_body,
        R.string.privacy_delete_title to R.string.privacy_delete_body,
        R.string.privacy_licenses_title to R.string.privacy_licenses_body,
    )
    Scaffold(topBar = { FsTopBar(stringResource(R.string.privacy_title), onBack = { nav.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter)) {
            sections.forEach { (title, body) ->
                Spacer(Modifier.height(Spacing.lg))
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Spacing.xs))
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
