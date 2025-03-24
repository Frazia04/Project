/*
Refactor the various results tables (studentres, attendance, deltapoints, results, comments).
- Table studentresults with primary key (exercise, sheet, userid), combining studentres, attendance, deltapoints.
- Table teamresults with primary key (exercise, sheet, groupid, teamid), such that there is a 1:n relation from
  teamresults to studentresults (multiple students in the same team). This replaces the former comments table.
- Table teamresults_assignment with primary key (exercise, sheet, assignment, groupid, teamid), such that there is a 1:n
  relation from teamresults to teamresults_assignment (multiple assignments for the same sheet). This replaces the
  former results table.
- The teamresults table avoids the n:m relation between studentres and results (for better query plans).
- Further additions/changes:
  - "excused" attendance
  - Split the group|team string into two separate columns
  - Use userid instead of studentid
  - Add comments per assignment (in addition to the existing comments per sheet)
*/

CREATE TYPE t_attendance AS ENUM ('PRESENT', 'ABSENT', 'EXCUSED');

CREATE TABLE studentresults
(
    exercise            VARCHAR(50)                           NOT NULL,
    sheet               VARCHAR(50)                           NOT NULL,
    userid              INT                                   NOT NULL,
    attended            t_attendance                              NULL,
    groupid             VARCHAR(50)                               NULL,
    teamid              VARCHAR(50)                               NULL,
    deltapoints         DECIMAL(20,1)                             NULL,
    deltapoints_reason  VARCHAR                                   NULL,

    CONSTRAINT pk__studentresults PRIMARY KEY (exercise, sheet, userid),
    CONSTRAINT fk__studentresults__sheets FOREIGN KEY (exercise, sheet) REFERENCES sheets(exercise, id),
    CONSTRAINT check__studentresults__teamid_requires_groupid CHECK (teamid IS NULL OR groupid IS NOT NULL)

    -- Added later:
    -- CONSTRAINT fk__studentresults__students FOREIGN KEY (userid, exercise) REFERENCES students(userid, exerciseid)
);

-- Non-unique index to look up team members
CREATE INDEX idx__studentresults__sheet_team ON studentresults(exercise, sheet, groupid, teamid);

CREATE TABLE teamresults
(
    exercise            VARCHAR(50)                    NOT NULL,
    sheet               VARCHAR(50)                    NOT NULL,
    groupid             VARCHAR(50)                    NOT NULL,
    teamid              VARCHAR(50)                    NOT NULL,
    comment             VARCHAR                            NULL,
    hidecomments        BOOLEAN                        NOT NULL DEFAULT FALSE,
    hidepoints          BOOLEAN                        NOT NULL DEFAULT FALSE,

    CONSTRAINT pk__teamresults PRIMARY KEY (exercise, sheet, groupid, teamid),
    CONSTRAINT fk__teamresults__sheets FOREIGN KEY (exercise, sheet) REFERENCES sheets (exercise, id),
    CONSTRAINT check__teamresults_teamid_requires_groupid CHECK (teamid IS NULL OR groupid IS NOT NULL)
);

CREATE TABLE teamresults_assignment
(
    exercise            VARCHAR(50)                    NOT NULL,
    sheet               VARCHAR(50)                    NOT NULL,
    assignment          VARCHAR(50)                    NOT NULL,
    groupid             VARCHAR(50)                    NOT NULL,
    teamid              VARCHAR(50)                    NOT NULL,
    points              DECIMAL(20,1)                      NULL,
    comment             VARCHAR                            NULL,

    CONSTRAINT pk__teamresults_assignment PRIMARY KEY (exercise, sheet, assignment, groupid, teamid),
    CONSTRAINT fk__teamresults_assignment__assignments FOREIGN KEY (exercise, sheet, assignment) REFERENCES assignments (exercise, sheet, id)

    -- Added later:
    -- CONSTRAINT fk__teamresults_assignment__teamresults FOREIGN KEY (exercise, sheet, groupid, teamid) REFERENCES teamresults (exercise, sheet, groupid, teamid)
);


-- Move studentres data
INSERT INTO studentresults (exercise,    sheet,   userid, groupid,                                                                 teamid)
SELECT                   sr.exercise, sr.sheet, u.userid, SUBSTRING(sr.team FROM 1 FOR GREATEST(0, POSITION('|' IN sr.team) - 1)), SUBSTRING(sr.team FROM (POSITION('|' IN sr.team) + 1))
FROM studentres AS sr
LEFT JOIN users AS u ON u.studentid = sr.studentid;

DROP TABLE studentres;


-- Move attendance data
INSERT INTO studentresults (exercise,   sheet,   userid, attended)
SELECT                    a.exercise, a.sheet, u.userid, (CASE WHEN a.attended THEN 'PRESENT'::t_attendance ELSE 'ABSENT'::t_attendance END)
FROM attendance AS a
LEFT JOIN users AS u ON u.studentid = a.studentid
ON CONFLICT ON CONSTRAINT pk__studentresults
DO UPDATE SET attended = EXCLUDED.attended
;

DROP TABLE attendance;


-- Move deltapoints data
INSERT INTO studentresults (exercise,   sheet,   userid,   deltapoints,   deltapoints_reason)
SELECT                    d.exercise, d.sheet, u.userid, d.delta,       d.reason
FROM deltapoints AS d
LEFT JOIN users AS u ON u.studentid = d.studentid
ON CONFLICT ON CONSTRAINT pk__studentresults
DO UPDATE SET deltapoints = EXCLUDED.deltapoints, deltapoints_reason = EXCLUDED.deltapoints_reason
;

DROP TABLE deltapoints;


-- Cleanup zero deltas
UPDATE studentresults SET deltapoints        = NULL WHERE deltapoints        = 0;
UPDATE studentresults SET deltapoints_reason = NULL WHERE deltapoints_reason = '';


-- Ensure that students with results are registered for the corresponding exercise
INSERT INTO students (userid, exerciseid)
SELECT DISTINCT userid, exercise
FROM studentresults
ON CONFLICT DO NOTHING
;

ALTER TABLE studentresults
ADD CONSTRAINT fk__studentresults__students FOREIGN KEY (userid, exercise) REFERENCES students(userid, exerciseid);


-- Move results data
INSERT INTO teamresults_assignment (exercise, sheet, assignment, groupid,                                                           teamid,                                           points)
SELECT                              exercise, sheet, assignment, SUBSTRING(team FROM 1 FOR GREATEST(0, POSITION('|' IN team) - 1)), SUBSTRING(team FROM (POSITION('|' IN team) + 1)), points
FROM results;

DROP TABLE results;


-- Move comments data
INSERT INTO teamresults (exercise, sheet, groupid,                                                           teamid,                                           comment, hidecomments)
SELECT                   exercise, sheet, SUBSTRING(team FROM 1 FOR GREATEST(0, POSITION('|' IN team) - 1)), SUBSTRING(team FROM (POSITION('|' IN team) + 1)), comment, COALESCE(hidden, false)
FROM comments;

DROP TABLE comments;


-- Add fk__teamresults_assignment__teamresults
INSERT INTO teamresults (exercise, sheet, groupid, teamid)
SELECT DISTINCT          exercise, sheet, groupid, teamid
FROM teamresults_assignment
ON CONFLICT DO NOTHING
;

ALTER TABLE teamresults_assignment
ADD CONSTRAINT fk__teamresults_assignment__teamresults FOREIGN KEY (exercise, sheet, groupid, teamid) REFERENCES teamresults (exercise, sheet, groupid, teamid);
