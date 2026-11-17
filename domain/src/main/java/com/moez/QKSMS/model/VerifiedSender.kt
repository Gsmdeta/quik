package dev.octoshrimpy.quik.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Local-first verified sender record. The model is deliberately source-aware
 * so a future community service can be added without changing stored data.
 */
open class VerifiedSender(
    @PrimaryKey var address: String = "",
    var displayName: String = "",
    var description: String = "",
    var logoUri: String? = null,
    var source: String = "local",
    var verifiedAt: Long = 0,
) : RealmObject()
