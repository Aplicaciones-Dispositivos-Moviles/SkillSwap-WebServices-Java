-- Learning Path Engine (US09): a certificate validated by a verifier that covers the skill of a node counts as
-- the skill already demonstrated, so the node is completed and linked to that certificate. The flag tells such a
-- node apart from one completed by passing its assessment (which may also have a certificate linked as
-- supporting evidence). Existing nodes were all completed by an assessment.
ALTER TABLE path_nodes
    ADD COLUMN completed_by_certificate boolean NOT NULL DEFAULT false;

-- Only a completed node with its certificate linked can be completed by a certificate.
ALTER TABLE path_nodes
    ADD CONSTRAINT ck_path_nodes_completed_by_certificate
        CHECK (NOT completed_by_certificate OR (status = 'Completed' AND linked_certificate_id IS NOT NULL));
