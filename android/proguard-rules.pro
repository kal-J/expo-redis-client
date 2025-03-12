# Lettuce and Netty
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Netty related
-keepclassmembers class io.netty.** { *; }
-keepnames class io.netty.** { *; }
-dontwarn io.netty.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# Avoid issues with epoll
-dontwarn io.netty.channel.epoll.*
-dontwarn io.netty.channel.unix.*
-dontwarn io.netty.util.internal.logging.*

# Redis client specifics
-keep class io.lettuce.** { *; }
-dontwarn io.lettuce.**