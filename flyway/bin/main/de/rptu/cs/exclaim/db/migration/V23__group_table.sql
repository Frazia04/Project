CREATE TABLE groups
(
  exerciseid         VARCHAR(50)  NOT NULL,
  groupid            VARCHAR(50)  NOT NULL,
  "day"              VARCHAR(50)      NULL,
  time               VARCHAR(200)      NULL,
  location           VARCHAR(200)      NULL,
  max_size           INT          NOT NULL,
  PRIMARY KEY (exerciseid, groupid),
  FOREIGN KEY (exerciseid) REFERENCES exercises (id)
);

ALTER TABLE exercises ADD COLUMN registration_open  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exercises ADD COLUMN group_join VARCHAR(50) NOT NULL DEFAULT 'NONE';
