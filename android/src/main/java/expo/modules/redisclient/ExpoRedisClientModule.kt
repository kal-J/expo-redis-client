package expo.modules.redisclient

import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ExpoRedisClientModule : Module() {
  private val TAG = "ExpoRedisClient"
  private var redisClient: RedisClient? = null
  private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
  private var pubSubCommands: RedisPubSubCommands<String, String>? = null
  private val subscribers = ConcurrentHashMap<String, MutableList<(String) -> Unit>>()

  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  override fun definition() = ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('ExpoRedisClient')` in JavaScript.
    Name("ExpoRedisClient")

    // Default method
    Function("getTheme") { 
      return@Function "system"
    }

    // Connect to Redis server
    AsyncFunction("connect") { url: String ->
      try {
        // Close previous connection if exists
        disconnect()
        
        // Create a new Redis client
        redisClient = RedisClient.create(url)
        
        // Create a pub/sub connection
        pubSubConnection = redisClient?.connectPubSub()
        pubSubCommands = pubSubConnection?.sync()
        
        // Set up the pub/sub listener
        pubSubConnection?.addListener(object : RedisPubSubListener<String, String> {
          override fun message(channel: String, message: String) {
            // Notify all subscribers for this channel
            subscribers[channel]?.forEach { callback ->
              CoroutineScope(Dispatchers.Main).launch {
                callback(message)
              }
            }
          }
          
          override fun message(pattern: String, channel: String, message: String) {
            // Pattern subscription messages handled here
            subscribers[pattern]?.forEach { callback ->
              CoroutineScope(Dispatchers.Main).launch {
                callback("$channel:$message")
              }
            }
          }
          
          override fun psubscribed(pattern: String, count: Long) {
            Log.d(TAG, "Pattern subscribed: $pattern, count: $count")
          }
          
          override fun punsubscribed(pattern: String, count: Long) {
            Log.d(TAG, "Pattern unsubscribed: $pattern, count: $count")
          }
          
          override fun subscribed(channel: String, count: Long) {
            Log.d(TAG, "Subscribed to channel: $channel, count: $count")
          }
          
          override fun unsubscribed(channel: String, count: Long) {
            Log.d(TAG, "Unsubscribed from channel: $channel, count: $count")
          }
        })
        
        return@AsyncFunction "Connected to Redis server"
      } catch (e: Exception) {
        throw Error("Failed to connect to Redis: ${e.message}")
      }
    }
    
    // Disconnect from Redis server
    AsyncFunction("disconnect") {
      disconnect()
      return@AsyncFunction "Disconnected from Redis server"
    }
    
    // Subscribe to a Redis channel
    AsyncFunction("subscribe") { channel: String ->
      try {
        pubSubCommands?.subscribe(channel)
        return@AsyncFunction "Subscribed to channel: $channel"
      } catch (e: Exception) {
        throw Error("Failed to subscribe to channel $channel: ${e.message}")
      }
    }
    
    // Unsubscribe from a Redis channel
    AsyncFunction("unsubscribe") { channel: String ->
      try {
        pubSubCommands?.unsubscribe(channel)
        subscribers.remove(channel)
        return@AsyncFunction "Unsubscribed from channel: $channel"
      } catch (e: Exception) {
        throw Error("Failed to unsubscribe from channel $channel: ${e.message}")
      }
    }
    
    // Pattern subscribe
    AsyncFunction("psubscribe") { pattern: String ->
      try {
        pubSubCommands?.psubscribe(pattern)
        return@AsyncFunction "Subscribed to pattern: $pattern"
      } catch (e: Exception) {
        throw Error("Failed to subscribe to pattern $pattern: ${e.message}")
      }
    }
    
    // Pattern unsubscribe
    AsyncFunction("punsubscribe") { pattern: String ->
      try {
        pubSubCommands?.punsubscribe(pattern)
        subscribers.remove(pattern)
        return@AsyncFunction "Unsubscribed from pattern: $pattern"
      } catch (e: Exception) {
        throw Error("Failed to unsubscribe from pattern $pattern: ${e.message}")
      }
    }
    
    // Publish a message to a channel
    AsyncFunction("publish") { channel: String, message: String ->
      try {
        val connection = redisClient?.connect()
        val syncCommands = connection?.sync()
        val result = syncCommands?.publish(channel, message)
        connection?.close()
        return@AsyncFunction "Published message to $channel, delivered to $result subscribers"
      } catch (e: Exception) {
        throw Error("Failed to publish to channel $channel: ${e.message}")
      }
    }
    
    // Add an event listener for a specific channel
    Function("addListener") { channel: String, callback: (String) -> Unit ->
      if (!subscribers.containsKey(channel)) {
        subscribers[channel] = mutableListOf()
      }
      subscribers[channel]?.add(callback)
      return@Function "Added listener for channel: $channel"
    }
    
    // Remove all event listeners for a specific channel
    Function("removeAllListeners") { channel: String ->
      subscribers.remove(channel)
      return@Function "Removed all listeners for channel: $channel"
    }
  }
  
  // Helper method to disconnect from Redis
  private fun disconnect() {
    try {
      pubSubConnection?.close()
      redisClient?.shutdown()
      
      pubSubConnection = null
      pubSubCommands = null
      redisClient = null
      subscribers.clear()
    } catch (e: Exception) {
      Log.e(TAG, "Error during disconnect: ${e.message}")
    }
  }
}