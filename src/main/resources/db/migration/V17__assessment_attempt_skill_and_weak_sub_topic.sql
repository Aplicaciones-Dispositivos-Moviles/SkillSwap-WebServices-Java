-- Assessment & Peer Review: each attempt keeps the skill it assessed and the sub-topic where the student failed the
-- most (T63, platform metrics), so the difficulty of skills and sub-topics is read without asking the blueprints one
-- by one. Until this version both were only computed on read.
ALTER TABLE assessment_attempts
    ADD COLUMN skill_tag character varying(100),
    ADD COLUMN weak_sub_topic character varying(100);

-- The skill of the attempts already recorded comes from their blueprint. Their weak sub-topic cannot be recovered
-- here (it needs the questions and the answers), so it stays null: they only count for the difficulty of the skill.
UPDATE assessment_attempts a
SET skill_tag = b.skill_tag
FROM assessment_blueprints b
WHERE b.id = a.blueprint_id;

CREATE INDEX ix_assessment_attempts_skill_tag_completed_at ON assessment_attempts (skill_tag, completed_at);
