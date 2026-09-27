package io.github.edwardg117.renderable.core;

/**
 * Something a mod does in the frame that Renderable can name by its owner (spec addendum §P5.A3 to §P5.A5):
 * the mod's handlers for one event class, one HUD layer, or one packet or payload type. All three roll up by
 * mod id into the Mods tab, so they share {@link Category#MOD} and use the mod id as the type id.
 *
 * @param kind  what was timed
 * @param modId the owning mod: the mod whose file registered the handler, or a layer's or packet's namespace
 * @param name  the event's fully-qualified class name, or the layer's or packet's full id
 */
public record ModKey(Kind kind, String modId, String name) implements ProfileKey {

    /** What a {@link ModKey} timed. */
    public enum Kind {
        EVENT("event"), LAYER("layer"), PACKET("packet");

        private final String word;

        Kind(String word) {
            this.word = word;
        }

        /** The word that starts the key's label and its serialised form. */
        public String word() {
            return word;
        }
    }

    public static ModKey event(String modId, String eventClassName) {
        return new ModKey(Kind.EVENT, modId, eventClassName);
    }

    public static ModKey layer(String modId, String layerId) {
        return new ModKey(Kind.LAYER, modId, layerId);
    }

    public static ModKey packet(String modId, String packetId) {
        return new ModKey(Kind.PACKET, modId, packetId);
    }

    @Override
    public Category category() {
        return Category.MOD;
    }

    @Override
    public String typeId() {
        return modId;
    }

    /**
     * The key on its own, as in a hitch's contributor list. An event row names its mod, because the event's
     * name does not; a layer's or packet's id already carries its namespace.
     */
    @Override
    public String label() {
        return kind == Kind.EVENT ? memberLabel() + " (" + modId + ")" : memberLabel();
    }

    /** The key under its mod's own row in the Mods tab and report, where the mod is already named. */
    public String memberLabel() {
        return kind.word + " " + (kind == Kind.EVENT ? simpleEventName(name) : name);
    }

    @Override
    public String serialize() {
        return kind.word + "@" + modId + ":" + name;
    }

    /** {@code net.neoforged.neoforge.client.event.ClientTickEvent$Post} reads {@code ClientTickEvent.Post}. */
    public static String simpleEventName(String className) {
        return className.substring(className.lastIndexOf('.') + 1).replace('$', '.');
    }
}
