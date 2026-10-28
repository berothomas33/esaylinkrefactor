package com.emvenhance.network.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A host answer without `responseCode` must never read as 0 (approved): Gson leaves a missing
 * field at the JVM default, so the field is nullable and `SaleCommunicationBehavior` treats
 * `null` as online failed.
 */
class SaleResponseTest {

    @Test
    fun aMissingResponseCodeIsNullNotZero() {
        val response = Gson().fromJson("{\"responseMessage\":\"?\"}", SaleResponse::class.java)

        assertNull(response.responseCode)
    }

    @Test
    fun aPresentResponseCodeIsRead() {
        val response = Gson().fromJson("{\"responseCode\":0}", SaleResponse::class.java)

        assertEquals(0, response.responseCode)
    }
}
