CREATE TABLE preferences
(
  exerciseid         VARCHAR(50)  NOT NULL,
  userid             INT          NOT NULL,
  preferred          VARCHAR          NULL,
  possible           VARCHAR          NULL,
  dislike            VARCHAR          NULL,
  impossible         VARCHAR          NULL,
  friends            VARCHAR          NULL,
  PRIMARY KEY (exerciseid, userid),
  FOREIGN KEY (exerciseid) REFERENCES exercises (id)
);