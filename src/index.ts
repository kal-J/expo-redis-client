// Reexport the native module. On web, it will be resolved to ExpoRedisClientModule.web.ts
// and on native platforms to ExpoRedisClientModule.ts
export { default } from './ExpoRedisClientModule';
export * from  './ExpoRedisClient.types';
