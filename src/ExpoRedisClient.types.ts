import type { StyleProp, ViewStyle } from 'react-native';

export type OnLoadEventPayload = {
    url: string;
};

export type ExpoRedisClientModuleEvents = {
    onChange: (params: ChangeEventPayload) => void;
};

export type ChangeEventPayload = {
    value: string;
};

export type ExpoRedisClientViewProps = {
    url: string;
    onLoad: (event: { nativeEvent: OnLoadEventPayload }) => void;
    style?: StyleProp<ViewStyle>;
};

export interface Message {
    id: string;
    channel: string;
    text: string;
    timestamp: number;
}

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
 * Redis Client Module interface
 */
export interface RedisClientInterface {
    /**
     * Connect to a Redis server using connection options
     * @param config - Redis connection configuration
     * @returns Connection status message
     */
    connect(config: RedisConfig): Promise<string>;

    /**
     * Connect to a Redis server using a URL string
     * @param url - Redis server URL (e.g., 'redis://localhost:6379')
     * @returns Connection status message
     */
    connectWithUrl(url: string): Promise<string>;

    /**
     * Disconnect from the Redis server
     * @returns Disconnection status message
     */
    disconnect(): Promise<string>;

    /**
     * Subscribe to a Redis channel or multiple channels
     * @param channels - Channel name(s) to subscribe to
     * @returns Subscription status message
     */
    subscribe(channels: string | string[]): Promise<string>;

    /**
     * Unsubscribe from a Redis channel or multiple channels
     * @param channels - Channel name(s) to unsubscribe from
     * @returns Unsubscription status message
     */
    unsubscribe(channels: string | string[]): Promise<string>;

    /**
     * Subscribe to a Redis pattern or multiple patterns
     * @param patterns - Pattern(s) to subscribe to (e.g., 'channel.*')
     * @returns Pattern subscription status message
     */
    psubscribe(patterns: string | string[]): Promise<string>;

    /**
     * Unsubscribe from a Redis pattern or multiple patterns
     * @param patterns - Pattern(s) to unsubscribe from
     * @returns Pattern unsubscription status message
     */
    punsubscribe(patterns: string | string[]): Promise<string>;

    /**
     * Publish a message to a Redis channel
     * @param channel - Channel to publish to
     * @param message - Message to publish
     * @returns Publish status message
     */
    publish(channel: string, message: string): Promise<string>;

    /**
     * Add a message listener for a specific channel
     * @param channel - Channel to listen to
     * @param callback - Function to call when a message is received
     * @returns Function to remove the listener
     */
    addListener(channel: string, callback: MessageListener): () => void;

    /**
     * Remove all listeners for a specific channel
     * @param channel - Channel to remove listeners from
     * @returns Status message
     */
    removeAllListeners(channel: string): string;

    /**
     * Check if client is currently connected to Redis
     * @returns Connection status
     */
    isConnectedToRedis(): boolean;

    /**
     * Get all active channel subscriptions
     * @returns Array of channel names
     */
    getActiveSubscriptions(): string[];

    /**
     * Get the current theme (system function example)
     * @returns Current theme
     */
    getTheme(): string;
}