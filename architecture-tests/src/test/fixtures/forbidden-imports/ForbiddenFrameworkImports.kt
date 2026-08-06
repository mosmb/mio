package io.morgan.mio.core.common

import androidx.paging.PagingData
import com.squareup.moshi.JsonClass
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.Serializable
import org.koin.core.component.KoinComponent

@JsonClass(generateAdapter = true)
class ForbiddenFrameworkImports : Serializable, KoinComponent
