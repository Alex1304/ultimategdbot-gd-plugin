-- v2.0.0-alpha1 init --
BEGIN;

DROP TABLE IF EXISTS gd_awarded_level;
CREATE TABLE gd_awarded_level(
	level_id BIGINT PRIMARY KEY,
	insert_date DATETIME NOT NULL,
	downloads INTEGER NOT NULL,
	likes INTEGER NOT NULL
);

DROP TABLE IF EXISTS gd_event_config;
CREATE TABLE gd_event_config(
	guild_id BIGINT PRIMARY KEY,
	channel_awarded_levels_id BIGINT,
	channel_timely_levels_id BIGINT,
	channel_gd_moderators_id BIGINT,
	role_awarded_levels_id BIGINT,
	role_timely_levels_id BIGINT,
	role_gd_moderators_id BIGINT
);

DROP TABLE IF EXISTS gd_leaderboard_ban;
CREATE TABLE gd_leaderboard_ban(
	account_id BIGINT PRIMARY KEY,
	banned_by BIGINT NOT NULL
);

DROP TABLE IF EXISTS gd_leaderboard;
CREATE TABLE gd_leaderboard(
	account_id BIGINT PRIMARY KEY,
	name VARCHAR(32) NOT NULL,
	stars INTEGER NOT NULL,
	diamonds INTEGER NOT NULL,
	user_coins INTEGER NOT NULL,
	secret_coins INTEGER NOT NULL,
	demons INTEGER NOT NULL,
	creator_points INTEGER NOT NULL,
	last_refreshed DATETIME NOT NULL
);

DROP TABLE IF EXISTS gd_level_request_config;
CREATE TABLE gd_level_request_config(
	guild_id BIGINT PRIMARY KEY,
	channel_submission_queue_id BIGINT,
	channel_archived_submissions_id BIGINT,
	role_reviewer_id BIGINT,
	open TINYINT(1) NOT NULL DEFAULT 0,
	max_queued_submissions_per_user INTEGER NOT NULL DEFAULT 5,
	min_reviews_required INTEGER NOT NULL DEFAULT 1,
	CONSTRAINT chk_max_queued_submissions_per_user CHECK (max_queued_submissions_per_user >= 1 AND max_queued_submissions_per_user <= 20),
	CONSTRAINT min_reviews_required CHECK (min_reviews_required >= 1 AND min_reviews_required <= 5)
);

DROP TABLE IF EXISTS gd_level_request_review;
CREATE TABLE gd_level_request_review(
	review_id BIGINT PRIMARY KEY AUTO_INCREMENT,
	reviewer_id BIGINT NOT NULL,
	review_timestamp DATETIME NOT NULL,
	review_content TEXT NOT NULL,
	submission_id BIGINT NOT NULL,
	CONSTRAINT review_content CHECK (review_content != '')
);

DROP TABLE IF EXISTS gd_level_request_submission;
CREATE TABLE gd_level_request_submission(
	submission_id BIGINT PRIMARY KEY AUTO_INCREMENT,
	level_id BIGINT NOT NULL,
	youtube_link VARCHAR(64),
	message_id BIGINT,
	message_channel_id BIGINT,
	guild_id BIGINT NOT NULL,
	submitter_id BIGINT NOT NULL,
	submission_timestamp DATETIME NOT NULL,
	reviewed TINYINT(1) NOT NULL
);

DROP TABLE IF EXISTS gd_linked_user;
CREATE TABLE gd_linked_user(
	discord_user_id BIGINT PRIMARY KEY,
	gd_user_id BIGINT NOT NULL,
	link_activated TINYINT(1) NOT NULL,
	confirmation_token VARCHAR(6)
);

DROP TABLE IF EXISTS gd_mod;
CREATE TABLE gd_mod(
	account_id BIGINT PRIMARY KEY,
	name VARCHAR(32) NOT NULL,
	elder TINYINT(1) NOT NULL
);

ALTER TABLE gd_level_request_review
	ADD CONSTRAINT fk_submission_review
	FOREIGN KEY (submission_id)
	REFERENCES gd_level_request_submission(submission_id)
	ON DELETE CASCADE;

COMMIT;