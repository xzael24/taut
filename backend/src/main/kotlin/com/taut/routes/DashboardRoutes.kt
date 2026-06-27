package com.taut.routes

import com.taut.plugins.authorizeRole
import com.taut.service.DashboardService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Dashboard routes — aggregated data for operator and DLH dashboards.
 */
fun Route.dashboardRoutes() {

    // GET /v1/dashboard/operator/{bank_id} — Operator dashboard data
    get("/dashboard/operator/{bank_id}") {
        if (!authorizeRole(call, "OPERATOR", "DLH_ADMIN")) return@get
        val bankId = call.parameters["bank_id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "Bank ID diperlukan."))
            return@get
        }
        val period = call.request.queryParameters["period"] ?: "this_month"
        val fromDate = call.request.queryParameters["from_date"]
        val toDate = call.request.queryParameters["to_date"]

        val result = DashboardService.getOperatorDashboard(bankId, period, fromDate, toDate)
        if (result.containsKey("error")) {
            call.respond(HttpStatusCode.BadRequest, result)
        } else {
            call.respond(HttpStatusCode.OK, result)
        }
    }

    // GET /v1/dashboard/dlh/{kota_id} — DLH dashboard per kota
    get("/dashboard/dlh/{kota_id}") {
        if (!authorizeRole(call, "DLH_ADMIN")) return@get
        val kotaId = call.parameters["kota_id"]?.toIntOrNull() ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "Kota ID diperlukan."))
            return@get
        }
        val fromDate = call.request.queryParameters["from_date"] ?: ""
        val toDate = call.request.queryParameters["to_date"] ?: ""

        if (fromDate.isBlank() || toDate.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "from_date dan to_date wajib diisi."))
            return@get
        }

        val result = DashboardService.getKecamatanDashboard(kotaId, fromDate, toDate)
        call.respond(HttpStatusCode.OK, result)
    }

    // GET /v1/dashboard/operator/{bank_id}/report — Generate monthly report PDF
    get("/dashboard/operator/{bank_id}/report") {
        val bankId = call.parameters["bank_id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "MISSING_PARAM", "message" to "Bank ID diperlukan."))
            return@get
        }
        val year = call.request.queryParameters["year"]?.toIntOrNull() ?: java.time.Year.now().value
        val month = call.request.queryParameters["month"]?.toIntOrNull() ?: java.time.MonthDay.now().monthValue

        val result = DashboardService.generateMonthlyReport(bankId, year, month)
        call.respond(HttpStatusCode.OK, result)
    }
}
