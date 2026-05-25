package com.astraf.hrgpslogger

import android.app.Application

/** Минимальный Application для Robolectric без MapLibre (нативные .so недоступны на JVM). */
class RobolectricTestApp : Application()
