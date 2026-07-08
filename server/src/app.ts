import cors from "@fastify/cors";
import rateLimit from "@fastify/rate-limit";
import Fastify from "fastify";
import { ZodError } from "zod";
import { corsOrigins } from "./config.js";
import {
  DebugRandomNotAllowedError,
  fetchQuantumNumbers,
  parseRandomQuery,
  type QuantumNumbers,
  type RandomQuery,
} from "./qrng.js";
import { generateTarget, parseTargetGenerationRequest } from "./targets.js";

type BuildAppOptions = {
  logger?: boolean | { level: string };
  fetcher?: (query: RandomQuery) => Promise<QuantumNumbers>;
};

export function buildApp(options: BuildAppOptions = {}) {
  const fetcher = options.fetcher ?? fetchQuantumNumbers;

  const app = Fastify({
    logger: options.logger ?? false,
  });

  app.register(cors, {
    origin: corsOrigins,
  });

  app.register(rateLimit, {
    max: 60,
    timeWindow: "1 minute",
  });

  app.get("/health", async () => ({
    ok: true,
    service: "quorvia-qrng-proxy",
  }));

  app.get("/v1/qrng", async (request, reply) => {
    try {
      const query = parseRandomQuery(request.query);
      const data = await fetcher(query);
      return data;
    } catch (error) {
      if (error instanceof ZodError) {
        return reply.code(400).send({
          error: "invalid_query",
          details: error.issues,
        });
      }

      if (error instanceof DebugRandomNotAllowedError) {
        return reply.code(403).send({
          error: "debug_random_disabled",
          message: "Debug random provider is disabled on this server.",
        });
      }

      request.log.error(error);
      return reply.code(502).send({
        error: "qrng_unavailable",
        message: "Quantum random source is unavailable. No fallback was used.",
      });
    }
  });

  app.post("/v1/targets/generate", async (request, reply) => {
    try {
      const generationRequest = parseTargetGenerationRequest(request.body);
      return await generateTarget(generationRequest, fetcher);
    } catch (error) {
      if (error instanceof ZodError) {
        return reply.code(400).send({
          error: "invalid_target_request",
          details: error.issues,
        });
      }

      if (error instanceof DebugRandomNotAllowedError) {
        return reply.code(403).send({
          error: "debug_random_disabled",
          message: "Debug random provider is disabled on this server.",
        });
      }

      request.log.error(error);
      return reply.code(502).send({
        error: "target_generation_unavailable",
        message: "Target generation is unavailable. No fallback was used.",
      });
    }
  });

  return app;
}
