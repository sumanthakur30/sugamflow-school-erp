export const environment = {
  apiBaseUrl: 'http://localhost:9090',
  defaultHost: 'hcp.localhost',
  /** School ERP UI (SSO / portal deep-links). */
  erpBaseUrl: 'http://localhost:4200',
  production: false,
  /**
   * Google reCAPTCHA site key. Empty = captcha UI off (matches admission.public-captcha.enabled=false).
   * In prod set both site key here and ADMISSION_PUBLIC_CAPTCHA_ENABLED=true + secret on admission-service.
   */
  captchaSiteKey: '',
};
