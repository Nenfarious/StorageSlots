package dev.lsdmc;

import com.google.gson.JsonObject;

public interface JsonSerializable {
  void serialize(JsonObject paramJsonObject);
  
  void deserialize(JsonObject paramJsonObject);
}
