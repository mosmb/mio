package io.morgan.mio.data.account

import kotlin.time.Clock

class DirectSystemClockInData {
    val now = Clock.System.now()
}
