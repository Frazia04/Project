package de.rptu.cs.exclaim.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * TestResult details received from rte-go
 * <p>
 * See struct definitions in rte.go
 */
@SuppressWarnings("NullAway")
public class TestResultDetails {
    private String id;
    private boolean compiled;
    private String compile_error;
    private String internal_error;
    private List<Test> tests;
    private int tests_executed;
    private int tests_failed;
    private List<String> missing_files;
    private List<String> illegal_files;

    public static TestResultDetails fromJson(String resultJson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(
                resultJson,
                new TypeReference<>() {
                }
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static class Test {
        private String name;
        private boolean success;
        private String error;
        private String expected;
        private String output;

        public String getName() {
            return name;
        }


        public String calculateNiceName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getExpected() {
            return expected;
        }

        public void setExpected(String expected) {
            this.expected = expected;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public void setCompiled(boolean compiled) {
        this.compiled = compiled;
    }

    public String getCompile_error() {
        return compile_error;
    }

    public void setCompile_error(String compile_error) {
        this.compile_error = compile_error;
    }

    public String getInternal_error() {
        return internal_error;
    }

    public void setInternal_error(String internal_error) {
        this.internal_error = internal_error;
    }

    public List<Test> getTests() {
        return tests;
    }

    public void setTests(List<Test> tests) {
        this.tests = tests;
    }

    public int getTests_executed() {
        return tests_executed;
    }

    public void setTests_executed(int tests_executed) {
        this.tests_executed = tests_executed;
    }

    public int getTests_failed() {
        return tests_failed;
    }

    public void setTests_failed(int tests_failed) {
        this.tests_failed = tests_failed;
    }

    public List<String> getMissing_files() {
        return missing_files;
    }

    public void setMissing_files(List<String> missing_files) {
        this.missing_files = missing_files;
    }

    public boolean hasMissing_files() {
        return this.missing_files != null && this.missing_files.size() > 0;
    }

    public List<String> getIllegal_files() {
        return illegal_files;
    }

    public void setIllegal_files(List<String> illegal_files) {
        this.illegal_files = illegal_files;
    }

    public boolean hasIllegal_files() {
        return this.illegal_files != null && this.illegal_files.size() > 0;
    }
}
