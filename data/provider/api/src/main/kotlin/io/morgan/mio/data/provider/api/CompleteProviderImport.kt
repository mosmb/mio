package io.morgan.mio.data.provider.api

public class CompleteProviderImport(
    public val provider: ProviderKey,
    public val accountId: ProviderAccountId,
    media: List<ProviderMedia>,
    entries: List<ProviderCollectionEntry>,
) {
    public val media: List<ProviderMedia> = media.toList()
    public val entries: List<ProviderCollectionEntry> = entries.toList()

    init {
        val mediaIds = this.media.map { item -> item.id }
        val entryIds = this.entries.map { entry -> entry.id }
        require(mediaIds.size == mediaIds.toSet().size)
        require(entryIds.size == entryIds.toSet().size)
        require(
            this.entries
                .map { entry -> entry.mediaId }
                .toSet()
                .size == this.entries.size,
        )
        require(this.entries.all { entry -> entry.accountId == accountId })
        require(this.entries.all { entry -> entry.mediaId in mediaIds })

        val mediaWithEntries = this.entries.map { entry -> entry.mediaId }.toSet()
        val mediaWithoutEntries = mediaIds.filterNot(mediaWithEntries::contains)
        require(
            mediaWithoutEntries.all { missingMediaId ->
                this.media.any { owner ->
                    owner.id != missingMediaId && missingMediaId in owner.relatedMediaIds
                }
            },
        )
    }
}
