CREATE TABLE IF NOT EXISTS transactions (
  transaction_id TEXT PRIMARY KEY,
  seller TEXT NOT NULL,
  buyer TEXT NOT NULL,
  created_by TEXT NOT NULL,
  encrypted_content TEXT NOT NULL,
  metadata JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    password TEXT NOT NULL
);