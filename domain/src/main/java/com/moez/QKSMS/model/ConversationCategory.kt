package dev.octoshrimpy.quik.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/** A user-created conversation tab/category. */
open class ConversationCategory(
    @PrimaryKey var name: String = "General",
) : RealmObject()
