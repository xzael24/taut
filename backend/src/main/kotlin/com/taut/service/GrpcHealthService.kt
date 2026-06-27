package com.taut.service

import io.grpc.BindableService
import io.grpc.health.v1.HealthCheckResponse
import io.grpc.protobuf.services.HealthStatusManager
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provides the standard gRPC health-check service (grpc.health.v1.Health)
 * and tracks whether the gRPC server is actually serving.
 *
 * Usage:
 *   - Register [service] with the gRPC [ServerBuilder].
 *   - Call [markHealthy] when the server starts successfully.
 *   - Call [markNotHealthy] when the server is shutting down or failing.
 *   - The HTTP `/health` endpoint calls [isHealthy] for a real check.
 */
object GrpcHealthService {

    private val log = LoggerFactory.getLogger(GrpcHealthService::class.java)

    // The default service name "" covers "overall server health".
    private const val OVERALL_SERVICE = ""

    /** The underlying gRPC health status manager. */
    private val healthStatusManager: HealthStatusManager = HealthStatusManager()

    /** Internal flag tracking whether the gRPC server is serving. */
    private val serving = AtomicBoolean(false)

    /**
     * The health service to register with [io.grpc.ServerBuilder].
     * This implements the standard gRPC health-check protocol so that
     * external health probes (e.g. Kubernetes, load balancers) can
     * query gRPC service health directly.
     */
    val service: BindableService
        get() = healthStatusManager.healthService

    /** Mark the server as SERVING (healthy). */
    fun markHealthy() {
        serving.set(true)
        healthStatusManager.setStatus(OVERALL_SERVICE, HealthCheckResponse.ServingStatus.SERVING)
        log.info("[gRPC Health] Status set to SERVING")
    }

    /** Mark the server as NOT_SERVING (unhealthy). */
    fun markNotHealthy() {
        serving.set(false)
        healthStatusManager.setStatus(OVERALL_SERVICE, HealthCheckResponse.ServingStatus.NOT_SERVING)
        log.info("[gRPC Health] Status set to NOT_SERVING")
    }

    /**
     * Check whether the gRPC server is currently healthy.
     *
     * Called by the HTTP `/health` endpoint so it reports real gRPC
     * health instead of a hardcoded `true`.
     */
    fun isHealthy(): Boolean = serving.get()
}
