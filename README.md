# Expo Redis Client

A native Redis client module for Expo and React Native applications on Android. This module provides a simple and efficient way to interact with Redis servers, supporting pub/sub, pattern subscriptions, and more.

> **Note:** Currently, this module only supports Android platforms. iOS support is planned for future releases.

## Features

- ✅ Connect to Redis servers with secure authentication
- ✅ Publish and subscribe to Redis channels
- ✅ Pattern-based subscription support
- ✅ Robust error handling with helpful error messages
- ✅ Android toast notifications for errors
- ✅ TypeScript definitions included
- ✅ Works in Expo managed and bare workflow

## Installation

```bash
# Using npm
npm install expo-redis-client

# Using Yarn
yarn add expo-redis-client

# Using Expo
expo install expo-redis-client
```

For Expo managed workflow projects, you need to rebuild your app:

```bash
expo prebuild
```

## Usage

### Connecting to Redis

```typescript
import RedisClient, { RedisConfig } from 'expo-redis-client';

// Connect using configuration object
const config: RedisConfig = {
  host: 'your-redis-server.com',
  port: 6379,
  password: 'your-password', // Optional
  useSSL: true, // Optional, defaults to false
  database: 0 // Optional, defaults to 0
};

async function connectToRedis() {
  try {
    await RedisClient.connect(config);
    console.log('Connected to Redis!');
  } catch (error) {
    console.error('Failed to connect:', error);
  }
}

// Or connect using a URL string
async function connectWithUrl() {
  try {
    await RedisClient.connectWithUrl('redis://username:password@your-redis-server.com:6379/0');
    console.log('Connected to Redis!');
  } catch (error) {
    console.error('Failed to connect:', error);
  }
}
```

### Publishing Messages

```typescript
async function publishMessage() {
  try {
    const result = await RedisClient.publish('channel-name', 'Hello from Expo!');
    console.log(result); // Shows delivery status
  } catch (error) {
    console.error('Failed to publish:', error);
  }
}
```

### Subscribing to Channels

```typescript
async function subscribeToChannel() {
  try {
    // Subscribe to one or more channels
    await RedisClient.subscribe('channel-name');
    // or multiple channels
    await RedisClient.subscribe(['channel-1', 'channel-2']);
    
    // Add a listener for messages
    const removeListener = RedisClient.addMessageListener((channel, message) => {
      console.log(`Received message from ${channel}:`, message);
    });
    
    // Later, when you want to stop listening
    removeListener();
    
    // Unsubscribe when done
    await RedisClient.unsubscribe('channel-name');
  } catch (error) {
    console.error('Subscription error:', error);
  }
}
```

### Pattern Subscriptions

```typescript
async function subscribeToPattern() {
  try {
    // Subscribe to a pattern (e.g., all channels starting with "user:")
    await RedisClient.psubscribe('user:*');
    
    // Add a pattern message listener
    const removeListener = RedisClient.addPatternMessageListener((pattern, channel, message) => {
      console.log(`Pattern ${pattern} matched channel ${channel}:`, message);
    });
    
    // Later, when you want to stop listening
    removeListener();
    
    // Unsubscribe when done
    await RedisClient.punsubscribe('user:*');
  } catch (error) {
    console.error('Pattern subscription error:', error);
  }
}
```

### Disconnecting

```typescript
async function disconnect() {
  try {
    await RedisClient.disconnect();
    console.log('Disconnected from Redis');
  } catch (error) {
    console.error('Disconnect error:', error);
  }
}
```

### Safe Execution Wrapper

```typescript
// Use the safeExecute helper for custom operations with built-in error handling
async function doSomethingWithRedis() {
  try {
    const result = await RedisClient.safeExecute(async () => {
      await RedisClient.subscribe('my-channel');
      return await RedisClient.publish('my-channel', 'Hello');
    });
    console.log('Operation completed:', result);
  } catch (error) {
    // Error already handled with toast notification
    // Additional error handling if needed
  }
}
```

## API Reference

### Configuration

```typescript
interface RedisConfig {
  host: string;
  port: number;
  password?: string;
  useSSL?: boolean;
  database?: number;
}
```

### Methods

| Method | Description |
|--------|-------------|
| `connect(config: RedisConfig)` | Connect to a Redis server using a configuration object |
| `connectWithUrl(url: string)` | Connect to a Redis server using a URL string |
| `disconnect()` | Disconnect from the Redis server |
| `publish(channel: string, message: string)` | Publish a message to a Redis channel |
| `subscribe(channels: string \| string[])` | Subscribe to one or more Redis channels |
| `unsubscribe(channels: string \| string[])` | Unsubscribe from one or more Redis channels |
| `psubscribe(patterns: string \| string[])` | Subscribe to one or more Redis patterns |
| `punsubscribe(patterns: string \| string[])` | Unsubscribe from one or more Redis patterns |
| `addMessageListener(callback)` | Add a listener for channel messages |
| `addPatternMessageListener(callback)` | Add a listener for pattern messages |
| `removeAllMessageListeners()` | Remove all channel message listeners |
| `removeAllPatternMessageListeners()` | Remove all pattern message listeners |
| `isConnectedToRedis()` | Check if client is currently connected |
| `safeExecute(operation)` | Safely execute a Redis operation with error handling |

## Error Handling

The client includes robust error handling with toast notifications. Errors will display as Android toast messages.

You can also handle errors manually:

```typescript
try {
  await RedisClient.connect(config);
} catch (error) {
  if (error instanceof Error) {
    // Handle specific error
    console.error('Connection error:', error.message);
  }
}
```

## Requirements

- Expo SDK 45 or later
- Android API level 21 or later

## Under the Hood

This module uses the [Lettuce](https://lettuce.io/) Redis client for Android.

## License

MIT