package org.blitzortung.android.data.provider.standard

import org.blitzortung.android.data.Parameters
import org.blitzortung.android.data.provider.GLOBAL_REGION
import org.blitzortung.android.data.provider.LOCAL_REGION
import org.blitzortung.android.jsonrpc.JsonRpcClient
import org.json.JSONObject
import java.net.URL

class JsonRpcData(
    private val client: JsonRpcClient,
    private val serviceUrl: URL,
) {

    fun requestData(parameters: Parameters) : JSONObject {
        val intervalDuration = parameters.intervalDuration
        val intervalOffset = parameters.intervalOffset
        val rasterBaselength = parameters.rasterBaselength
        val countThreshold = parameters.countThreshold
        val region = parameters.region
        val localReference = parameters.localReference

        return when (region) {
            GLOBAL_REGION -> {
                with(
                    client.call(
                        serviceUrl,
                        "get_global_strikes_grid",
                        intervalDuration,
                        rasterBaselength,
                        intervalOffset,
                        countThreshold
                    )
                ) {
                    put("y1", 0.0)
                    put("x0", 0.0)
                }
            }
            LOCAL_REGION -> {
                client.call(
                    serviceUrl,
                    "get_local_strikes_grid",
                    localReference!!.x,
                    localReference.y,
                    rasterBaselength,
                    intervalDuration,
                    intervalOffset,
                    countThreshold
                )
            }
            else -> {
                client.call(
                    serviceUrl,
                    "get_strikes_grid",
                    intervalDuration,
                    rasterBaselength,
                    intervalOffset,
                    region,
                    countThreshold
                )
            }
        }

    }
}