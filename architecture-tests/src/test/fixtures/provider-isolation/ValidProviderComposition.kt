package io.morgan.mio.di

import io.morgan.mio.data.provider.anilist.AniListProviderFactory
import io.morgan.mio.data.provider.myanimelist.MyAnimeListProviderFactory

internal class ValidProviderComposition(
    private val aniListProviderFactory: AniListProviderFactory,
    private val myAnimeListProviderFactory: MyAnimeListProviderFactory,
)
