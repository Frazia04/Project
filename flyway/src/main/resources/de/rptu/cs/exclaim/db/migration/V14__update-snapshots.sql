-- Names of saved files contain the snapshot time, but without milliseconds.
-- See commit ca4024ee7aef2d152fafbd5667abd566ebe9ecd7
UPDATE uploads SET delete_date = DATE_TRUNC('second', delete_date);
UPDATE testresult SET snapshot = DATE_TRUNC('second', snapshot);
