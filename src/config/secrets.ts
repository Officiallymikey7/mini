/**
 * config/secrets.ts
 * Safe secret loading – reads OPENAI_API_KEY ONLY from the process environment.
 * Never accept key values from user input, constructor args, or hardcoded strings.
 */
import * as dotenv from 'dotenv';

// Load .env file if present (dev convenience). In production, supply vars directly.
dotenv.config();

export interface Secrets {
  openAiApiKey: string;
}

/** Returns validated secrets from environment variables. Throws if required vars are missing. */
export function loadSecrets(): Secrets {
  const openAiApiKey = process.env['OPENAI_API_KEY'];
  if (!openAiApiKey || openAiApiKey.trim() === '') {
    throw new Error(
      'OPENAI_API_KEY environment variable is not set. ' +
        'Copy .env.example to .env and fill in your key, or export it in your shell. ' +
        'NEVER hardcode API keys in source files.',
    );
  }
  return { openAiApiKey: openAiApiKey.trim() };
}
