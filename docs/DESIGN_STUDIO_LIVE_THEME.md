# Design Studio — Live theming

School UI applies Design Studio theme at runtime (no rebuild).

## Behaviour

| Surface | Source |
|---|---|
| Login page | `GET /api/config/design-studio/theme/published?organizationId=…` (no JWT) |
| Admin shell / pages | `GET /api/config/design-studio/theme` (JWT) |
| Live edit | Design Studio `preview()` → CSS variables + branding immediately |
| Persist | **Save Draft** / **Publish** |

Applied tokens: `--sf-primary`, `--sf-menu`, `--sf-button`, `--sf-radius`, `--sf-font`, login background image, document title, favicon, sidebar logo / name / tagline / footer.

## Gateway

`/api/config/design-studio/theme/published` is JWT-exempt so the login screen can white-label before sign-in.

## Try it

1. Sign in → Design Studio  
2. Change primary / menu colors or school name — shell updates live  
3. Save → sign out → enter org id on login — branding + colors load for that org  
