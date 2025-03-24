/*
- Split the team column (single string of form 'group|team') into two columns
- DROP CONSTRAINT fk__testresult__sheets (already transitive through fk__testresult__assignments)
*/

-- Rename existing constraints such that we can re-use those names
ALTER TABLE testresult RENAME CONSTRAINT pk__testresult TO old_pk__testresult;
ALTER TABLE testresult RENAME CONSTRAINT fk__testresult__assignments TO old_fk__testresult__assignments;

CREATE TABLE testresult2
(
    exercise        VARCHAR(50)  NOT NULL,
    sheet           VARCHAR(50)  NOT NULL,
    groupid         VARCHAR(50)  NOT NULL,
    teamid          VARCHAR(50)  NOT NULL,
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

    CONSTRAINT pk__testresult PRIMARY KEY (exercise, sheet, assignment, groupid, teamid, requestnr),
    CONSTRAINT fk__testresult__assignments FOREIGN KEY (assignment, exercise, sheet) REFERENCES assignments (id, exercise, sheet)
);

-- Move data
INSERT INTO testresult2 (exercise, sheet, groupid,                                                           teamid,                                           assignment, requestnr, retries, time_request, snapshot, time_started, time_done, compiled, internal_error, missing_files, illegal_files, tests_passed, tests_total, result)
SELECT                   exercise, sheet, SUBSTRING(team FROM 1 FOR GREATEST(0, POSITION('|' IN team) - 1)), SUBSTRING(team FROM (POSITION('|' IN team) + 1)), assignment, requestnr, retries, time_request, snapshot, time_started, time_done, compiled, internal_error, missing_files, illegal_files, tests_passed, tests_total, result
FROM testresult;

DROP TABLE testresult;
ALTER TABLE testresult2 RENAME TO testresult;
