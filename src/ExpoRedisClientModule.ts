import { requireNativeModule } from 'expo-modules-core';
import { EventEmitter } from 'expo-modules-core';
import { Platform, ToastAndroid } from 'react-native';
import * as Notifications from 'expo-notifications';

// It loads the native module object from the JSI or falls back to
// the bridge module (from NativeModulesProxy) if the remote debugger is on.
const ExpoRedisClient = requireNativeModule('ExpoRedisClient');

/**
 * Redis connection configuration options
 */
export interface RedisConfig {
  host: string;
  port: number;
  password?: string;
  useSSL?: boolean;
  database?: number;
}

/**
 * Message listener callback type
 */
export type MessageListener = (message: string) => void;

/**
 * Toast notification helper
 * Shows a toast notification on Android and a local notification on iOS
 */
const showToast = (message: string, duration = 'SHORT') => {
  if (Platform.OS === 'android') {
    ToastAndroid.show(message, ToastAndroid.SHORT);
  } else if (Platform.OS === 'ios') {
    // For iOS, use local notifications
    Notifications.scheduleNotificationAsync({
      content: {
        title: 'Redis Client',
        body: message,
      },
      trigger: null, // Show immediately
    }).catch((err: any) => console.error('Failed to show notification:', err));
  }
};

/**
 * Error handler wrapper function
 * Wraps an async function with error handling
 */
const withErrorHandling = async (operation: () => Promise<any>, errorPrefix: string) => {
  try {
    return await operation();
  } catch (error: any) {
    const errorMessage = `${errorPrefix}: ${error?.message || 'Unknown error'}`;
    console.error(errorMessage);
    showToast(errorMessage);
    throw error; // Re-throw to allow caller to handle if needed
  }
};

class RedisClient {
  private emitter: typeof EventEmitter;
  private listeners: Record<string, MessageListener[]>;
  private isConnected: boolean;

  constructor() {
    this.emitter = ExpoRedisClient;
    this.listeners = {};
    this.isConnected = false;
  }

  /**
   * Connect to a Redis server using connection options
   * @param config - Redis connection configuration
   * @returns Connection status message
   */
  async connect(config: RedisConfig): Promise<string> {
    return withErrorHandling(async () => {
      const { host, port, password, useSSL = false, database = 0 } = config;
      
      // Build Redis URL from config
      const protocol = useSSL ? 'rediss' : 'redis';
      const auth = password ? `:${encodeURIComponent(password)}@` : '';
      const db = database ? `/${database}` : '';
      
      const url = `${protocol}://${auth}${host}:${port}${db}`;
      
      const result = await ExpoRedisClient.connect(url);
      this.isConnected = true;
      return result;
    }, 'Redis connection failed');
  }

  /**
   * Connect to a Redis server using a URL string
   * @param url - Redis server URL (e.g., 'redis://localhost:6379')
   * @returns Connection status message
   */
  async connectWithUrl(url: string): Promise<string> {
    return withErrorHandling(async () => {
      const result = await ExpoRedisClient.connect(url);
      this.isConnected = true;
      return result;
    }, 'Redis connection failed');
  }

  /**
   * Disconnect from the Redis server
   * @returns Disconnection status message
   */
  async disconnect(): Promise<string> {
    return withErrorHandling(async () => {
      const result = await ExpoRedisClient.disconnect();
      this.isConnected = false;
      this.listeners = {};
      return result;
    }, 'Redis disconnection failed');
  }

  /**
   * Subscribe to a Redis channel or multiple channels
   * @param channels - Channel name(s) to subscribe to
   * @returns Subscription status message
   */
  async subscribe(channels: string | string[]): Promise<string> {
    return withErrorHandling(async () => {
      if (!this.isConnected) {
        throw new Error('Not connected to Redis. Call connect() first.');
      }

      const channelArray = Array.isArray(channels) ? channels : [channels];
      let result = '';

      for (const channel of channelArray) {
        result = await ExpoRedisClient.subscribe(channel);
        ExpoRedisClient.addListener(channel, (message: string) => {
          if (this.listeners[channel]) {
            this.listeners[channel].forEach(callback => {
              try {
                callback(message);
              } catch (error: any) {
                console.error(`Error in listener for channel ${channel}:`, error);
                showToast(`Listener error: ${error?.message || 'Unknown error'}`);
              }
            });
          }
        });
      }

      return result;
    }, 'Redis subscription failed');
  }

