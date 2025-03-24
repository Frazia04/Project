/*
Refactor exercises table:
- Split single term string in year int + term enum + optional comment string
- Make group_join an enum
*/

-- Rename existing constraints such that we can re-use those names
ALTER TABLE exercises RENAME CONSTRAINT pk__exercises TO old_pk__exercises;

CREATE TABLE exercises2
(
    id                 VARCHAR(50)                            NOT NULL,
    lecture            VARCHAR(250)                           NOT NULL,
    "year"             SMALLINT                               NOT NULL,
    term               ENUM ('SUMMER', 'WINTER')                  NULL,
    term_comment       VARCHAR(255)                           NOT NULL DEFAULT '',
    registration_open  BOOLEAN                                NOT NULL DEFAULT FALSE,
    group_join         ENUM ('NONE', 'GROUP', 'PREFERENCES')  NOT NULL DEFAULT 'NONE',

    CONSTRAINT pk__exercises PRIMARY KEY (id)
);

-- Move data
INSERT INTO exercises2 (id, lecture, "year", term_comment, registration_open, group_join)
SELECT                  id, lecture, 0,      term,         registration_open, group_join
FROM exercises;

-- Delete constraints referencing the old table
ALTER TABLE assistants  DROP CONSTRAINT fk__assistants__exercises;
ALTER TABLE exams       DROP CONSTRAINT fk__exams__exercises;
ALTER TABLE groups      DROP CONSTRAINT fk__groups__exercises;
ALTER TABLE preferences DROP CONSTRAINT fk__preferences__exercises;
ALTER TABLE sheets      DROP CONSTRAINT fk__sheets__exercises;

-- Replace old table by new one
DROP TABLE exercises;
ALTER TABLE exercises2 RENAME TO exercises;

-- Re-create the constraints deleted above
ALTER TABLE assistants  ADD CONSTRAINT fk__assistants__exercises  FOREIGN KEY (exerciseid) REFERENCES exercises(id);
ALTER TABLE exams       ADD CONSTRAINT fk__exams__exercises       FOREIGN KEY (exercise)   REFERENCES exercises(id);
ALTER TABLE groups      ADD CONSTRAINT fk__groups__exercises      FOREIGN KEY (exerciseid) REFERENCES exercises(id);
ALTER TABLE preferences ADD CONSTRAINT fk__preferences__exercises FOREIGN KEY (exerciseid) REFERENCES exercises(id);
ALTER TABLE sheets      ADD CONSTRAINT fk__sheets__exercises      FOREIGN KEY (exercise)   REFERENCES exercises(id);
