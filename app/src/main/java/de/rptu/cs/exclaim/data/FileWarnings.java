package de.rptu.cs.exclaim.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.annotation.Nonnull;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("NullAway")
public class FileWarnings {

    @JsonProperty(value = "file")
    private String filename;
    private List<Warning> warnings;

    private int fileId;

    public static final class FileWarningExtractor implements ResultSetExtractor<Map<Integer, List<Warning>>> {
        @Override
        //Map: FileID -> List of Warnings
        public Map<Integer, List<Warning>> extractData(ResultSet rs) throws SQLException, DataAccessException {
            Map<Integer, List<Warning>> fileWarnings = new HashMap<>();
            while (rs.next()) {
                int fileId = rs.getInt("fileid");
                Warning warning = new Warning.RowMapper().mapRow(rs, rs.getRow());
                if (fileWarnings.containsKey(fileId)) {
                    fileWarnings.get(fileId).add(warning);
                } else {
                    List<Warning> warnings = new ArrayList<>();
                    warnings.add(warning);
                    fileWarnings.put(fileId, warnings);
                }
            }
            return fileWarnings;
        }
    }

    public FileWarnings(String filename) {
        this.filename = filename;
    }

    public FileWarnings() {
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<Warning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<Warning> warnings) {
        this.warnings = warnings;
    }

    public int getFileId() {
        return fileId;
    }

    public void setFileId(int fileId) {
        this.fileId = fileId;
    }

    public static class Warning {

        private String rule;
        private String rule_set;
        private int begin_line;
        private String info_url;
        private int priority;
        private String message;

        public static class RowMapper implements org.springframework.jdbc.core.RowMapper<Warning> {

            @Override
            public Warning mapRow(@Nonnull ResultSet rs, int rowNum) throws SQLException {
                return new Warning(rs.getString("rule"), rs.getString("ruleset"), rs.getInt("line"),
                    rs.getString("infourl"), rs.getInt("priority"), rs.getString("message"));
            }
        }

        public Warning(String rule, String rule_set, int begin_line, String info_url, int priority, String message) {
            this.rule = rule;
            this.rule_set = rule_set;
            this.begin_line = begin_line;
            this.info_url = info_url;
            this.priority = priority;
            this.message = message;
        }

        public Warning() {
        }

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }

        public String getRule_set() {
            return rule_set;
        }

        public void setRule_set(String rule_set) {
            this.rule_set = rule_set;
        }

        public int getBegin_line() {
            return begin_line;
        }

        public void setBegin_line(int begin_line) {
            this.begin_line = begin_line;
        }

        public String getInfo_url() {
            return info_url;
        }

        public void setInfo_url(String info_url) {
            this.info_url = info_url;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @JsonIgnore
        public String getMarkdown() {
            if (info_url == null) {
                return message;
            } else {
                return message + "\n [Weitere Infos](" + info_url + ")";
            }
        }
    }
}
