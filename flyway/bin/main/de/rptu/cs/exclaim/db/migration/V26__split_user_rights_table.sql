/*
Separate table per role instead of a "role" column in a single table brings advantages:
- Prevent student from being in multiple groups/teams
- Prevent tutors without group
- No need for an explicit integer key
*/

CREATE TABLE students
(
  userid            INT          NOT NULL,
  exerciseid        VARCHAR(50)  NOT NULL,
  groupid           VARCHAR(50)      NULL,
  teamid            VARCHAR(50)      NULL,

  CONSTRAINT pk__students PRIMARY KEY (userid, exerciseid),
  CONSTRAINT fk__students__users FOREIGN KEY (userid) REFERENCES users(userid),
  CONSTRAINT fk__students__groups FOREIGN KEY (exerciseid, groupid) REFERENCES groups(exerciseid, groupid),
  CONSTRAINT check__students__teamid_requires_groupid CHECK (teamid IS NULL OR groupid IS NOT NULL)
);

CREATE TABLE tutors
(
  userid            INT          NOT NULL,
  exerciseid        VARCHAR(50)  NOT NULL,
  groupid           VARCHAR(50)  NOT NULL,
  visible           BOOLEAN      NOT NULL DEFAULT TRUE,

  CONSTRAINT pk__tutors PRIMARY KEY (userid, exerciseid, groupid),
  CONSTRAINT fk__tutors__users FOREIGN KEY (userid) REFERENCES users(userid),
  CONSTRAINT fk__tutors__groups FOREIGN KEY (exerciseid, groupid) REFERENCES groups(exerciseid, groupid)
);

CREATE TABLE assistants
(
  userid            INT          NOT NULL,
  exerciseid        VARCHAR(50)  NOT NULL,

  CONSTRAINT pk__assistants PRIMARY KEY (userid, exerciseid),
  CONSTRAINT fk__assistants__users FOREIGN KEY (userid) REFERENCES users(userid),
  CONSTRAINT fk__assistants__exercises FOREIGN KEY (exerciseid) REFERENCES exercises(id)
);

INSERT INTO students (userid, exerciseid, groupid, teamid)
SELECT                userid, exerciseid, groupid, teamid
FROM user_rights
WHERE role = 'student';

INSERT INTO tutors (userid, exerciseid, groupid)
SELECT              userid, exerciseid, groupid
FROM user_rights
WHERE role = 'tutor';

INSERT INTO assistants (userid, exerciseid)
SELECT                  userid, exerciseid
FROM user_rights
WHERE role = 'assistant';

DROP TABLE user_rights;
