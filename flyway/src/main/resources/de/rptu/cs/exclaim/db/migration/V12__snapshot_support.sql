--ALTER TABLE testresult DROP COLUMN submitted_files;
--ALTER TABLE testresult ADD COLUMN snapshot DATETIME AFTER time_request;
--UPDATE testresult SET snapshot = time_request;
--ALTER TABLE testresult ALTER COLUMN snapshot SET NOT NULL;
--ALTER TABLE testresult ADD COLUMN internal_error BOOLEAN NULL AFTER compiled;

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
    tests_passed    INT          NULL,
    tests_total     INT          NULL,
    -- result just stores the resulting json without further structure
    result          CLOB         NULL,

    PRIMARY KEY (exercise, sheet, team, assignment, requestnr),
    FOREIGN KEY (sheet, exercise) REFERENCES sheets (id, exercise),
    FOREIGN KEY (assignment, exercise, sheet) REFERENCES assignments (id, exercise, sheet)
);

INSERT INTO testresult2 (exercise, sheet, team, assignment, requestnr, retries, time_request, snapshot,     time_started, time_done, compiled, internal_error, tests_passed, tests_total, result)
SELECT                   exercise, sheet, team, assignment, requestnr, retries, time_request, time_request, time_started, time_done, compiled, NULL,           tests_passed, tests_total, result
FROM testresult;

DROP TABLE testresult;
ALTER TABLE testresult2 RENAME TO testresult;
