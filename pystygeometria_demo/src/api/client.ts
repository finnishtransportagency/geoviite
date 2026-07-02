import { Environment } from "./types";

export interface ApiConfig {
  environment: Environment;
  devApiKey: string;
  prodApiKey: string;
}

function apiKeyForEnvironment(config: ApiConfig): string | undefined {
  switch (config.environment) {
    case "local":
      return undefined;
    case "dev":
    case "test":
      return config.devApiKey || undefined;
    case "prod":
      return config.prodApiKey || undefined;
  }
}

export async function apiGet<T>(
  config: ApiConfig,
  path: string,
  params?: Record<string, string>,
): Promise<T> {
  const base = `/${config.environment}`;
  const query = params ? `?${new URLSearchParams(params)}` : "";
  const headers: Record<string, string> = {};
  const apiKey = apiKeyForEnvironment(config);
  if (apiKey) {
    headers["x-api-key"] = apiKey;
  }
  const response = await fetch(`${base}${path}${query}`, { headers });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText} for ${path}`);
  }
  return (await response.json()) as T;
}
