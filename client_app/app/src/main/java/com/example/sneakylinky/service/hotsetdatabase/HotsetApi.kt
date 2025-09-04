
package com.example.sneakylinky.service.hotsetdatabase

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Path

data class HotsetRecordDto(
    val whiteSnapshot: List<String>?,
    val blackSnapshot: List<String>?,
    val whiteAdd: List<String>,
    val whiteRemove: List<String>,
    val blackAdd: List<String>,
    val blackRemove: List<String>
)

data class HotsetEnvelope(val record: HotsetRecordDto)

interface HotsetApi {
    // Use relative paths (no leading slash) so baseUrl ".../v1/" is honored
    @HEAD("DomainHotset/latest-version")
    suspend fun headLatestVersion(): Response<Void>  // <-- Void, not Unit

    @GET("DomainHotset/{version}")
    suspend fun getDeltaOrSnapshot(@Path("version") version: Int): HotsetEnvelope
}
