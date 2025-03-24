ALTER TABLE students
ADD CONSTRAINT fk__students__exercises FOREIGN KEY (exerciseid) REFERENCES exercises(id);
