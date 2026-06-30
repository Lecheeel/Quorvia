import { buildApp } from "./app.js";
import { config } from "./config.js";

const app = buildApp({
  logger: {
    level: config.NODE_ENV === "production" ? "info" : "debug",
  },
});
await app.listen({
  host: config.HOST,
  port: config.PORT,
});
