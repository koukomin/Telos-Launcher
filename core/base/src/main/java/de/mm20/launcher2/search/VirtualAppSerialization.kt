package de.mm20.launcher2.search

/**
 * Serializer for synthetic Telos "virtual apps" (Phone, Messages, Store, ...). They are not backed
 * by the PackageManager, so the only thing that needs to be persisted is their key; the
 * [VirtualAppDeserializer] resolves it again through the registered [VirtualAppProvider]s.
 *
 * Without this, [NullSerializer] made every save (pin, dock, replace) of a virtual app a silent no-op.
 */
class VirtualAppSerializer : SearchableSerializer {
    override fun serialize(searchable: SavableSearchable): String? {
        return searchable.key.takeIf { it.isNotEmpty() }
    }

    override val typePrefix: String
        get() = "virtualapp"
}

class VirtualAppDeserializer(
    private val domain: String,
    private val providers: () -> List<VirtualAppProvider>,
) : SearchableDeserializer {
    override suspend fun deserialize(serialized: String): SavableSearchable? {
        if (serialized.isEmpty()) return null
        return providers()
            .asSequence()
            .flatMap { it.getVirtualApps().asSequence() }
            .firstOrNull { it.domain == domain && it.key == serialized }
    }
}
