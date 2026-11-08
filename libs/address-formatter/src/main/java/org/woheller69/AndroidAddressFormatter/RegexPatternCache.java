package org.woheller69.AndroidAddressFormatter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

class RegexPatternCache {
  @NonNull
  private Map<String, Pattern> map = new HashMap<>();

  @Nullable
  public Pattern get(@NonNull String key) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else {
      Pattern newPattern = Pattern.compile(key);
      map.put(key, newPattern);
      return newPattern;
    }
  }

  @Nullable
  public Pattern get(@NonNull String key, int flags) {
    if (map.containsKey(key)) {
      return map.get(key);
    } else {
      Pattern newPattern = Pattern.compile(key, flags);
      map.put(key, newPattern);
      return newPattern;
    }
  }
}
