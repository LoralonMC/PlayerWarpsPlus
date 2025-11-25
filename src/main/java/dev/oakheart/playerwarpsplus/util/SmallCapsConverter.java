package dev.oakheart.playerwarpsplus.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts regular text to Unicode small caps characters.
 *
 * <p>This utility class provides a simple conversion from standard ASCII letters
 * to their Unicode small caps equivalents for aesthetic text formatting.
 *
 * <p>Example:
 * <pre>{@code
 * String result = SmallCapsConverter.convert("Hello World");
 * // Returns: "ʜᴇʟʟᴏ ᴡᴏʀʟᴅ"
 * }</pre>
 *
 * <p><strong>Limitations:</strong>
 * Not all characters have Unicode small caps equivalents. The following characters
 * map to themselves (no small caps variant available):
 * <ul>
 *   <li>'s' and 'S' - Uses lowercase 's' (no Unicode small caps available)</li>
 *   <li>'x' and 'X' - Uses lowercase 'x' (no Unicode small caps available)</li>
 * </ul>
 *
 * <p>All other letters (a-z, A-Z) have proper small caps equivalents.
 * Non-alphabetic characters (numbers, symbols, spaces, etc.) are passed through unchanged.
 *
 * <p>This class uses a static mapping and all methods are static.
 * The class should not be instantiated.
 */
public class SmallCapsConverter {

    private static final Map<Character, Character> SMALL_CAPS_MAP = new HashMap<>();

    static {
        // Lowercase to small caps
        SMALL_CAPS_MAP.put('a', 'ᴀ');
        SMALL_CAPS_MAP.put('b', 'ʙ');
        SMALL_CAPS_MAP.put('c', 'ᴄ');
        SMALL_CAPS_MAP.put('d', 'ᴅ');
        SMALL_CAPS_MAP.put('e', 'ᴇ');
        SMALL_CAPS_MAP.put('f', 'ғ');
        SMALL_CAPS_MAP.put('g', 'ɢ');
        SMALL_CAPS_MAP.put('h', 'ʜ');
        SMALL_CAPS_MAP.put('i', 'ɪ');
        SMALL_CAPS_MAP.put('j', 'ᴊ');
        SMALL_CAPS_MAP.put('k', 'ᴋ');
        SMALL_CAPS_MAP.put('l', 'ʟ');
        SMALL_CAPS_MAP.put('m', 'ᴍ');
        SMALL_CAPS_MAP.put('n', 'ɴ');
        SMALL_CAPS_MAP.put('o', 'ᴏ');
        SMALL_CAPS_MAP.put('p', 'ᴘ');
        SMALL_CAPS_MAP.put('q', 'ǫ');
        SMALL_CAPS_MAP.put('r', 'ʀ');
        SMALL_CAPS_MAP.put('s', 's'); // No Unicode small caps variant available
        SMALL_CAPS_MAP.put('t', 'ᴛ');
        SMALL_CAPS_MAP.put('u', 'ᴜ');
        SMALL_CAPS_MAP.put('v', 'ᴠ');
        SMALL_CAPS_MAP.put('w', 'ᴡ');
        SMALL_CAPS_MAP.put('x', 'x'); // No Unicode small caps variant available
        SMALL_CAPS_MAP.put('y', 'ʏ');
        SMALL_CAPS_MAP.put('z', 'ᴢ');

        // Uppercase to small caps (same as lowercase)
        SMALL_CAPS_MAP.put('A', 'ᴀ');
        SMALL_CAPS_MAP.put('B', 'ʙ');
        SMALL_CAPS_MAP.put('C', 'ᴄ');
        SMALL_CAPS_MAP.put('D', 'ᴅ');
        SMALL_CAPS_MAP.put('E', 'ᴇ');
        SMALL_CAPS_MAP.put('F', 'ғ');
        SMALL_CAPS_MAP.put('G', 'ɢ');
        SMALL_CAPS_MAP.put('H', 'ʜ');
        SMALL_CAPS_MAP.put('I', 'ɪ');
        SMALL_CAPS_MAP.put('J', 'ᴊ');
        SMALL_CAPS_MAP.put('K', 'ᴋ');
        SMALL_CAPS_MAP.put('L', 'ʟ');
        SMALL_CAPS_MAP.put('M', 'ᴍ');
        SMALL_CAPS_MAP.put('N', 'ɴ');
        SMALL_CAPS_MAP.put('O', 'ᴏ');
        SMALL_CAPS_MAP.put('P', 'ᴘ');
        SMALL_CAPS_MAP.put('Q', 'ǫ');
        SMALL_CAPS_MAP.put('R', 'ʀ');
        SMALL_CAPS_MAP.put('S', 's'); // No Unicode small caps variant available
        SMALL_CAPS_MAP.put('T', 'ᴛ');
        SMALL_CAPS_MAP.put('U', 'ᴜ');
        SMALL_CAPS_MAP.put('V', 'ᴠ');
        SMALL_CAPS_MAP.put('W', 'ᴡ');
        SMALL_CAPS_MAP.put('X', 'x'); // No Unicode small caps variant available
        SMALL_CAPS_MAP.put('Y', 'ʏ');
        SMALL_CAPS_MAP.put('Z', 'ᴢ');
    }

    /**
     * Converts a string to small caps
     *
     * @param text The text to convert
     * @return The text in small caps
     */
    public static String convert(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            result.append(SMALL_CAPS_MAP.getOrDefault(c, c));
        }

        return result.toString();
    }
}
