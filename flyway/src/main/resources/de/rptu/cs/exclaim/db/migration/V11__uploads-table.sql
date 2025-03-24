CREATE TABLE uploads
(
  exercise           VARCHAR(50)  NOT NULL,
  sheet              VARCHAR(50)  NOT NULL,
  team               VARCHAR(200) NOT NULL,
  assignment         VARCHAR(50)  NOT NULL,
  filename           VARCHAR(200) NOT NULL,
  upload_date        TIMESTAMP    NOT NULL,
  uploader_studentid VARCHAR(50)  NULL, -- null for backwards compatibility
  delete_date        TIMESTAMP    NULL,
  deleter_studentid  VARCHAR(50)  NULL,

  PRIMARY KEY (exercise, sheet, team, assignment, filename, upload_date),
  FOREIGN KEY (sheet, exercise) REFERENCES sheets (id, exercise),
  FOREIGN KEY (assignment, exercise, sheet) REFERENCES assignments (id, exercise, sheet)
)
