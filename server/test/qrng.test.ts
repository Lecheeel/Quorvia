import assert from "node:assert/strict";
import test from "node:test";
import { buildApp } from "../src/app.js";
import {
  DebugRandomNotAllowedError,
  generateDebugRandomNumbers,
  normalizeAqnResponse,
  parseRandomQuery,
  type RandomQuery,
} from "../src/qrng.js";

test("parseRandomQuery applies safe defaults", () => {
  assert.deepEqual(parseRandomQuery({}), {
    type: "uint16",
    length: 32,
    provider: "aqn",
  });
});

test("parseRandomQuery rejects unsupported random types", () => {
  assert.throws(
    () => parseRandomQuery({ type: "local", length: 4 }),
    /Invalid option/,
  );
});

test("parseRandomQuery bounds request length", () => {
  assert.throws(
    () => parseRandomQuery({ type: "uint16", length: 2048 }),
    /Too big/,
  );
});

test("normalizeAqnResponse accepts valid ANU payloads", () => {
  assert.deepEqual(
    normalizeAqnResponse(
      {
        success: true,
        type: "uint16",
        length: "2",
        data: [123, 456],
      },
      { type: "uint16", length: 2, provider: "aqn" },
    ),
    {
      source: "ANU Quantum Numbers",
      type: "uint16",
      length: 2,
      values: [123, 456],
    },
  );
});

test("normalizeAqnResponse rejects mismatched payload lengths", () => {
  assert.throws(
    () =>
      normalizeAqnResponse(
        {
          success: true,
          type: "uint16",
          length: "3",
          data: [123, 456],
        },
        { type: "uint16", length: 3, provider: "aqn" },
      ),
    /length mismatch/,
  );
});

test("normalizeAqnResponse rejects unsuccessful upstream payloads", () => {
  assert.throws(
    () =>
      normalizeAqnResponse(
        {
          success: false,
          type: "uint16",
          length: "2",
          data: [123, 456],
        },
        { type: "uint16", length: 2, provider: "aqn" },
      ),
    /Invalid input/,
  );
});

test("health endpoint returns service identity", async () => {
  const app = buildApp();
  try {
    const response = await app.inject({
      method: "GET",
      url: "/health",
    });

    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), {
      ok: true,
      service: "quorvia-qrng-proxy",
    });
  } finally {
    await app.close();
  }
});

test("qrng endpoint rejects invalid queries before upstream calls", async () => {
  let upstreamCalled = false;
  const app = buildApp({
    fetcher: async (query) => {
      upstreamCalled = true;
      return {
        source: "ANU Quantum Numbers",
        type: query.type,
        length: query.length,
        values: [],
      };
    },
  });
  try {
    const response = await app.inject({
      method: "GET",
      url: "/v1/qrng?type=local&length=4",
    });

    assert.equal(response.statusCode, 400);
    assert.equal(response.json().error, "invalid_query");
    assert.equal(upstreamCalled, false);
  } finally {
    await app.close();
  }
});

test("qrng endpoint returns normalized upstream values", async () => {
  const seenQueries: RandomQuery[] = [];
  const app = buildApp({
    fetcher: async (query) => {
      seenQueries.push(query);
      return {
        source: "ANU Quantum Numbers",
        type: query.type,
        length: query.length,
        values: [1, 2, 3, 4],
      };
    },
  });

  try {
    const response = await app.inject({
      method: "GET",
      url: "/v1/qrng?type=uint16&length=4",
    });

    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), {
      source: "ANU Quantum Numbers",
      type: "uint16",
      length: 4,
      values: [1, 2, 3, 4],
    });
    assert.deepEqual(seenQueries, [{ type: "uint16", length: 4, provider: "aqn" }]);
  } finally {
    await app.close();
  }
});

test("generateDebugRandomNumbers marks debug source", () => {
  const result = generateDebugRandomNumbers({
    type: "uint16",
    length: 4,
    provider: "debug",
  });

  assert.equal(result.source, "debug");
  assert.equal(result.type, "uint16");
  assert.equal(result.length, 4);
  assert.equal(result.values.length, 4);
  assert.ok(result.values.every((value) => Number.isInteger(value)));
});

test("qrng endpoint can return debug provider values when fetcher allows it", async () => {
  const app = buildApp({
    fetcher: async (query) => generateDebugRandomNumbers(query),
  });

  try {
    const response = await app.inject({
      method: "GET",
      url: "/v1/qrng?type=uint8&length=3&provider=debug",
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.source, "debug");
    assert.equal(body.type, "uint8");
    assert.equal(body.length, 3);
    assert.equal(body.values.length, 3);
  } finally {
    await app.close();
  }
});

test("qrng endpoint rejects debug provider when server config disables it", async () => {
  const app = buildApp({
    fetcher: async () => {
      throw new DebugRandomNotAllowedError();
    },
  });

  try {
    const response = await app.inject({
      method: "GET",
      url: "/v1/qrng?type=uint8&length=3&provider=debug",
    });

    assert.equal(response.statusCode, 403);
    assert.deepEqual(response.json(), {
      error: "debug_random_disabled",
      message: "Debug random provider is disabled on this server.",
    });
  } finally {
    await app.close();
  }
});

test("qrng endpoint fails closed when upstream is unavailable", async () => {
  const app = buildApp({
    fetcher: async () => {
      throw new Error("upstream unavailable");
    },
  });

  try {
    const response = await app.inject({
      method: "GET",
      url: "/v1/qrng?type=uint16&length=4",
    });

    assert.equal(response.statusCode, 502);
    assert.deepEqual(response.json(), {
      error: "qrng_unavailable",
      message: "Quantum random source is unavailable. No fallback was used.",
    });
  } finally {
    await app.close();
  }
});
