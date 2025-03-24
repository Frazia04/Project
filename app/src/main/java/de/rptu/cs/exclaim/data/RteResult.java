package de.rptu.cs.exclaim.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("NullAway")
public class RteResult {

    private TestResultDetails test_result;
    private List<FileWarnings> file_warnings;

    public static RteResult fromJson(String resultJson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(
                resultJson, new TypeReference<>() {
                }
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public TestResultDetails getTest_result() {
        return test_result;
    }

    public void setTest_result(TestResultDetails test_result) {
        this.test_result = test_result;
    }

    public List<FileWarnings> getFile_warnings() {
        return file_warnings;
    }

    public void setFile_warnings(List<FileWarnings> file_warnings) {
        this.file_warnings = file_warnings;
    }
}
