CREATE TABLE testresult
(
  exercise        VARCHAR(50)  NOT NULL,
  sheet           VARCHAR(50)  NOT NULL,
  team            VARCHAR(200) NOT NULL,
  assignment      VARCHAR(50)  NOT NULL,
  requestnr       INT          NOT NULL,
  retries         INT          NOT NULL DEFAULT 0,
  time_request    TIMESTAMP    NOT NULL,
  -- files submitted to RTE
  submitted_files VARCHAR      NOT NULL,
  -- fields below are only used when test is completed
  time_started    TIMESTAMP    NULL,
  time_done       TIMESTAMP    NULL,
  compiled        BOOLEAN      NULL,
  tests_passed    INT          NULL,
  tests_total     INT          NULL,
  -- result just stores the resulting json without further structure
  result          CLOB         NULL,

  PRIMARY KEY (exercise, sheet, team, assignment, requestnr),
  FOREIGN KEY (sheet, exercise) REFERENCES sheets (id, exercise),
  FOREIGN KEY (assignment, exercise, sheet) REFERENCES assignments (id, exercise, sheet)
)
