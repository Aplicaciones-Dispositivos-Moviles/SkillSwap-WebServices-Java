-- Assessment & Peer Review: the video of the final demonstration (US29) and the review of the verifier. The video
-- lives in the file storage; only its reference is kept. A demonstration that has a video or was reviewed must have
-- the reference, and a reviewed one must have its score.
ALTER TABLE final_demonstrations
    ADD COLUMN video_storage_reference character varying(300),
    ADD COLUMN video_content_type character varying(50),
    ADD COLUMN video_size_bytes bigint,
    ADD COLUMN video_submitted_at timestamp with time zone,
    ADD COLUMN score integer,
    ADD COLUMN feedback character varying(1000),
    ADD COLUMN reviewed_at timestamp with time zone;

ALTER TABLE final_demonstrations
    ADD CONSTRAINT ck_final_demonstrations_score CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_final_demonstrations_video CHECK (
        status NOT IN ('VideoSubmitted', 'Approved', 'Rejected') OR video_storage_reference IS NOT NULL),
    ADD CONSTRAINT ck_final_demonstrations_review CHECK (
        (status IN ('Approved', 'Rejected')) = (score IS NOT NULL AND reviewed_at IS NOT NULL));
