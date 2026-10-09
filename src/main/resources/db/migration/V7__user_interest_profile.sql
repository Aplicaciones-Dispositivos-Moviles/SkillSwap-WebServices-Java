-- Identity & Access: interest profile of the student (US04).
--
-- interest_topics: the topics the student wrote, as a JSON array of strings (["Desarrollo web", "Java"]).
-- skill_vector:    the tags of the internal skill catalog those topics and the bio refer to (["javascript",
--                  "java-language"]), recalculated by the application whenever the topics or the bio change. It
--                  personalizes the learning paths and the matching with verifiers.
-- Both start empty for the existing accounts: the vector is calculated the next time the profile is updated.
ALTER TABLE users
    ADD COLUMN interest_topics jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE users
    ADD COLUMN skill_vector jsonb NOT NULL DEFAULT '[]'::jsonb;
