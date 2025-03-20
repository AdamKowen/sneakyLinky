package com.example.sneakylinky

class FakeApiService : ApiLinkService {
    override suspend fun checkUrl(request: Map<String, String>): ApiResponse {
        // מחזיר קישור לא בטוח כדי לבדוק את התגובה ב-UI
        return ApiResponse(
            status = "unsafe",
            message = "Test: Link marked as unsafe."
        )
    }
}
