--ALTER TABLE testresult ADD COLUMN missing_files BOOLEAN NULL AFTER internal_error;
--ALTER TABLE testresult ADD COLUMN illegal_files BOOLEAN NULL AFTER missing_files;
--UPDATE testresult SET missing_files = false;
--UPDATE testresult SET illegal_files = false;

CREATE TABLE testresult2
(
    exercise        VARCHAR(50)  NOT NULL,
    sheet           VARCHAR(50)  NOT NULL,
    team            VARCHAR(200) NOT NULL,
    assignment      VARCHAR(50)  NOT NULL,
    requestnr       INT          NOT NULL,
    retries         INT          NOT NULL DEFAULT 0,
    time_request    TIMESTAMP    NOT NULL,
    snapshot        TIMESTAMP    NOT NULL,
    -- fields below are only used when test is completed
    time_started    TIMESTAMP    NULL,
    time_done       TIMESTAMP    NULL,
    compiled        BOOLEAN      NULL,
    internal_error  BOOLEAN      NULL,
    missing_files   BOOLEAN      NULL,
    illegal_files   BOOLEAN      NULL,
    tests_passed    INT          NULL,
    tests_total     INT          NULL,
    -- result just stores the resulting json without further structure
    result          CLOB         NULL,

    PRIMARY KEY (exercise, sheet, team, assignment, requestnr),
    FOREIGN KEY (sheet, exercise) REFERENCES sheets (id, exercise),
    FOREIGN KEY (assignment, exercise, sheet) REFERENCES assignments (id, exercise, sheet)
);

INSERT INTO testresult2 (exercise, sheet, team, assignment, requestnr, retries, time_request, snapshot, time_started, time_done, compiled, internal_error, missing_files, illegal_files, tests_passed, tests_total, result)
SELECT                   exercise, sheet, team, assignment, requestnr, retries, time_request, snapshot, time_started, time_done, compiled, internal_error, false,         false,         tests_passed, tests_total, result
FROM testresult;

DROP TABLE testresult;
ALTER TABLE testresult2 RENAME TO testresult;
