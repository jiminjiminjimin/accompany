package com.example.pathreport

/** A manual app lock stays visible until the user presses its entry button. */
internal fun canEnterCareApp(secure: Boolean, deviceLocked: Boolean, manuallyLocked: Boolean): Boolean =
    !manuallyLocked && (!secure || !deviceLocked)
