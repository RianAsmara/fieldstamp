package id.asmaraputra.fieldstamp.feature.paywall

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import id.asmaraputra.fieldstamp.R
import id.asmaraputra.fieldstamp.app.AppContainer
import id.asmaraputra.fieldstamp.app.appViewModel
import id.asmaraputra.fieldstamp.app.findActivity
import id.asmaraputra.fieldstamp.core.billing.EntitlementPolicy
import id.asmaraputra.fieldstamp.core.billing.ProductState
import id.asmaraputra.fieldstamp.core.billing.PurchaseEvent
import id.asmaraputra.fieldstamp.core.designsystem.FsTopBar
import id.asmaraputra.fieldstamp.core.designsystem.Notice
import id.asmaraputra.fieldstamp.core.designsystem.NoticeTone
import id.asmaraputra.fieldstamp.core.designsystem.SectionHeader
import id.asmaraputra.fieldstamp.core.designsystem.Spacing
import kotlinx.coroutines.launch

class PaywallViewModel(val c: AppContainer) : ViewModel() {
    val product = c.billing.product
    val entitlement = c.billing.entitlement
    val events = c.billing.events
    val pending = c.billing.hasPendingPurchase

    init { c.billing.refresh() }

    fun consume() = c.billing.consumeEvent()
}

@Composable
fun PaywallScreen(nav: NavController) {
    val vm = appViewModel { c, _ -> PaywallViewModel(c) }
    val product by vm.product.collectAsStateWithLifecycle()
    val entitlement by vm.entitlement.collectAsStateWithLifecycle()
    val event by vm.events.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(event) {
        val e = event ?: return@LaunchedEffect
        val msg = when (e) {
            PurchaseEvent.Purchased -> R.string.billing_purchased
            PurchaseEvent.Pending -> R.string.billing_pending
            PurchaseEvent.Cancelled -> R.string.billing_cancelled
            PurchaseEvent.AlreadyOwned -> R.string.billing_restored
            PurchaseEvent.VerificationFailed -> R.string.billing_verification_failed
            is PurchaseEvent.Failed -> R.string.billing_failed
        }
        vm.consume()
        scope.launch { snackbar.showSnackbar(resources.getString(msg)) }
    }

    Scaffold(
        topBar = { FsTopBar(stringResource(R.string.pro_title), onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!entitlement.isPro) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(Spacing.gutter)) {
                        when (val p = product) {
                            is ProductState.Available -> Button(
                                onClick = { context.findActivity()?.let { vm.c.billing.launchPurchase(it) } },
                                enabled = !pending,
                                modifier = Modifier.fillMaxWidth().height(Spacing.touch),
                            ) { Text(stringResource(R.string.pro_buy, p.formattedPrice)) }
                            ProductState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(Spacing.md))
                                Text(stringResource(R.string.pro_loading))
                            }
                            ProductState.NotConfigured -> Notice(stringResource(R.string.pro_not_configured), NoticeTone.INFO)
                            ProductState.NotFound -> Notice(stringResource(R.string.pro_not_found), NoticeTone.INFO)
                            is ProductState.Unavailable -> Notice(
                                stringResource(R.string.pro_unavailable),
                                NoticeTone.WARNING,
                                action = { TextButton(onClick = { vm.c.billing.refresh() }) { Text(stringResource(R.string.action_retry)) } },
                            )
                        }
                        if (pending) {
                            Spacer(Modifier.height(Spacing.sm))
                            Notice(stringResource(R.string.billing_pending), NoticeTone.INFO)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (entitlement.isPro) {
                Notice(stringResource(R.string.pro_active_notice), NoticeTone.INFO, Modifier.padding(Spacing.gutter))
            }
            Text(
                stringResource(R.string.pro_intro),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
            )
            SectionHeader(stringResource(R.string.pro_includes))
            listOf(R.string.pro_feature_templates, R.string.pro_feature_branding, R.string.pro_feature_layouts, R.string.pro_feature_zip).forEach {
                FeatureLine(stringResource(it))
            }
            SectionHeader(stringResource(R.string.pro_free_includes))
            listOf(R.string.free_feature_capture, R.string.free_feature_template, R.string.free_feature_reports, R.string.free_feature_export, R.string.free_feature_offline).forEach {
                FeatureLine(if (it == R.string.free_feature_template) stringResource(it, EntitlementPolicy.FREE_TEMPLATE_LIMIT) else stringResource(it))
            }
            Text(
                stringResource(R.string.pro_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.gutter),
            )
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.xs), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
