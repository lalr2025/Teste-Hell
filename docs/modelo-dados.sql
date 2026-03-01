-- Modelo SQL de referência para app Android offline (Room/SQLite)

CREATE TABLE report (
  id TEXT PRIMARY KEY,
  created_at TEXT NOT NULL,
  latitude REAL,
  longitude REAL,
  location_unavailable INTEGER NOT NULL DEFAULT 0,
  sync_status TEXT NOT NULL DEFAULT 'PENDING_SYNC'
);

CREATE TABLE question (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  required INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE question_option (
  id TEXT PRIMARY KEY,
  question_id TEXT NOT NULL,
  label TEXT NOT NULL,
  value TEXT NOT NULL,
  FOREIGN KEY (question_id) REFERENCES question(id)
);

CREATE TABLE answer (
  id TEXT PRIMARY KEY,
  report_id TEXT NOT NULL,
  question_id TEXT NOT NULL,
  option_id TEXT NOT NULL,
  answered_at TEXT NOT NULL,
  FOREIGN KEY (report_id) REFERENCES report(id),
  FOREIGN KEY (question_id) REFERENCES question(id),
  FOREIGN KEY (option_id) REFERENCES question_option(id)
);

CREATE TABLE geo_photo (
  id TEXT PRIMARY KEY,
  report_id TEXT NOT NULL,
  file_path TEXT NOT NULL,
  latitude REAL,
  longitude REAL,
  captured_at TEXT NOT NULL,
  sync_status TEXT NOT NULL DEFAULT 'PENDING_SYNC',
  FOREIGN KEY (report_id) REFERENCES report(id)
);

CREATE INDEX idx_answer_report_id ON answer(report_id);
CREATE INDEX idx_photo_report_id ON geo_photo(report_id);
