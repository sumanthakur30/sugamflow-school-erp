/** localStorage keys shared by AuthSessionService and HTTP interceptors.
 * Interceptors must read these directly — injecting AuthSessionService creates a
 * HttpClient ↔ AuthSessionService circular DI (NG0200).
 */
export const SESSION_TOKEN_KEY = 'sf.accessToken';
export const SESSION_DATA_KEY = 'sf.session';
export const SESSION_TENANT_KEY = 'sf.tenantId';
export const SESSION_BRANCH_KEY = 'sf.branchId';
export const SESSION_ACADEMIC_KEY = 'sf.sessionId';
export const SESSION_USER_KEY = 'sf.userId';
export const SESSION_ROLE_KEY = 'sf.role';
