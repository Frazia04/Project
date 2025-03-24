/*
- Use userid instead of studentid
- DROP CONSTRAINT fk__examresults__exams (already transitive through fk__examresults__examtasks and fk__examresults__examparticipants)
*/

-- Rename existing constraints such that we can re-use those names
ALTER TABLE examparticipants RENAME CONSTRAINT pk__examparticipants TO old_pk__examparticipants;
ALTER TABLE examparticipants RENAME CONSTRAINT fk__examparticipants__exams TO old_fk__examparticipants__exams;
ALTER TABLE examresults RENAME CONSTRAINT pk__examresults TO old_pk__examresults;
ALTER TABLE examresults RENAME CONSTRAINT fk__examresults__examtasks TO old_fk__examresults__examtasks;
ALTER TABLE examresults RENAME CONSTRAINT fk__examresults__examparticipants TO old_fk__examresults__examparticipants;

CREATE TABLE examparticipants2
(
    exercise             VARCHAR(50)   NOT NULL,
    examid               VARCHAR(50)   NOT NULL,
    userid               INT           NOT NULL,

    CONSTRAINT pk__examparticipants PRIMARY KEY (exercise, examid, userid),
    CONSTRAINT fk__examparticipants__exams FOREIGN KEY (examid, exercise) REFERENCES exams (id, exercise)

    -- Added later:
    -- CONSTRAINT fk__examparticipants__students FOREIGN KEY (userid, exercise) REFERENCES students(userid, exerciseid)
);

CREATE TABLE examresults2
(
    exercise             VARCHAR(50)   NOT NULL,
    examid               VARCHAR(50)   NOT NULL,
    userid               INT           NOT NULL,
    taskid               VARCHAR(50)   NOT NULL,
    points               DECIMAL(20,1) NOT NULL,

    CONSTRAINT pk__examresults PRIMARY KEY (exercise, examid, userid, taskid),
    CONSTRAINT fk__examresults__examtasks FOREIGN KEY (exercise, examid, taskid) REFERENCES examtasks (exercise, examid, id),
    CONSTRAINT fk__examresults__examparticipants FOREIGN KEY (exercise, examid, userid) REFERENCES examparticipants2 (exercise, examid, userid)
);

-- Move data
INSERT INTO examparticipants2 (exercise,   examid,   userid)
SELECT                       e.exercise, e.examid, u.userid
FROM examparticipants AS e
LEFT JOIN users AS u ON u.studentid = e.studentid;

INSERT INTO examresults2 (exercise,   examid,   userid,   taskid,   points)
SELECT                  r.exercise, r.examid, u.userid, r.taskid, r.points
FROM examresults AS r
LEFT JOIN users AS u ON u.studentid = r.studentid;

DROP TABLE examresults;
DROP TABLE examparticipants;
ALTER TABLE examresults2 RENAME TO examresults;
ALTER TABLE examparticipants2 RENAME TO examparticipants;


-- Ensure that exam participants are registered for the corresponding exercise
INSERT INTO students (userid, exerciseid)
SELECT DISTINCT userid, exercise
FROM examparticipants AS e
WHERE NOT EXISTS (
    SELECT 1 FROM students AS s
    WHERE s.userid = e.userid AND s.exerciseid = e.exercise
);

ALTER TABLE examparticipants
ADD CONSTRAINT fk__examparticipants__students FOREIGN KEY (userid, exercise) REFERENCES students(userid, exerciseid);