  /**
   * Unsubscribe from a Redis channel or multiple channels
   * @param channels - Channel name(s) to unsubscribe from
   * @returns Unsubscription status message
   */
  async unsubscribe(channels: string | string[]): Promise<string> {
    return withErrorHandling(async () => {
      if (!this.isConnected) {
        throw new Error('Not connected to Redis. Call connect() first.');
      }

      const channelArray = Array.isArray(channels) ? channels : [channels];
      let result = '';

      for (const channel of channelArray) {
        delete this.listeners[channel];
        result = await ExpoRedisClient.unsubscribe(channel);
      }

      return result;
    }, 'Redis unsubscribe failed');
  }

  /**
   * Subscribe to a Redis pattern or multiple patterns
   * @param patterns - Pattern(s) to subscribe to (e.g., 'channel.*')
   * @returns Pattern subscription status message
   */
  async psubscribe(patterns: string | string[]): Promise<string> {
    return withErrorHandling(async () => {
      if (!this.isConnected) {
        throw new Error('Not connected to Redis. Call connect() first.');
      }

      const patternArray = Array.isArray(patterns) ? patterns : [patterns];
      let result = '';

      for (const pattern of patternArray) {
        result = await ExpoRedisClient.psubscribe(pattern);
        ExpoRedisClient.addListener(pattern, (message: string) => {
          if (this.listeners[pattern]) {
            this.listeners[pattern].forEach(callback => {
              try {
                callback(message);
              } catch (error: any) {
                console.error(`Error in listener for pattern ${pattern}:`, error);
                showToast(`Listener error: ${error?.message || 'Unknown error'}`);
              }
            });
          }
        });
      }

      return result;
    }, 'Redis pattern subscription failed');
  }

  /**
   * Unsubscribe from a Redis pattern or multiple patterns
   * @param patterns - Pattern(s) to unsubscribe from
   * @returns Pattern unsubscription status message
   */
  async punsubscribe(patterns: string | string[]): Promise<string> {
    return withErrorHandling(async () => {
      if (!this.isConnected) {
        throw new Error('Not connected to Redis. Call connect() first.');
      }

      const patternArray = Array.isArray(patterns) ? patterns : [patterns];
      let result = '';

      for (const pattern of patternArray) {
        delete this.listeners[pattern];
        result = await ExpoRedisClient.punsubscribe(pattern);
      }

      return result;
    }, 'Redis pattern unsubscribe failed');
  }

  /**
   * Publish a message to a Redis channel
   * @param channel - Channel to publish to
   * @param message - Message to publish
   * @returns Publish status message
   */
  async publish(channel: string, message: string): Promise<string> {
    return withErrorHandling(async () => {
      if (!this.isConnected) {
        throw new Error('Not connected to Redis. Call connect() first.');
      }
      
      return await ExpoRedisClient.publish(channel, message);
    }, 'Redis publish failed');
  }

  /**
   * Add a message listener for a specific channel
   * @param channel - Channel to listen to
   * @param callback - Function to call when a message is received
   * @returns Function to remove the listener
   */
  addListener(channel: string, callback: MessageListener): () => void {
    if (!this.listeners[channel]) {
      this.listeners[channel] = [];
    }
    this.listeners[channel].push(callback);
    
    return () => {
      this.listeners[channel] = this.listeners[channel].filter(cb => cb !== callback);
      if (this.listeners[channel].length === 0) {
        delete this.listeners[channel];
      }
    };
  }

  /**
   * Remove all listeners for a specific channel
   * @param channel - Channel to remove listeners from
   * @returns Status message
   */
  removeAllListeners(channel: string): string {
    try {
      delete this.listeners[channel];
      return ExpoRedisClient.removeAllListeners(channel);
    } catch (error: any) {
      const errorMessage = `Failed to remove listeners: ${error?.message || 'Unknown error'}`;
      console.error(errorMessage);
      showToast(errorMessage);
      return 'Error removing listeners';
    }
  }

  /**
   * Check if client is currently connected to Redis
   * @returns Connection status
   */
  isConnectedToRedis(): boolean {
    return this.isConnected;
  }

  /**
   * Get all active channel subscriptions
   * @returns Array of channel names
   */
  getActiveSubscriptions(): string[] {
    return Object.keys(this.listeners);
  }

  /**
   * Get the current theme (system function example)
   * @returns Current theme
   */
  getTheme(): string {
    try {
      return ExpoRedisClient.getTheme();
    } catch (error) {
      console.error('Failed to get theme:', error);
      return 'system';
    }
  }

  /**
   * Safe wrapper for executing Redis operations with proper error handling
   * This method allows consumers to easily execute Redis operations with
   * built-in error handling
   * @param operation - Async function containing Redis operations
   * @returns Result of the operation or throws a handled error
   */
  async safeExecute<T>(operation: () => Promise<T>): Promise<T> {
    return withErrorHandling(operation, 'Redis operation failed');
  }
}

// Export a singleton instance
export default new RedisClient();