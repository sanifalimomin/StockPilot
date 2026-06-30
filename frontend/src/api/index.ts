import type { ApiClient } from './client';
import { httpClient } from './http';

export const api: ApiClient = httpClient;

export type { ApiClient };
export * from './types';
