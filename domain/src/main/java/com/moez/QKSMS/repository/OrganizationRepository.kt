package dev.octoshrimpy.quik.repository

import io.reactivex.Completable

/** Local-first organization data used by categories, message tags, and sender verification. */
interface OrganizationRepository {
    data class VerifiedSenderData(
        val address: String,
        val displayName: String,
        val description: String = "",
        val logoUri: String? = null,
        val source: String = "local",
        val verifiedAt: Long = System.currentTimeMillis(),
    )

    fun getCategories(): List<String>

    fun addCategory(name: String): Boolean

    fun deleteCategory(name: String)

    fun setConversationCategory(threadIds: Collection<Long>, category: String): Completable

    fun getTags(): List<String>

    fun tagMessages(messageIds: Collection<Long>, tag: String, enabled: Boolean = true): Completable

    fun getVerifiedSenders(): List<VerifiedSenderData>

    fun getVerifiedSender(address: String): VerifiedSenderData?

    fun saveVerifiedSender(sender: VerifiedSenderData): Completable

    fun removeVerifiedSender(address: String): Completable
}
