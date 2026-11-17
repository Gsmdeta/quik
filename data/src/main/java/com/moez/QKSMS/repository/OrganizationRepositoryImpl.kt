package dev.octoshrimpy.quik.repository

import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.ConversationCategory
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.model.VerifiedSender
import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import javax.inject.Inject

class OrganizationRepositoryImpl @Inject constructor() : OrganizationRepository {
    override fun getCategories(): List<String> = Realm.getDefaultInstance().use { realm ->
        realm.where(Conversation::class.java)
            .findAll()
            .map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .let { categories ->
                val saved = realm.where(ConversationCategory::class.java).findAll().map { it.name }
                (listOf("General") + categories + saved).distinct().sorted()
            }
    }

    override fun addCategory(name: String): Boolean {
        val normalized = name.trim()
        if (normalized.isEmpty()) return false
        Realm.getDefaultInstance().use { realm ->
            if (realm.where(ConversationCategory::class.java)
                    .equalTo("name", normalized)
                    .count() > 0L
            ) return false
            realm.executeTransaction { realm.insert(ConversationCategory(normalized)) }
        }
        return true
    }

    override fun deleteCategory(name: String) {
        if (name == "General") return
        Realm.getDefaultInstance().use { realm ->
            realm.executeTransaction {
                realm.where(Conversation::class.java)
                    .equalTo("category", name)
                    .findAll()
                    .forEach { it.category = "General" }
                realm.where(ConversationCategory::class.java)
                    .equalTo("name", name)
                    .findAll()
                    .deleteAllFromRealm()
            }
        }
    }

    override fun setConversationCategory(threadIds: Collection<Long>, category: String) =
        Completable.fromAction {
            val normalized = category.trim().ifEmpty { "General" }
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.where(Conversation::class.java)
                        .`in`("id", threadIds.toLongArray())
                        .findAll()
                        .forEach { it.category = normalized }
                }
            }
        }.subscribeOn(Schedulers.io())

    override fun getTags(): List<String> = Realm.getDefaultInstance().use { realm ->
        realm.where(Message::class.java)
            .findAll()
            .flatMap { message -> message.tags.toList() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .sorted()
    }

    override fun tagMessages(messageIds: Collection<Long>, tag: String, enabled: Boolean) =
        Completable.fromAction {
            val normalized = tag.trim()
            if (normalized.isEmpty()) return@fromAction
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.where(Message::class.java)
                        .`in`("id", messageIds.toLongArray())
                        .findAll()
                        .forEach { message ->
                            if (enabled && normalized !in message.tags) message.tags.add(normalized)
                            if (!enabled) message.tags.remove(normalized)
                        }
                }
            }
        }.subscribeOn(Schedulers.io())

    override fun getVerifiedSenders(): List<OrganizationRepository.VerifiedSenderData> =
        Realm.getDefaultInstance().use { realm ->
            realm.where(VerifiedSender::class.java).findAll().map(::toData)
        }

    override fun getVerifiedSender(address: String): OrganizationRepository.VerifiedSenderData? =
        Realm.getDefaultInstance().use { realm ->
            realm.where(VerifiedSender::class.java)
                .equalTo("address", address)
                .findFirst()
                ?.let(::toData)
        }

    override fun saveVerifiedSender(sender: OrganizationRepository.VerifiedSenderData) =
        Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.copyToRealmOrUpdate(
                        VerifiedSender(
                            address = sender.address.trim(),
                            displayName = sender.displayName.trim(),
                            description = sender.description.trim(),
                            logoUri = sender.logoUri,
                            source = sender.source.trim().ifEmpty { "local" },
                            verifiedAt = sender.verifiedAt,
                        )
                    )
                }
            }
        }.subscribeOn(Schedulers.io())

    override fun removeVerifiedSender(address: String) =
        Completable.fromAction {
            Realm.getDefaultInstance().use { realm ->
                realm.executeTransaction {
                    realm.where(VerifiedSender::class.java)
                        .equalTo("address", address)
                        .findAll()
                        .deleteAllFromRealm()
                }
            }
        }.subscribeOn(Schedulers.io())

    private fun toData(sender: VerifiedSender) = OrganizationRepository.VerifiedSenderData(
        address = sender.address,
        displayName = sender.displayName,
        description = sender.description,
        logoUri = sender.logoUri,
        source = sender.source,
        verifiedAt = sender.verifiedAt,
    )
}
