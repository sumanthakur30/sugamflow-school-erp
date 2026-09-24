-- Archive logo tiles mistakenly published as gallery (school_cms_db).
UPDATE cms_gallery_item
SET status = 'ARCHIVED',
    updated_at = NOW()
WHERE organization_id = 'HCP-01'
  AND status = 'PUBLISHED'
  AND (
    LOWER(TRIM(title)) IN ('logo', 'favicon', 'crest')
    OR LOWER(TRIM(title)) LIKE '%logo%'
  );
