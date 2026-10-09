# Gallery on Render

The frontend uses `VITE_API_BASE_URL=https://gwonsystem-backend.onrender.com` in
development and production. Restart Vite after changing environment files.
Use the origin without an `/api` suffix.
Configure the frontend origin in backend `CORS_ALLOWED_ORIGINS` / the existing
`app.cors.allowed-origins` property and preserve the existing session-cookie setup.

Deploy the backend before the frontend. Existing PostgreSQL credentials are reused.
`spring.jpa.hibernate.ddl-auto=update` creates `gallery_photos` on startup; no new
storage credentials or Render persistent disk are required. Photos are stored as
PostgreSQL `bytea`, so they survive application restarts/redeploys while the DB persists.

- `GET /api/gallery`: newest 24 photo metadata records, publicly readable.
- `GET /api/gallery/{id}/image`: original image bytes, publicly readable and cacheable.
- `POST /api/gallery`: multipart `photo`, `title`, `description`; authenticated
  REGULAR/SUPERVISOR users only, with the existing CSRF protection.
- JPG/PNG only, maximum 1 MiB, maximum 20 million pixels.

Photo bytes are omitted from list queries. Frontend sample photos remain labelled
as mock images when the gallery is empty. Previous browser-local mock photos are
not automatically published. Select a photo again to upload it to the server.

The API is connected in code, but deployment itself is a separate operation.
For a large gallery, move image bytes to object storage and retain metadata in the DB.
