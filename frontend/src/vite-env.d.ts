/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Absolute API origin baked into hosted builds (e.g. http://<alb-dns>).
   * Unset in dev, where the Vite proxy forwards relative /api requests.
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
