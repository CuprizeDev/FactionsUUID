package com.massivecraft.factions.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mkremins.fanciful.FancyMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FancySender {

    private static final Map<String, TextDecoration> DECORATIONS = new LinkedHashMap<>();

    static {
        DECORATIONS.put("bold", TextDecoration.BOLD);
        DECORATIONS.put("italic", TextDecoration.ITALIC);
        DECORATIONS.put("underlined", TextDecoration.UNDERLINED);
        DECORATIONS.put("strikethrough", TextDecoration.STRIKETHROUGH);
        DECORATIONS.put("obfuscated", TextDecoration.OBFUSCATED);
    }

    private FancySender() {
    }

    public static void send(FancyMessage message, CommandSender to) {
        if (to == null) {
            return;
        }
        to.sendMessage(toComponent(new JsonParser().parse(message.toJSONString())));
    }

    private static Component toComponent(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return Component.empty();
        }
        if (element.isJsonPrimitive()) {
            return legacy(element.getAsString());
        }
        if (element.isJsonArray()) {
            TextComponent.Builder builder = Component.text();
            for (JsonElement child : (JsonArray) element) {
                builder.append(toComponent(child));
            }
            return builder.build();
        }
        JsonObject object = element.getAsJsonObject();
        TextComponent.Builder builder = Component.text();
        if (object.has("text")) {
            String text = object.get("text").getAsString();
            if (text.indexOf('§') >= 0) {
                builder.append(legacy(text));
            } else {
                builder.content(text);
            }
        }
        if (object.has("color")) {
            TextColor color = color(object.get("color").getAsString());
            if (color != null) {
                builder.color(color);
            }
        }
        for (Map.Entry<String, TextDecoration> entry : DECORATIONS.entrySet()) {
            if (object.has(entry.getKey()) && object.get(entry.getKey()).getAsBoolean()) {
                builder.decoration(entry.getValue(), true);
            }
        }
        if (object.has("clickEvent")) {
            ClickEvent click = click(object.getAsJsonObject("clickEvent"));
            if (click != null) {
                builder.clickEvent(click);
            }
        }
        if (object.has("hoverEvent")) {
            JsonObject hover = object.getAsJsonObject("hoverEvent");
            if (hover.has("action") && hover.get("action").getAsString().equals("show_text") && hover.has("value")) {
                builder.hoverEvent(HoverEvent.showText(toComponent(hover.get("value"))));
            }
        }
        if (object.has("extra")) {
            for (JsonElement child : object.getAsJsonArray("extra")) {
                builder.append(toComponent(child));
            }
        }
        return builder.build();
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private static TextColor color(String name) {
        if (name.startsWith("#")) {
            return TextColor.fromHexString(name);
        }
        return NamedTextColor.NAMES.value(name);
    }

    private static ClickEvent click(JsonObject click) {
        if (!click.has("action") || !click.has("value")) {
            return null;
        }
        String value = click.get("value").getAsString();
        switch (click.get("action").getAsString()) {
            case "run_command":
                return ClickEvent.runCommand(value);
            case "suggest_command":
                return ClickEvent.suggestCommand(value);
            case "open_url":
                return ClickEvent.openUrl(value);
            case "copy_to_clipboard":
                return ClickEvent.copyToClipboard(value);
            default:
                return null;
        }
    }
}
