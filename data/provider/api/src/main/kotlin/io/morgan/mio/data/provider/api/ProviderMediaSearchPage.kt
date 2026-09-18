package io.morgan.mio.data.provider.api

public class ProviderMediaSearchPage(
    items: List<ProviderMedia>,
    public val nextCursor: ProviderSearchCursor?,
) {
    public val items: List<ProviderMedia> = items.toList()

    init {
        require(
            this.items
                .map(ProviderMedia::id)
                .distinct()
                .size == this.items.size,
        )
    }
}
