package com.astraf.hrgpslogger

data class RoutePreviewPoint(
    val latitude: Double,
    val longitude: Double,
)

fun List<AcceptedGpsPoint>.toRoutePreview(maxPoints: Int = 120): List<RoutePreviewPoint> {
    if (isEmpty()) return emptyList()
    if (size <= maxPoints) {
        return map { RoutePreviewPoint(it.latitude, it.longitude) }
    }
    val source = this
    val pointCount = source.size
    val step = pointCount.toDouble() / maxPoints
    val result = buildList {
        var index = 0.0
        while (index < pointCount) {
            val point = source[index.toInt()]
            add(RoutePreviewPoint(point.latitude, point.longitude))
            index += step
        }
    }
    val sourceLast = source.last()
    return if (result.last().latitude == sourceLast.latitude && result.last().longitude == sourceLast.longitude) {
        result
    } else {
        result + RoutePreviewPoint(sourceLast.latitude, sourceLast.longitude)
    }
}
