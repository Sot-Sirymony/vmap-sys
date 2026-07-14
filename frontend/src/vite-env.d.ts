/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Set from package.json in vite.config.ts
  readonly VITE_APP_VERSION: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
