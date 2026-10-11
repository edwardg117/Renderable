package io.github.edwardg117.renderable.core;

/**
 * Something a mod does in the frame that Renderable can name by its owner (spec addendum §P5.A3 to §P5.A5, §P6.5):
 * the mod's handlers for one event class, one HUD layer, one packet or payload type, or one sound event played in
 * one category. All of them roll up by mod id into the Mods tab, so they share {@link Category#MOD} and use the mod
 * id as the type id.
 *
 * @param kind  what was timed
 * @param modId the owning mod: the mod whose file registered the handler, or a layer's, packet's or sound event's
 *              namespace
 * @param name  the event's fully-qualified class name, the layer's or packet's full id, or for a sound
 *              {@code <event id> (<category>)}
 */
public record ModKey(Kind kind, String modId, String name) implements ProfileKey {

    /** What a {@link ModKey} timed. */
    public enum Kind {
        EVENT("event"), LAYER("layer"), PACKET("packet"), SOUND("sound");

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

    /**
     * One sound event played in one category (spec addendum §P6.5). The event id's namespace names the mod, as a
     * layer's or packet's does, and the name carries the category, so one event played in two categories is two
     * rows: {@code sound@create:create:steam (block)}, which reads {@code sound create:steam (Blocks)} in a label.
     *
     * @param eventId  the event's id as {@code namespace:path}, from {@code SoundInstance.getLocation()}; an id with
     *                 no colon is taken to be {@code minecraft}'s
     * @param category the category's name, from {@code SoundSource.getName()}: {@code block}, {@code master}, ...
     */
    public static ModKey sound(String eventId, String category) {
        int colon = eventId.indexOf(':');
        String modId = colon < 0 ? "minecraft" : eventId.substring(0, colon);
        return new ModKey(Kind.SOUND, modId, eventId + " (" + category + ")");
    }

    /** A sound key's event id, {@code create:steam}; "" for any other kind. */
    public String soundEvent() {
        if (kind != Kind.SOUND) return "";
        int open = name.lastIndexOf(" (");
        return open >= 0 ? name.substring(0, open) : "";
    }

    /** A sound key's category, a {@code SoundSource} name such as {@code block}; "" for any other kind. */
    public String soundCategory() {
        if (kind != Kind.SOUND) return "";
        int open = name.lastIndexOf(" (");
        return open >= 0 && name.endsWith(")") ? name.substring(open + 2, name.length() - 1) : "";
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
        if (kind == Kind.SOUND) {
            // The label names the category as the Music & Sounds screen does; the name and the serialised form keep the
            // internal one, so the JSON key is stable and the advice reads the same category (spec addendum §P6.14).
            return kind.word + " " + soundEvent() + " (" + SoundCategories.slider(soundCategory()) + ")";
        }
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
