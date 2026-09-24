export const environment = {
  production: true,
  /** Same-origin /api → Nginx → gateway-service :9090 (do not hardcode EC2 host). */
  apiBaseUrl: '',
  /** Public HCP school website — CMS Builder live preview iframe. */
  websitePreviewUrl: 'https://hcpschool.com',
};
