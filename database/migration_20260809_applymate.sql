CREATE TABLE candidate_profile (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  profile_id VARCHAR(80) NOT NULL,
  schema_version VARCHAR(20) NOT NULL,
  content_json LONGTEXT NOT NULL,
  revision INT NOT NULL DEFAULT 1,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  UNIQUE KEY idx_candidate_profile_profile_id (profile_id)
);

CREATE TABLE profile_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  profile_id VARCHAR(80) NOT NULL,
  revision INT NOT NULL,
  content_json LONGTEXT NOT NULL,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY idx_profile_snapshot_profile_revision (profile_id, revision)
);

CREATE TABLE resume_parse_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  resume_id BIGINT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  extractor VARCHAR(80) NOT NULL,
  status VARCHAR(40) NOT NULL,
  extracted_length INT NOT NULL DEFAULT 0,
  result_json LONGTEXT NULL,
  error_code VARCHAR(80) NULL,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_resume_parse_record_resume (resume_id)
);

CREATE TABLE applymate_extension_pairing (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  extension_id VARCHAR(120) NOT NULL,
  display_name VARCHAR(160),
  claim_secret_hash VARCHAR(64),
  token_hash VARCHAR(64),
  status VARCHAR(20) NOT NULL,
  created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  approved_time TIMESTAMP NULL,
  KEY idx_applymate_pairing_extension (extension_id)
);
