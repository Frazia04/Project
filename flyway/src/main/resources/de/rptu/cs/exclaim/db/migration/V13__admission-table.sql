CREATE TABLE admissions
(
  exercise           VARCHAR(50)  NOT NULL,
  studentid          VARCHAR(50)  NOT NULL,
  message            VARCHAR NOT NULL,

  PRIMARY KEY (exercise, studentid),
  FOREIGN KEY (exercise) REFERENCES exercises (id)
)