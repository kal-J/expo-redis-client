package expo.modules.redisclient

import android.util.Log
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.modules.EventEmitter
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
import java.time.Duration

// Custom exception for Redis operations
class RedisModuleException(message: String, cause: Throwable? = null) : 
    CodedException(message, "ERR_REDIS_GENERIC", cause)

class ExpoRedisClientModule : Module() {
  private val TAG = "ExpoRedisClient"
  private var redisClient: RedisClient? = null
  private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
  private var pubSubCommands: RedisPubSubCommands<String, String>? = null
  private val CONNECTION_TIMEOUT = Duration.ofSeconds(5)

  // Use EventEmitter for callbacks instead of function references
  private val eventEmitter by lazy { appContext.eventEmitter }
  
  override fun definition() = ModuleDefinition {
    Name("ExpoRedisClient")

    // Default method
    Function("getTheme") { 
      try {
        return@Function "system"
      } catch (e: Exception) {
        Log.e(TAG, "Error in getTheme: ${e.message}")
        throw RedisModuleException("Failed to get theme: ${e.message}")
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
              setTimeout(CONNECTION_TIMEOUT)
            }
          pubSubCommands = pubSubConnection?.sync()
        } catch (e: RedisConnectionException) {
          safeDisconnect()
          Log.e(TAG, "Redis connection error: ${e.message}")
          throw RedisModuleException("Failed to establish Redis connection: ${e.message}", e)
        } catch (e: RedisCommandTimeoutException) {
          safeDisconnect()
          Log.e(TAG, "Redis connection timeout: ${e.message}")
          throw RedisModuleException("Redis connection timed out: ${e.message}", e)
        }
        
        // Set up the pub/sub listener
        pubSubConnection?.addListener(object : RedisPubSubListener<String, String> {
          override fun message(channel: String, message: String) {
            // Use the EventEmitter to send messages to JS
            CoroutineScope(Dispatchers.Main).launch {
              try {
                eventEmitter.emit(channel, mapOf("message" to message))
              } catch (e: Exception) {
                Log.e(TAG, "Error emitting message for channel $channel: ${e.message}")
              }
            }
          }
          
          override fun message(pattern: String, channel: String, message: String) {
            // Pattern subscription messages handled here
            CoroutineScope(Dispatchers.Main).launch {
              try {
                eventEmitter.emit(pattern, mapOf(
                  "channel" to channel,
                  "message" to message
                ))
              } catch (e: Exception) {
                Log.e(TAG, "Error emitting pattern message for $pattern: ${e.message}")
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
        throw RedisModuleException("Failed to connect to Redis: ${e.message}", e)
      }
    }
    
    // Disconnect from Redis server
    AsyncFunction("disconnect") {
      try {
        safeDisconnect()
        return@AsyncFunction "Disconnected from Redis server"
      } catch (e: Exception) {
        Log.e(TAG, "Error during disconnect: ${e.message}")
        throw RedisModuleException("Failed to disconnect from Redis: ${e.message}", e)
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
        throw RedisModuleException("Failed to subscribe to channel $channel: ${e.message}", e)
      } catch (e: Exception) {
        Log.e(TAG, "Error on subscribe: ${e.message}")
        throw RedisModuleException("Failed to subscribe to channel $channel: ${e.message}", e)
      }
    }
    
    // Unsubscribe from a Redis channel
    AsyncFunction("unsubscribe") { channel: String ->
      try {
        ensureConnected()
        pubSubCommands?.unsubscribe(channel)
        return@AsyncFunction "Unsubscribed from channel: $channel"
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on unsubscribe: ${e.message}")
        throw RedisModuleException("Failed to unsubscribe from channel $channel: ${e.message}", e)
      } catch (e: Exception) {
        Log.e(TAG, "Error on unsubscribe: ${e.message}")
        throw RedisModuleException("Failed to unsubscribe from channel $channel: ${e.message}", e)
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
        throw RedisModuleException("Failed to subscribe to pattern $pattern: ${e.message}", e)
      } catch (e: Exception) {
        Log.e(TAG, "Error on pattern subscribe: ${e.message}")
        throw RedisModuleException("Failed to subscribe to pattern $pattern: ${e.message}", e)
      }
    }
    
    // Pattern unsubscribe
    AsyncFunction("punsubscribe") { pattern: String ->
      try {
        ensureConnected()
        pubSubCommands?.punsubscribe(pattern)
        return@AsyncFunction "Unsubscribed from pattern: $pattern"
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on pattern unsubscribe: ${e.message}")
        throw RedisModuleException("Failed to unsubscribe from pattern $pattern: ${e.message}", e)
      } catch (e: Exception) {
        Log.e(TAG, "Error on pattern unsubscribe: ${e.message}")
        throw RedisModuleException("Failed to unsubscribe from pattern $pattern: ${e.message}", e)
      }
    }
    
    // Publish a message to a channel
    AsyncFunction("publish") { channel: String, message: String ->
      try {
        ensureConnected()
        val connection = redisClient?.connect()
          ?.apply { setTimeout(CONNECTION_TIMEOUT) }
        
        val syncCommands = connection?.sync()
        val result = syncCommands?.publish(channel, message)
        connection?.close()
        return@AsyncFunction "Published message to $channel, delivered to $result subscribers"
      } catch (e: RedisCommandTimeoutException) {
        Log.e(TAG, "Redis publish operation timed out: ${e.message}")
        throw RedisModuleException("Publish operation timed out: ${e.message}", e)
      } catch (e: RedisException) {
        Log.e(TAG, "Redis error on publish: ${e.message}")
        throw RedisModuleException("Failed to publish to channel $channel: ${e.message}", e)
      } catch (e: Exception) {
        Log.e(TAG, "Error on publish: ${e.message}")
        throw RedisModuleException("Failed to publish to channel $channel: ${e.message}", e)
      }
    }
    
    // Add event listeners
    Events("*") // Allow any event name for Redis channels
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
    }
  }
  
  // Helper method to check if connected and throw if not
  private fun ensureConnected() {
    if (redisClient == null || pubSubConnection == null) {
      throw RedisModuleException("Not connected to Redis server. Call connect() first.")
    }
  }
}