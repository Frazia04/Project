CREATE TABLE exams
(
  id                   VARCHAR(50)   NOT NULL,
  exercise             VARCHAR(50)   NOT NULL,
  label                VARCHAR(200)  NOT NULL,
  date                 TIMESTAMP     NOT NULL,
  location             VARCHAR(200)      NULL,
  registration_open    BOOLEAN       NOT NULL,
  show_results         BOOLEAN       NOT NULL,

  PRIMARY KEY (id, exercise),
  FOREIGN KEY (exercise) REFERENCES exercises (id)
  );

CREATE TABLE examtasks
(
  exercise             VARCHAR(50)   NOT NULL,
  examid               VARCHAR(50)   NOT NULL,
  id                   VARCHAR(50)   NOT NULL,
  max_points           DECIMAL(20,1) NOT NULL,

  PRIMARY KEY (exercise, examid, id),
  FOREIGN KEY (examid, exercise) REFERENCES exams (id, exercise)
);

CREATE TABLE examgrades
(
  exercise             VARCHAR(50)   NOT NULL,
  examid               VARCHAR(50)   NOT NULL,
  grade                VARCHAR(50)   NOT NULL,
  min_points           DECIMAL(20,1) NOT NULL,

  PRIMARY KEY (exercise, examid, grade),
  FOREIGN KEY (examid, exercise) REFERENCES exams (id, exercise)
);

CREATE TABLE examparticipants
(
  exercise             VARCHAR(50)   NOT NULL,
  examid               VARCHAR(50)   NOT NULL,
  studentid            VARCHAR(50)   NOT NULL,

  PRIMARY KEY (exercise, examid, studentid),
  FOREIGN KEY (examid, exercise) REFERENCES exams (id, exercise)
);

CREATE TABLE examresults
(
    exercise             VARCHAR(50)   NOT NULL,
    examid               VARCHAR(50)   NOT NULL,
    studentid            VARCHAR(50)   NOT NULL,
    taskid               VARCHAR(50)   NOT NULL,
    points               DECIMAL(20,1) NOT NULL,

    PRIMARY KEY (exercise, examid, studentid, taskid),
    FOREIGN KEY (examid, exercise) REFERENCES exams (id, exercise),
    FOREIGN KEY (exercise, examid, taskid) REFERENCES examtasks (exercise, examid, id),
    FOREIGN KEY (exercise, examid, studentid) REFERENCES examparticipants (exercise, examid, studentid)
);
