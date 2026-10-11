package io.github.edwardg117.renderable.core;

/**
 * The names the game's Music & Sounds screen gives the sound categories. A sound row's label and the Advice tab both
 * use them (spec addendum §P6.9, §P6.14); a row's key keeps the internal name.
 */
public final class SoundCategories {

    private SoundCategories() {
    }

    /**
     * A sound category's slider in Music & Sounds, by its {@code SoundSource} name: the English caption of
     * {@code soundCategory.<name>} in Minecraft 1.21.1's {@code en_us.json} (spec addendum §P6.9). A category with no
     * slider here reads as it is.
     */
    public static String slider(String category) {
        return switch (category) {
            case "master" -> "Master Volume";
            case "music" -> "Music";
            case "record" -> "Jukebox/Note Blocks";
            case "weather" -> "Weather";
            case "block" -> "Blocks";
            case "hostile" -> "Hostile Creatures";
            case "neutral" -> "Friendly Creatures";
            case "player" -> "Players";
            case "ambient" -> "Ambient/Environment";
            case "voice" -> "Voice/Speech";
            default -> category;
        };
    }
}
