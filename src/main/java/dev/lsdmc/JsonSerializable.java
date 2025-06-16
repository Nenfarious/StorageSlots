package dev.lsdmc;

import com.google.gson.JsonObject;

public interface JsonSerializable {
    void serialize(JsonObject json);
    void deserialize(JsonObject json);
} 