import { Agent, request } from "undici";
import { z } from "zod";
import { randomBytes, randomInt } from "node:crypto";
import { config } from "./config.js";

const randomRequestSchema = z.object({
  type: z.enum(["uint8", "uint16", "hex8", "hex16"]).default("uint16"),
  length: z.coerce.number().int().min(1).max(1024).default(32),
  size: z.coerce.number().int().min(1).max(1024).optional(),
  provider: z.enum(["aqn", "debug"]).default("aqn"),
});

const aqnResponseSchema = z.object({
  success: z.literal(true),
  type: z.string(),
  length: z.union([z.string(), z.number()]),
  data: z.array(z.union([z.number().int(), z.string()])),
});

export type RandomQuery = z.infer<typeof randomRequestSchema>;

export type QuantumNumbers = {
  source: "ANU Quantum Numbers" | "debug";
  type: RandomQuery["type"];
  length: number;
  values: Array<number | string>;
};

export function parseRandomQuery(input: unknown): RandomQuery {
  return randomRequestSchema.parse(input);
}

export function normalizeAqnResponse(input: unknown, query: RandomQuery): QuantumNumbers {
  const parsed = aqnResponseSchema.parse(input);
  const length = Number(parsed.length);
  if (parsed.data.length !== length || length !== query.length) {
    throw new Error("AQN response length mismatch");
  }

  return {
    source: "ANU Quantum Numbers",
    type: query.type,
    length,
    values: parsed.data,
  };
}

export async function fetchQuantumNumbers(query: RandomQuery): Promise<QuantumNumbers> {
  if (query.provider === "debug") {
    if (!config.ALLOW_DEBUG_RANDOM) {
      throw new DebugRandomNotAllowedError();
    }
    return generateDebugRandomNumbers(query);
  }

  const endpoint = new URL(config.AQN_API_URL);
  endpoint.searchParams.set("type", query.type);
  endpoint.searchParams.set("length", String(query.length));
  if (query.size !== undefined) {
    endpoint.searchParams.set("size", String(query.size));
  }

  const response = await request(endpoint, {
    method: "GET",
    dispatcher: new Agent({
      connect: {
        timeout: config.AQN_TIMEOUT_MS,
      },
    }),
    headers: {
      "x-api-key": config.AQN_API_KEY,
      accept: "application/json",
    },
    bodyTimeout: config.AQN_TIMEOUT_MS,
    headersTimeout: config.AQN_TIMEOUT_MS,
  });

  const bodyText = await response.body.text();
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new Error(`AQN request failed with ${response.statusCode}: ${bodyText}`);
  }

  try {
    return normalizeAqnResponse(JSON.parse(bodyText), query);
  } catch {
    throw new Error("AQN returned an unexpected response");
  }
}

export class DebugRandomNotAllowedError extends Error {
  constructor() {
    super("Debug random provider is not enabled");
  }
}

export function generateDebugRandomNumbers(query: RandomQuery): QuantumNumbers {
  return {
    source: "debug",
    type: query.type,
    length: query.length,
    values: Array.from({ length: query.length }, () => generateDebugValue(query.type)),
  };
}

function generateDebugValue(type: RandomQuery["type"]): number | string {
  switch (type) {
    case "uint8":
      return randomInt(0, 256);
    case "uint16":
      return randomInt(0, 65_536);
    case "hex8":
      return randomBytes(1).toString("hex");
    case "hex16":
      return randomBytes(2).toString("hex");
  }
}
