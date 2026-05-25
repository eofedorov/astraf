package com.astraf.hrgpslogger

enum class GpsSegmentAction {
    APPEND_CURRENT,
    APPEND_BRIDGED,
    START_NEW_SEGMENT,
    IGNORE,
    REJECT,
}
