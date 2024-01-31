package crackers.kobots.mqtt

import crackers.kobots.app.AppCommon.mqttClient
import crackers.kobots.mqtt.homeassistant.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KobotLightTest : FunSpec(testGuts())

private fun testGuts(): FunSpec.() -> Unit = {
    lateinit var broker: GenericContainer<*>

    beforeSpec {
        broker = GenericContainer("eclipse-mosquitto").apply {
            setCommand("mosquitto -c /mosquitto-no-auth.conf")
            withLabel("name", "mosquitto")
            withExposedPorts(1883)
            withNetwork(Network.SHARED)
            start()
            println("Staring broker")
            waitingFor(HostPortWaitStrategy().forPorts(1883))
        }
        val mappedPort = broker.getMappedPort(1883)
        System.setProperty("mqtt.broker", "tcp://localhost:$mappedPort")
        println("Broker running on port $mappedPort")
    }

    afterSpec {
        println("Stopping broker")
        broker.stop()
    }

    xtest("Setup a single light and verify discovery message") {
        val controller = mockk<LightController>()
        val initialState = LightState(brightness = 255, color = LightColor(255, 255, 255))
        every { controller.current() } returns initialState

        // listen for discovery messages
        val discoveryReceived = CountDownLatch(1)
        var discoveryMessage: JSONObject? = null
        var discoveryTopic: String? = null
        var initialStatusReceived: JSONObject? = null
        val initialStatusReceivedLatch = CountDownLatch(1)

        mqttClient.addTopicListener("homeassistant/+/+/config") { topic, message ->
            discoveryMessage = JSONObject(message.decodeToString())
            discoveryTopic = topic
            discoveryReceived.countDown()
        }
        mqttClient.addTopicListener("kobots_ha/test_light/state") { _, message ->
            initialStatusReceived = JSONObject(message.decodeToString())
            initialStatusReceivedLatch.countDown()
        }

        val light = KobotLight("test_light", controller, "Test Light", DeviceIdentifier("foo", "foo"))
        light.start()

        discoveryReceived.await(1, TimeUnit.SECONDS) shouldBe true
        discoveryTopic shouldBe "homeassistant/light/test_light/config"
        discoveryMessage shouldNotBe null

        // load the schema from the data file and compare it to the discovery message
        val schema = JSONObject(javaClass.getResource("/light_schema.json")!!.readText())
        discoveryMessage shouldNotBe null
        testJSONObjects(schema, discoveryMessage!!)

        initialStatusReceivedLatch.await(20, TimeUnit.MILLISECONDS) shouldBe true
        initialStatusReceived shouldNotBe null
        testJSONObjects(initialState.json(), initialStatusReceived!!)
    }
}

private fun testJSONObjects(expected: JSONObject, actual: JSONObject) {
    expected.keySet() shouldBe actual.keySet()
    expected.keySet().forEach { key ->
        actual.has(key) shouldBe true
        when (val expectedSchemaValue = expected.get(key)) {
            is JSONArray -> {
                // compare the arrays as sets
                val expectedSet = expectedSchemaValue.toList().toSet()
                val actualSet = actual.getJSONArray(key).toList().toSet()
                actualSet shouldBe expectedSet
            }

            is JSONObject -> testJSONObjects(expectedSchemaValue, actual.getJSONObject(key))
            else -> actual.get(key) shouldBe expectedSchemaValue
        }
    }
}
