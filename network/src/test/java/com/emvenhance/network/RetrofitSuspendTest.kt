package com.emvenhance.network

import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * The Retrofit `suspend` wiring against a real HTTP server: Gson fills the Kotlin response
 * classes, a non-2xx answer throws, and cancelling the caller (the terminal's online timeout)
 * cancels the HTTP call.
 */
class RetrofitSuspendTest {

    private lateinit var server: MockWebServer
    private lateinit var behavior: RetrofitCommunicationBehavior

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val connection = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HostApiConnection::class.java)
        behavior = RetrofitCommunicationBehavior(connection)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun anApprovalIsParsedIntoTheKotlinResponse() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "{\"responseCode\":0,\"authCode\":\"123456\",\"chipData\":\"910A0102030405060708090A\"}",
            ),
        )

        val result = behavior.authorize(CONFIG)

        assertTrue(result.isApproved)
        assertEquals("123456", result.authCode)
        assertEquals("00", result.responseCode)
        assertEquals(10, result.arpc!!.size)
        val request = server.takeRequest()
        assertEquals("/crypto/purchase", request.path)
        assertTrue(request.body.readUtf8().contains("\"posEntryMode\":\"051\""))
    }

    @Test
    fun aDeclineKeepsTheHostsCodeAndMessage() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"responseCode\":51,\"responseMessage\":\"Insufficient funds\"}"))

        val result = behavior.authorize(CONFIG)

        assertTrue(!result.isApproved && !result.isFailed)
        assertEquals("51", result.responseCode)
        assertEquals("Insufficient funds", result.message)
    }

    @Test
    fun anHttpErrorIsThrown() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            behavior.authorize(CONFIG)
            fail("no exception")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
        }
    }

    @Test(timeout = 5_000)
    fun aTimeoutCancelsTheHttpCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) // a host that never answers

        val started = System.currentTimeMillis()
        try {
            withTimeout(200) { behavior.authorize(CONFIG) }
            fail("no timeout")
        } catch (e: TimeoutCancellationException) {
            // expected
        }

        assertTrue("waited " + (System.currentTimeMillis() - started) + " ms", System.currentTimeMillis() - started < 2_000)
    }

    private companion object {
        val CONFIG = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP).withIccData("9F2701")
    }
}
