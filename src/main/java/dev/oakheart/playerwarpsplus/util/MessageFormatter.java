package dev.oakheart.playerwarpsplus.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles message formatting with MiniMessage and custom placeholders.
 *
 * <p>This utility class provides methods to:
 * <ul>
 *   <li>Format messages using MiniMessage syntax</li>
 *   <li>Replace custom placeholders (%warp%, %seconds%, etc.)</li>
 *   <li>Process custom &lt;smallcaps&gt; and &lt;sc&gt; tags for Unicode small caps conversion</li>
 *   <li>Send formatted titles and subtitles to players</li>
 * </ul>
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>%warp% or %warp_display% - The warp display name</li>
 *   <li>%seconds% - Seconds remaining in countdown (countdown messages only)</li>
 * </ul>
 *
 * <p>Custom tags:
 * <ul>
 *   <li>&lt;smallcaps&gt;text&lt;/smallcaps&gt; - Converts text to Unicode small caps</li>
 *   <li>&lt;sc&gt;text&lt;/sc&gt; - Short alias for smallcaps</li>
 * </ul>
 *
 * <p>All methods are static and the class should not be instantiated.
 */
public class MessageFormatter {

    private static final Pattern SMALLCAPS_PATTERN = Pattern.compile("<(smallcaps|sc)>(.*?)</\\1>");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageFormatter() {}

    /**
     * Format a message with placeholders
     *
     * @param message      The raw message from config
     * @param warpName     The warp display name
     * @param secondsLeft  Seconds left in countdown (-1 for final message)
     * @return Formatted Component
     */
    public static Component format(String message, String warpName, int secondsLeft) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        // Validate warp name
        if (warpName == null) {
            warpName = "Unknown";
        }

        // Escape warp name to prevent MiniMessage tag injection
        warpName = MINI_MESSAGE.escapeTags(warpName);

        // Replace placeholders with %placeholder% syntax
        String processed = message
                .replace("%warp%", warpName)
                .replace("%warp_display%", warpName);

        if (secondsLeft >= 0) {
            processed = processed.replace("%seconds%", String.valueOf(secondsLeft));
        }

        // Process smallcaps tags before MiniMessage parsing
        processed = processSmallCapsTags(processed);

        // Parse with MiniMessage
        return MINI_MESSAGE.deserialize(processed);
    }

    /**
     * Process <smallcaps>text</smallcaps> and <sc>text</sc> tags.
     * Converts only the text portions to small caps while preserving any
     * nested MiniMessage tags (e.g. {@code <sc><bold>text</bold></sc>} works).
     */
    private static String processSmallCapsTags(String message) {
        Matcher matcher = SMALLCAPS_PATTERN.matcher(message);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String innerContent = matcher.group(2);
            String converted = convertTextPreservingTags(innerContent);
            matcher.appendReplacement(result, Matcher.quoteReplacement(converted));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Converts text to small caps while preserving MiniMessage tags.
     * Characters inside angle brackets are left untouched.
     */
    private static String convertTextPreservingTags(String content) {
        StringBuilder result = new StringBuilder();
        StringBuilder textBuffer = new StringBuilder();
        boolean inTag = false;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                // Flush accumulated text as small caps
                if (textBuffer.length() > 0) {
                    result.append(SmallCapsConverter.convert(textBuffer.toString()));
                    textBuffer.setLength(0);
                }
                inTag = true;
                result.append(c);
            } else if (c == '>' && inTag) {
                inTag = false;
                result.append(c);
            } else if (inTag) {
                result.append(c);
            } else {
                textBuffer.append(c);
            }
        }

        // Flush any remaining text
        if (textBuffer.length() > 0) {
            result.append(SmallCapsConverter.convert(textBuffer.toString()));
        }

        return result.toString();
    }

    /**
     * Send a title to a player with formatting
     *
     * @param player      The player to send to
     * @param titleText   Title message
     * @param subtitleText Subtitle message
     * @param fadeIn      Fade in time (ticks)
     * @param stay        Stay time (ticks)
     * @param fadeOut     Fade out time (ticks)
     * @param warpName    Warp name for placeholders
     * @param secondsLeft Seconds left (-1 for final)
     */
    public static void sendTitle(Player player, String titleText, String subtitleText,
                                  int fadeIn, int stay, int fadeOut,
                                  String warpName, int secondsLeft) {
        Component title = format(titleText, warpName, secondsLeft);
        Component subtitle = format(subtitleText, warpName, secondsLeft);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );

        player.showTitle(Title.title(title, subtitle, times));
    }
}
