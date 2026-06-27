package com.taut.app.data.sync

import com.taut.app.BuildConfig
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * Provides gRPC channel and stubs for the TAUT SyncService.
 * Created lazily — not Hilt-injected (avoids KSP classpath issues with generated protobuf).
 *
 * Security: Always uses TLS (TLS 1.3 via system CA trust store). No plaintext allowed.
 */
object GrpcClientProvider {

    private const val DEFAULT_HOST = "10.0.2.2"
    private const val DEFAULT_PORT = 9000
    private const val MAX_INBOUND_MESSAGE_SIZE = 4 * 1024 * 1024 // 4 MB

    @Volatile
    private var channel: ManagedChannel? = null

    fun getChannel(): ManagedChannel {
        return channel ?: synchronized(this) {
            channel ?: run {
                val host = BuildConfig.SYNC_HOST.ifEmpty { DEFAULT_HOST }
                val port = if (BuildConfig.SYNC_PORT > 0) BuildConfig.SYNC_PORT else DEFAULT_PORT
                ManagedChannelBuilder.forAddress(host, port)
                    .apply {
                        if (BuildConfig.DEBUG) {
                            usePlaintext()
                        } else {
                            useTransportSecurity()
                        }
                    }
                    .idleTimeout(5, TimeUnit.MINUTES)
                    .keepAliveTime(60, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
                    .build()
                    .also { channel = it }
            }
        }
    }

    /**
     * Gracefully shuts down the gRPC channel, waiting up to 5 seconds
     * for in-flight RPCs to complete before terminating.
     */
    fun shutdown() {
        channel?.let { ch ->
            channel = null
            try {
                ch.shutdown()
                if (!ch.awaitTermination(5, TimeUnit.SECONDS)) {
                    ch.shutdownNow()
                    ch.awaitTermination(2, TimeUnit.SECONDS)
                }
            } catch (_: InterruptedException) {
                ch.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
}