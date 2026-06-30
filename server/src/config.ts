import { z } from "zod";

const configSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  AQN_API_KEY: z.string().min(1, "AQN_API_KEY is required"),
  AQN_API_URL: z.url().default("https://api.quantumnumbers.anu.edu.au"),
  AQN_TIMEOUT_MS: z.coerce.number().int().min(1000).max(60000).default(15000),
  CORS_ORIGINS: z.string().default("*"),
});

export const config = configSchema.parse(process.env);

export const corsOrigins =
  config.CORS_ORIGINS === "*"
    ? true
    : config.CORS_ORIGINS.split(",").map((origin) => origin.trim()).filter(Boolean);
