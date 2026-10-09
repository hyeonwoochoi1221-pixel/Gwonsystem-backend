# Gallery storage setup

New uploads go to public-images/gallery/<UUID>.png or .jpg. Existing database images remain readable. The existing authenticated REGULAR/SUPERVISOR upload permission and CSRF protection remain in effect.

## Server environment

- SUPABASE_SECRET_KEY: Supabase Dashboard > Settings > API Keys > Secret key. Keep only in the backend runtime environment / deployment secret settings. Never commit it or use a VITE_ variable.
- SUPABASE_URL: defaults to https://zsaqpsohhwygrawphnsg.supabase.co
- SUPABASE_STORAGE_BUCKET: defaults to public-images

Spring Boot does not automatically load a .env file; configure these in the IDE run configuration, process environment, or deployment platform. Restart the backend after configuring the key.

The existing public-images bucket must be public and accept image/png and image/jpeg files up to 1 MB. No anonymous write policy is needed: uploads go through the authenticated backend. The gallery/ prefix is created by uploading; no manual folder is required.

## Database

The entity adds nullable storage_path (varchar 160). This project uses Hibernate ddl-auto=update. If schema updates are disabled, apply:

ALTER TABLE gallery_photos ADD COLUMN IF NOT EXISTS storage_path varchar(160);

Existing image bytes are retained. New rows use an empty byte array for compatibility with the existing NOT NULL image column, and store the object path instead.

## Verification

Sign in as an authorized member, open /gallery/new, select a JPG/PNG under 1 MB, enter a title and submit. Confirm the object under public-images/gallery and the image in /gallery. A missing key returns 503; upstream upload failures return 502. If DB persistence rolls back, the uploaded object is removed. Cleanup failures log the object path for manual cleanup; no key or upstream response is logged. An ambiguous network failure/process crash can still leave an orphan file and needs storage reconciliation.
