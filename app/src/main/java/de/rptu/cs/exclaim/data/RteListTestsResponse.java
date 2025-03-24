package de.rptu.cs.exclaim.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("NullAway")
public class RteListTestsResponse {
    private List<String> tests;
    private boolean success;

    public static RteListTestsResponse fromJson(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(
                json,
                new TypeReference<>() {
                }
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @JsonProperty("Tests")
    public List<String> getTests() {
        return tests;
    }

    public void setTests(List<String> tests) {
        this.tests = tests;
    }

    @JsonProperty("Success")
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
