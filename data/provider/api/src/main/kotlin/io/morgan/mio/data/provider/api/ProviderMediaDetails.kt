package io.morgan.mio.data.provider.api

public class ProviderMediaDetails(
    public val provider: ProviderKey,
    public val root: ProviderMedia,
    related: List<ProviderMedia>,
) {
    public val related: List<ProviderMedia> = related.toList()

    init {
        require(
            this.related
                .map(ProviderMedia::id)
                .distinct()
                .size == this.related.size,
        )
        require(this.related.none { media -> media.id == root.id })
        require(root.relatedMediaIds == this.related.map(ProviderMedia::id))
    }
}
