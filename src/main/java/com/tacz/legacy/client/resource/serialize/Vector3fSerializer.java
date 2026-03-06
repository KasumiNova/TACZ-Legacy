package com.tacz.legacy.client.resource.serialize;

import com.google.gson.*;
import org.joml.Vector3f;

import java.lang.reflect.Type;

/**
 * Gson (de)serializer for JOML {@link Vector3f}.
 * Handles JSON arrays like {@code [1.0, 2.0, 3.0]}.
 * Port of upstream TACZ Vector3fSerializer.
 */
public class Vector3fSerializer implements JsonDeserializer<Vector3f>, JsonSerializer<Vector3f> {
    @Override
    public Vector3f deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            float x = array.get(0).getAsFloat();
            float y = array.get(1).getAsFloat();
            float z = array.get(2).getAsFloat();
            return new Vector3f(x, y, z);
        } else {
            throw new JsonSyntaxException("Expected " + json + " to be a Vector3f because it's not an array");
        }
    }

    @Override
    public JsonElement serialize(Vector3f src, Type typeOfSrc, JsonSerializationContext context) {
        JsonArray array = new JsonArray();
        array.add(new JsonPrimitive(src.x()));
        array.add(new JsonPrimitive(src.y()));
        array.add(new JsonPrimitive(src.z()));
        return array;
    }
}
