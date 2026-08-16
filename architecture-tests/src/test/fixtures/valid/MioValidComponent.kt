package io.morgan.mio.core.designsystem.component

public data class MioValidComponentModel(
    public val enabled: Boolean,
)

public fun MioValidComponent(model: MioValidComponentModel) {
    model
}
