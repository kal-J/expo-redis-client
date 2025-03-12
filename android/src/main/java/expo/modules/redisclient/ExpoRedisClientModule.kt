package expo.modules.redisclient

import android.util.Log
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandTimeoutException
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.RedisException
import io.lettuce.core.pubsub.RedisPubSubListener
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// Custom exceptions for better error handling
class RedisModuleException(message: String, val code: String = "ERR_REDIS_GENERIC") : 
    CodedException(message, code)

class RedisConnectionError(message: String) : 
    RedisModuleException(message, "ERR_REDIS_CONNECTION")

class RedisTimeoutError(message: String) : 
    RedisModuleException(message, "ERR_REDIS_TIMEOUT")

class RedisOperationError(message: String) : 
    RedisModuleException(message, "ERR_REDIS_OPERATION")

class ExpoRedisClientModule : Module() {
  private val TAG = "ExpoRedisClient"
  private var redisClient: RedisClient? = null
  private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
  private var pubSubCommands: RedisPubSubCommands<String, String>? = null
  private val subscribers = ConcurrentHashMap<String, MutableList<(String) -> Unit>>()
  
  // Connection timeout (in seconds)
  private val CONNECTION_TIMEOUT = 5L

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
      try {
        return@Function "system"
      } catch (e: Exception) {
        Log.e(TAG, "Error in getTheme: ${e.message}")
        throw RedisOperationError("Failed to get theme: ${e.message}")
      }
    }

    // Connect to Redis server
    AsyncFunction("connect") { url: String ->
      try {
        // Close previous connection if exists
        safeDisconnect()
        
        // Create a new Redis client
        redisClient = RedisClient.create(url)
        
        // Create a pub/sub connection with timeout
        try {
          pubSubConnection = redisClient?.connectPubSub()
            ?.apply { 
              // Set timeout for commands
              setTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            }
          pubSubCommands = pubSubConnection?.sync()
        } catch (e: RedisConnectionException) {
          safeDisconnect()
          Log.e(TAG, "Redis connection error: ${e.message}")
          throw RedisConnectionError("Failed to establish Redis connection: ${e.message}")
        } catch (e: RedisCommandTimeoutException) {
          safeDisconnect()
          Log.e(TAG, "Redis connection timeout: ${e.message}")
          throw RedisTimeoutError("Redis connection timed out: ${e.message}")
        }
        
        // Set up the pub/sub listener
        pubSubConnection?.addListener(object : RedisPubSubListener<String, String> {
          override fun message(channel: String, message: String) {
            // Notify all subscribers for this channel
            subscribers[channel]?.forEach { callback ->
              CoroutineScope(Dispatchers.Main).launch {
                try {
                  callback(message)
                } catch (e: Exception) {
                  Log.e(TAG, "Error in message listener for channel $channel: ${e.message}")
                }
              }
            }
          }
          
          override fun message(pattern: String, channel: String, message: String) {
            // Pattern subscription messages handled here
            subscribers[pattern]?.forEach { callback ->
              CoroutineScope(Dispatchers.Main).launch {
                try {
                  callback("$channel:$message")
                } catch (e: Exception) {
                  Log.e(TAG, "Error in pattern message listener for $pattern: ${e.message}")
                }
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
      } catch (e: RedisModuleException) {
        // Re-throw custom exceptions
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Error connecting to Redis: ${e.message}")
        throw RedisConnectionError("Failed to connect to Redis: ${e.message}")
      }
    }
    
    // Disconnect from Redis server
    AsyncFunction("disconnect") {
      try {
        safeDisconnect()
        return@AsyncFunction "Disconnected from Redis server"
      } catch (e: Exception) {
        Log.e(TAG, "Error during disconnect: ${e.message}")
        throw RedisOperationError("Failed to disconnect from Redis: ${e.message}")
      }
    }
    
    // Subscribe to a Redis channel
    AsyncFunction("subscribe") { channel: String ->
      try {
        ensureConnected()
        pubSubCommands?.subscribe(channel)
        return@AsyncFunction "Subscribed to channel: $channel"
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on subscribe: ${e.message}")
        throw RedisOperationError("Failed to subscribe to channel $channel: ${e.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Error on subscribe: ${e.message}")
        throw RedisOperationError("Failed to subscribe to channel $channel: ${e.message}")
      }
    }
    
    // Unsubscribe from a Redis channel
    AsyncFunction("unsubscribe") { channel: String ->
      try {
        ensureConnected()
        pubSubCommands?.unsubscribe(channel)
        subscribers.remove(channel)
        return@AsyncFunction "Unsubscribed from channel: $channel"
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on unsubscribe: ${e.message}")
        throw RedisOperationError("Failed to unsubscribe from channel $channel: ${e.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Error on unsubscribe: ${e.message}")
        throw RedisOperationError("Failed to unsubscribe from channel $channel: ${e.message}")
      }
    }
    
    // Pattern subscribe
    AsyncFunction("psubscribe") { pattern: String ->
      try {
        ensureConnected()
        pubSubCommands?.psubscribe(pattern)
        return@AsyncFunction "Subscribed to pattern: $pattern"
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on pattern subscribe: ${e.message}")
        throw RedisOperationError("Failed to subscribe to pattern $pattern: ${e.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Error on pattern subscribe: ${e.message}")
        throw RedisOperationError("Failed to subscribe to pattern $pattern: ${e.message}")
      }
    }
    
    // Pattern unsubscribe
    AsyncFunction("punsubscribe") { pattern: String ->
      try {
        ensureConnected()
        pubSubCommands?.punsubscribe(pattern)
        subscribers.remove(pattern)
        return@AsyncFunction "Unsubscribed from pattern: $pattern"
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on pattern unsubscribe: ${e.message}")
        throw RedisOperationError("Failed to unsubscribe from pattern $pattern: ${e.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Error on pattern unsubscribe: ${e.message}")
        throw RedisOperationError("Failed to unsubscribe from pattern $pattern: ${e.message}")
      }
    }
    
    // Publish a message to a channel
    AsyncFunction("publish") { channel: String, message: String ->
      try {
        ensureConnected()
        val connection = redisClient?.connect()
          ?.apply { setTimeout(CONNECTION_TIMEOUT, TimeUnit.SECONDS) }
        
        val syncCommands = connection?.sync()
        val result = syncCommands?.publish(channel, message)
        connection?.close()
        return@AsyncFunction "Published message to $channel, delivered to $result subscribers"
      } catch (e: RedisCommandTimeoutException) {
        Log.e(TAG, "Redis publish operation timed out: ${e.message}")
        throw RedisTimeoutError("Publish operation timed out: ${e.message}")
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on publish: ${e.message}")
        throw RedisOperationError("Failed to publish to channel $channel: ${e.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Error on publish: ${e.message}")
        throw RedisOperationError("Failed to publish to channel $channel: ${e.message}")
      }
    }
    
    // Add an event listener for a specific channel
    Function("addListener") { channel: String, callback: (String) -> Unit ->
      try {
        if (!subscribers.containsKey(channel)) {
          subscribers[channel] = mutableListOf()
        }
        subscribers[channel]?.add(callback)
        return@Function "Added listener for channel: $channel"
      } catch (e: Exception) {
        Log.e(TAG, "Error adding listener: ${e.message}")
        throw RedisOperationError("Failed to add listener for channel $channel: ${e.message}")
      }
    }
    
    // Remove all event listeners for a specific channel
    Function("removeAllListeners") { channel: String ->
      try {
        subscribers.remove(channel)
        return@Function "Removed all listeners for channel: $channel"
      } catch (e: Exception) {
        Log.e(TAG, "Error removing listeners: ${e.message}")
        throw RedisOperationError("Failed to remove listeners for channel $channel: ${e.message}")
      }
    }
  }
  
  // Helper method to disconnect from Redis safely
  private fun safeDisconnect() {
    try {
      pubSubConnection?.close()
      redisClient?.shutdown()
    } catch (e: Exception) {
      Log.e(TAG, "Error during disconnect: ${e.message}")
    } finally {
      pubSubConnection = null
      pubSubCommands = null
      redisClient = null
      subscribers.clear()
    }
  }
  
  // Helper method to check if connected and throw if not
  private fun ensureConnected() {
    if (redisClient == null || pubSubConnection == null) {
      throw RedisConnectionError("Not connected to Redis server. Call connect() first.")
    }
  }
}