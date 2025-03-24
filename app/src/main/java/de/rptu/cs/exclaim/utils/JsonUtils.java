package de.rptu.cs.exclaim.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class JsonUtils {
    private static final ObjectWriter OBJECT_WRITER = new ObjectMapper().writer();

    /**
     * Convert the given value to JSON
     *
     * @param value the object to format
     * @return the JSON string
     */
    public static String toJson(Object value) {
        try {
            return OBJECT_WRITER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
