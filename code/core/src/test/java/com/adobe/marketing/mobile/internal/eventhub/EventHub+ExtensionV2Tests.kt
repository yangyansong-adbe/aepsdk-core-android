package com.adobe.marketing.mobile.internal.eventhub

import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.ExtensionApiV2
import com.adobe.marketing.mobile.ExtensionApiV2Builder
import com.adobe.marketing.mobile.ExtensionV2
import com.adobe.marketing.mobile.ExtensionV2Delegate
import com.adobe.marketing.mobile.internal.configuration.ConfigurationExtension
import kotlinx.coroutines.delay
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ExtensionV2Sample : ExtensionV2() {
    override val name: String
        get() = "com.test.ExtensionV2Sample"
    override val friendlyName: String?
        get() = null
    override val version: String?
        get() = null
    override val metadata: Map<String, String>?
        get() = null

    private var api: ExtensionApiV2? = null

    override fun onRegistered(buildExtensionApi: ExtensionApiV2Builder) {
        api = buildExtensionApi { _ ->
            return@buildExtensionApi true
        }
        api?.registerEventListener("TypeX", "SourceX") { event ->
            delay(100)
            receivedXEvents.add(event)
        }
        api?.registerEventListener("TypeY", "SourceY") { event ->
            delay(10)
            receivedYEvents.add(event)
        }
    }
}

private val receivedXEvents = mutableListOf<Event>()
private val receivedYEvents = mutableListOf<Event>()

private class ExtensionV2DelegateImpl : ExtensionV2Delegate() {
    override fun getExtensionV2Class(): Class<out ExtensionV2> {
        return ExtensionV2Sample::class.java
    }
}

class EventHubExtensionV2Tests {

    @Test
    fun isExtensionV2DelegateTest() {
        assertTrue(isExtensionV2Delegate(ExtensionV2DelegateImpl::class.java))
    }

    @Test
    fun identifyExtensionClassesTest() {
        val extensions = mutableSetOf(
            ConfigurationExtension::class.java,
            ExtensionV2DelegateImpl::class.java
        )
        val pair = identifyExtensionClasses(extensions)
        assertEquals(1, pair.first.size)
        assertEquals(1, pair.second.size)
        assertTrue(pair.first.contains(ConfigurationExtension::class.java))
        assertTrue(pair.second.contains(ExtensionV2Sample::class.java))
    }

    @Test
    fun registerExtensionV2Test() {
        receivedXEvents.clear()
        receivedYEvents.clear()
        val eventHub = EventHub()
        val latch = CountDownLatch(1)
        eventHub.registerExtensions(
            setOf(
                ConfigurationExtension::class.java,
                ExtensionV2DelegateImpl::class.java
            )
        ) {
            latch.countDown()
        }
        latch.await()
        Thread.sleep(100)
        // shared state changed -> event hub
        assertEquals(1, TestOnly.dispatchedEvents.size)
        val eventX = Event.Builder(
            "Test Event",
            "TypeX", "SourceX"
        ).build()
        val eventY = Event.Builder(
            "Test Event",
            "TypeY", "SourceY"
        ).build()
        eventHub.dispatch(eventX)
        eventHub.dispatch(eventX)
        eventHub.dispatch(eventX)
        eventHub.dispatch(eventX)
        eventHub.dispatch(eventX)
        eventHub.dispatch(eventY)
        eventHub.dispatch(eventY)
        eventHub.dispatch(eventY)
        Thread.sleep(350)
        assertEquals(3, receivedXEvents.size)
        // Y events should be blocked as the first X event is still processing
        assertEquals(0, receivedYEvents.size)
        Thread.sleep(250)
        assertEquals(5, receivedXEvents.size)
        assertEquals(3, receivedYEvents.size)

    }

}