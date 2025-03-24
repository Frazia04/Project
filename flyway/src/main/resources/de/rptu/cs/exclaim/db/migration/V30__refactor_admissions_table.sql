/*
- Use userid instead of studentid
- Add a boolean column to denote whether the admission is achieved
*/

-- Rename existing constraints such that we can re-use those names
ALTER TABLE admissions RENAME CONSTRAINT pk__admissions TO old_pk__admissions;

CREATE TABLE admissions2
(
    exercise VARCHAR(50) NOT NULL,
    userid   INT         NOT NULL,
    achieved BOOLEAN         NULL,
    message  VARCHAR     NOT NULL,

    CONSTRAINT pk__admissions PRIMARY KEY (exercise, userid)

    -- Added later:
    -- CONSTRAINT fk__admissions__students FOREIGN KEY (userid, exercise) REFERENCES students(userid, exerciseid)
);

-- Move data
INSERT INTO admissions2 (exercise,   userid,   message)
SELECT                 a.exercise, u.userid, a.message
FROM admissions AS a
LEFT JOIN users AS u ON u.studentid = a.studentid;

DROP TABLE admissions;
ALTER TABLE admissions2 RENAME TO admissions;

-- Ensure that students with admissions are registered for the corresponding exercise
INSERT INTO students (userid, exerciseid)
SELECT DISTINCT userid, exercise
FROM admissions AS a
WHERE NOT EXISTS (
    SELECT 1 FROM students AS s
    WHERE s.userid = a.userid AND s.exerciseid = a.exercise
);

ALTER TABLE admissions
ADD CONSTRAINT fk__admissions__students FOREIGN KEY (userid, exercise) REFERENCES students(userid, exerciseid);
