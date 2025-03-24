ALTER TABLE annotations ALTER COLUMN annotationobj SET NOT NULL;

ALTER TABLE assignments ALTER COLUMN label SET NOT NULL;
ALTER TABLE assignments ALTER COLUMN maxpoints SET NOT NULL;
UPDATE assignments SET showstatistics = false WHERE showstatistics IS NULL;
ALTER TABLE assignments ALTER COLUMN showstatistics SET NOT NULL;

UPDATE exams SET location = '' WHERE location IS NULL;
ALTER TABLE exams ALTER COLUMN location SET DEFAULT '';
ALTER TABLE exams ALTER COLUMN location SET NOT NULL;

UPDATE groups SET time = '' WHERE time IS NULL;
ALTER TABLE groups ALTER COLUMN time SET DEFAULT '';
ALTER TABLE groups ALTER COLUMN time SET NOT NULL;
UPDATE groups SET location = '' WHERE location IS NULL;
ALTER TABLE groups ALTER COLUMN location SET DEFAULT '';
ALTER TABLE groups ALTER COLUMN location SET NOT NULL;

ALTER TABLE sheets ALTER COLUMN label SET NOT NULL;
