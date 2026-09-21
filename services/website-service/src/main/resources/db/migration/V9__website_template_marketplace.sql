-- Phase 6: marketplace template catalog (platform-owned, applied per site).
CREATE TABLE website_template (
    code              VARCHAR(64)  PRIMARY KEY,
    name              VARCHAR(128) NOT NULL,
    description       VARCHAR(1024),
    preview_image_url VARCHAR(1024),
    theme_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    homepage_json     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    navigation_json   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    seo_json          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order        INT          NOT NULL DEFAULT 100,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO website_template (
  code, name, description, theme_json, homepage_json, navigation_json, seo_json, sort_order
) VALUES
(
  'classic-school',
  'Classic School',
  'Traditional blue/gold school homepage with admission CTA.',
  '{"primaryColor":"#0B3D91","secondaryColor":"#F5B700"}'::jsonb,
  '[{"type":"HERO","enabled":true,"order":10,"content":{"title":"Welcome","subtitle":"Excellence in education"}},{"type":"ADMISSION_CTA","enabled":true,"order":40,"content":{"title":"Admissions Open","ctaLabel":"Apply Now"}}]'::jsonb,
  '[{"label":"Home","path":"/","order":10},{"label":"About","path":"/about","order":20},{"label":"Admission","path":"/admission","order":30}]'::jsonb,
  '{"defaultTitle":"School Website","defaultDescription":"Official school website"}'::jsonb,
  10
),
(
  'modern-campus',
  'Modern Campus',
  'Clean modern layout with news-forward homepage.',
  '{"primaryColor":"#0F766E","secondaryColor":"#F59E0B"}'::jsonb,
  '[{"type":"HERO","enabled":true,"order":10,"content":{"title":"A modern campus","subtitle":"Learning without limits"}},{"type":"ANNOUNCEMENTS","enabled":true,"order":20,"content":{}},{"type":"QUICK_LINKS","enabled":true,"order":30,"content":{}}]'::jsonb,
  '[{"label":"Home","path":"/","order":10},{"label":"News","path":"/news","order":20},{"label":"Blog","path":"/blog","order":30},{"label":"Alumni","path":"/alumni","order":40}]'::jsonb,
  '{"defaultTitle":"Modern Campus","defaultDescription":"News and campus life"}'::jsonb,
  20
);
