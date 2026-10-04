CREATE DATABASE IF NOT EXISTS job_tracker DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE job_tracker;

CREATE TABLE IF NOT EXISTS resume (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  file_size BIGINT NOT NULL,
  file_type VARCHAR(20) NOT NULL,
  version_name VARCHAR(100) NOT NULL,
  resume_category VARCHAR(255),
  remark VARCHAR(500),
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_resume_version (version_name),
  INDEX idx_resume_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS job_application (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_name VARCHAR(100) NOT NULL,
  position_name VARCHAR(120) NOT NULL,
  position_type VARCHAR(60),
  recruitment_type VARCHAR(40),
  resume_category VARCHAR(80),
  resume_alias VARCHAR(255),
  work_location VARCHAR(100),
  salary VARCHAR(80),
  source VARCHAR(80),
  job_link VARCHAR(500),
  job_description TEXT,
  progress_flow TEXT,
  current_status VARCHAR(40) NOT NULL DEFAULT '待投递',
  progress_result VARCHAR(40),
  progress_operated_time DATETIME,
  applied_time DATETIME,
  submission_group_id VARCHAR(80),
  preference_order INT,
  resume_id BIGINT,
  remark VARCHAR(1000),
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_application_company (company_name),
  INDEX idx_application_status (current_status),
  INDEX idx_application_type (position_type),
  INDEX idx_application_recruitment_type (recruitment_type),
  INDEX idx_application_resume (resume_id),
  INDEX idx_application_applied_time (applied_time),
  INDEX idx_application_submission_group (submission_group_id),
  INDEX idx_application_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  round VARCHAR(60) NOT NULL,
  interview_time DATETIME,
  interviewer VARCHAR(100),
  questions TEXT,
  result VARCHAR(100),
  difficulty INT,
  summary TEXT,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_interview_job (job_id),
  INDEX idx_interview_time (interview_time),
  INDEX idx_interview_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS interview_note (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  content MEDIUMTEXT,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_note_job (job_id),
  INDEX idx_note_updated (updated_time),
  INDEX idx_note_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS note_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT,
  type VARCHAR(20) NOT NULL,
  title VARCHAR(160) NOT NULL,
  file_name VARCHAR(255),
  file_path VARCHAR(500),
  content MEDIUMTEXT,
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_note_item_parent (parent_id),
  INDEX idx_note_item_type (type),
  INDEX idx_note_item_updated (updated_time),
  INDEX idx_note_item_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reminder (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(160) NOT NULL,
  content VARCHAR(1000),
  remind_time DATETIME NOT NULL,
  end_time DATETIME,
  schedule_type VARCHAR(40) DEFAULT '自定义',
  priority VARCHAR(20) DEFAULT '中',
  importance VARCHAR(20) DEFAULT '普通',
  related_application_id BIGINT,
  status VARCHAR(40) NOT NULL DEFAULT '未完成',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  INDEX idx_reminder_time (remind_time),
  INDEX idx_reminder_status (status),
  INDEX idx_reminder_related_application (related_application_id),
  INDEX idx_reminder_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
