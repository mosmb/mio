package io.morgan.mio

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

public class SyncEmulatorRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, SyncEmulatorApplication::class.java.name, context)
}
