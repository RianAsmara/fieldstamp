package id.asmaraputra.fieldstamp.core.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import id.asmaraputra.fieldstamp.BuildConfig
import id.asmaraputra.fieldstamp.core.settings.EntitlementCache
import id.asmaraputra.fieldstamp.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.coroutines.resume

/** Billing configuration lives in gradle.properties, not scattered through the code. */
data class BillingConfig(
    val proProductId: String = BuildConfig.PRO_PRODUCT_ID,
    val licenseKey: String = BuildConfig.PLAY_LICENSE_KEY,
) {
    val isConfigured: Boolean get() = proProductId.isNotBlank()
}

sealed interface ProductState {
    data object Loading : ProductState
    /** No product ID was configured for this build. */
    data object NotConfigured : ProductState
    /** Play is reachable but the product does not exist or is inactive in Play Console. */
    data object NotFound : ProductState
    data class Unavailable(val responseCode: Int) : ProductState
    data class Available(val formattedPrice: String, internal val details: ProductDetails) : ProductState
}

sealed interface PurchaseEvent {
    data object Purchased : PurchaseEvent
    data object Pending : PurchaseEvent
    data object Cancelled : PurchaseEvent
    data object AlreadyOwned : PurchaseEvent
    data object VerificationFailed : PurchaseEvent
    data class Failed(val responseCode: Int) : PurchaseEvent
}

sealed interface RestoreResult {
    data object Restored : RestoreResult
    data object NothingToRestore : RestoreResult
    data class Failed(val responseCode: Int) : RestoreResult
}

/**
 * Google Play Billing for the one-time Pro unlock (non-consumable in-app product).
 * Core features never wait on this class; it only decides whether Pro extras are unlocked.
 */
class BillingManager(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val config: BillingConfig = BillingConfig(),
) {
    private val _product = MutableStateFlow<ProductState>(if (config.isConfigured) ProductState.Loading else ProductState.NotConfigured)
    val product: StateFlow<ProductState> = _product.asStateFlow()

    private val _events = MutableStateFlow<PurchaseEvent?>(null)
    val events: StateFlow<PurchaseEvent?> = _events.asStateFlow()

    private val _pending = MutableStateFlow(false)
    val hasPendingPurchase: StateFlow<Boolean> = _pending.asStateFlow()

    private val _playVerifiedThisSession = MutableStateFlow(false)

    val entitlement: StateFlow<Entitlement> = combine(
        settings.entitlementCache,
        settings.debugProOverride,
        _playVerifiedThisSession,
    ) { cache, debug, verifiedNow ->
        val resolved = EntitlementPolicy.resolve(cache, System.currentTimeMillis(), debug && BuildConfig.DEBUG)
        if (resolved.source == EntitlementSource.CACHED && verifiedNow) resolved.copy(source = EntitlementSource.PLAY_VERIFIED) else resolved
    }.stateIn(scope, SharingStarted.Eagerly, Entitlement.FREE)

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        scope.launch { onPurchasesUpdated(result, purchases.orEmpty()) }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun consumeEvent() { _events.value = null }

    /** Connects (if needed), refreshes product details and the owned purchase. Safe to call often. */
    fun refresh() {
        if (!config.isConfigured) return
        scope.launch {
            val connected = ensureConnected()
            if (connected.responseCode != BillingClient.BillingResponseCode.OK) {
                _product.value = ProductState.Unavailable(connected.responseCode)
                return@launch
            }
            loadProduct()
            restore()
        }
    }

    private suspend fun ensureConnected(): BillingResult {
        if (client.isReady) return BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    private suspend fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(config.proProductId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            ).build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _product.value = ProductState.Unavailable(result.billingResult.responseCode)
            return
        }
        val details = result.productDetailsList?.firstOrNull()
        val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
        _product.value = if (details != null && price != null) ProductState.Available(price, details) else ProductState.NotFound
    }

    fun launchPurchase(activity: Activity) {
        val available = _product.value as? ProductState.Available ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(available.details).build()),
            ).build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.value = PurchaseEvent.Failed(result.responseCode)
        }
    }

    suspend fun restore(): RestoreResult {
        if (!config.isConfigured) return RestoreResult.Failed(BillingClient.BillingResponseCode.DEVELOPER_ERROR)
        val connected = ensureConnected()
        if (connected.responseCode != BillingClient.BillingResponseCode.OK) return RestoreResult.Failed(connected.responseCode)
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Keep the last known state; a transient error is not a revocation.
            return RestoreResult.Failed(result.billingResult.responseCode)
        }
        val owned = result.purchasesList.filter { config.proProductId in it.products }
        val purchased = owned.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED && isSignatureValid(it) }
        _pending.value = owned.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        return if (purchased != null) {
            acknowledgeIfNeeded(purchased)
            grant(purchased)
            RestoreResult.Restored
        } else {
            // Play answered authoritatively that nothing is owned (refunded, revoked or never bought).
            settings.saveEntitlement(EntitlementCache(false, System.currentTimeMillis(), null))
            _playVerifiedThisSession.value = false
            RestoreResult.NothingToRestore
        }
    }

    private suspend fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val pro = purchases.filter { config.proProductId in it.products }
                val purchased = pro.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                when {
                    purchased != null && !isSignatureValid(purchased) -> _events.value = PurchaseEvent.VerificationFailed
                    purchased != null -> {
                        acknowledgeIfNeeded(purchased)
                        grant(purchased)
                        _events.value = PurchaseEvent.Purchased
                    }
                    pro.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> {
                        _pending.value = true
                        _events.value = PurchaseEvent.Pending
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.value = PurchaseEvent.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                restore()
                _events.value = PurchaseEvent.AlreadyOwned
            }
            else -> _events.value = PurchaseEvent.Failed(result.responseCode)
        }
    }

    private suspend fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        // Non-consumable: acknowledge (never consume). Unacknowledged purchases are refunded by Play after 3 days.
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
    }

    private suspend fun grant(purchase: Purchase) {
        _pending.value = false
        settings.saveEntitlement(EntitlementCache(true, System.currentTimeMillis(), sha256(purchase.purchaseToken)))
        _playVerifiedThisSession.value = true
    }

    /**
     * Verifies Play's RSA signature when a license key is configured. Without a key the purchase is accepted
     * as reported by the Play client (documented limitation: there is no server-side verification).
     */
    private fun isSignatureValid(purchase: Purchase): Boolean {
        if (config.licenseKey.isBlank()) return true
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(config.licenseKey, Base64.DEFAULT)))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(purchase.originalJson.toByteArray())
                verify(Base64.decode(purchase.signature, Base64.DEFAULT))
            }
        }.getOrDefault(false)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
