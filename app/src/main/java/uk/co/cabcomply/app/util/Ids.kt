package uk.co.cabcomply.app.util

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
