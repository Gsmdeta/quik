package dev.octoshrimpy.quik.blocking

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.Completable
import io.reactivex.Single
import timber.log.Timber
import javax.inject.Inject

/**
 * QUIK-side adapter for SpamBlocker's local screening provider.
 *
 * QUIK remains the default SMS app and performs all provider writes/deletes.
 * SpamBlocker only supplies the screening decision and never receives a
 * provider write capability.
 */
class SpamBlockerBlockingClient @Inject constructor(
    private val context: Context,
) : BlockingClient {

    companion object {
        private const val AUTHORITY = "spam.blocker.quik"
        private const val METHOD_SCREEN_SMS = "screen_sms"
        private const val METHOD_SET_ENABLED = "set_enabled"
        private const val METHOD_BLOCK_NUMBERS = "block_numbers"
        private const val METHOD_UNBLOCK_NUMBERS = "unblock_numbers"

        private const val KEY_ADDRESS = "address"
        private const val KEY_BODY = "body"
        private const val KEY_SIM_SLOT = "sim_slot"
        private const val KEY_SHOULD_BLOCK = "should_block"
        private const val KEY_REASON = "reason"
        private const val KEY_ERROR = "error"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NUMBERS = "numbers"

        private val providerUri = Uri.parse("content://$AUTHORITY")
    }

    private fun resolver(): ContentResolver = context.contentResolver

    private fun providerPackage(): String? = context.packageManager
        .resolveContentProvider(AUTHORITY, PackageManager.MATCH_ALL)
        ?.packageName

    override fun isAvailable(): Boolean = providerPackage() != null

    override fun getClientCapability() = BlockingClient.Capability.BLOCK_WITHOUT_PERMISSION

    override fun shouldBlock(address: String): Single<BlockingClient.Action> =
        screen(address = address, body = "", simSlot = null)

    override fun shouldBlock(message: Message): Single<BlockingClient.Action> =
        screen(address = message.address, body = message.getText(), simSlot = null)

    override fun isBlacklisted(address: String): Single<BlockingClient.Action> =
        shouldBlock(address)

    private fun screen(address: String, body: String, simSlot: Int?): Single<BlockingClient.Action> =
        Single.fromCallable {
            if (!isAvailable()) return@fromCallable BlockingClient.Action.DoNothing

            val request = Bundle().apply {
                putString(KEY_ADDRESS, address)
                putString(KEY_BODY, body)
                simSlot?.let { putInt(KEY_SIM_SLOT, it) }
            }
            val result = resolver().call(providerUri, METHOD_SCREEN_SMS, null, request)
                ?: return@fromCallable BlockingClient.Action.DoNothing

            if (result.getBoolean(KEY_ERROR, false)) {
                Timber.w("SpamBlocker screening failed: ${result.getString(KEY_REASON)}")
                return@fromCallable BlockingClient.Action.DoNothing
            }

            if (result.getBoolean(KEY_SHOULD_BLOCK, false)) {
                BlockingClient.Action.Block(
                    reason = result.getString(KEY_REASON),
                )
            } else {
                BlockingClient.Action.Unblock
            }
        }.onErrorReturn {
            Timber.w(it, "SpamBlocker is unavailable")
            BlockingClient.Action.DoNothing
        }

    override fun block(addresses: List<String>): Completable = updateNumbers(METHOD_BLOCK_NUMBERS, addresses)

    override fun unblock(addresses: List<String>): Completable = updateNumbers(METHOD_UNBLOCK_NUMBERS, addresses)

    private fun updateNumbers(method: String, addresses: List<String>): Completable =
        Completable.fromCallable {
            if (!isAvailable()) return@fromCallable
            resolver().call(providerUri, method, null, Bundle().apply {
                putStringArrayList(KEY_NUMBERS, ArrayList(addresses))
            })
        }

    fun setIntegrationEnabled(enabled: Boolean): Completable = Completable.fromCallable {
        if (!isAvailable()) return@fromCallable
        resolver().call(providerUri, METHOD_SET_ENABLED, null, Bundle().apply {
            putBoolean(KEY_ENABLED, enabled)
        })
    }

    override fun openSettings() {
        context.packageManager.getLaunchIntentForPackage(providerPackage() ?: return)?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }
}
