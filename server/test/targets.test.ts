import assert from "node:assert/strict";
import test from "node:test";
import { buildApp } from "../src/app.js";
import { DebugRandomNotAllowedError, type RandomQuery } from "../src/qrng.js";
import { batchCountForMode, parseTargetGenerationRequest, valuesToSamplePoints } from "../src/targets.js";

test("target request parser defaults to standard attractor generation", () => {
  assert.deepEqual(
    parseTargetGenerationRequest({
      origin: { latitude: 39.9087, longitude: 116.3975 },
      radiusMeters: 3000,
    }),
    {
      origin: { latitude: 39.9087, longitude: 116.3975 },
      radiusMeters: 3000,
      mode: "standard",
      targetType: "attractor",
      provider: "aqn",
      heatGridSize: 32,
      pointSampleLimit: 512,
    },
  );
});

test("target request parser rejects invalid target types", () => {
  assert.throws(
    () =>
      parseTargetGenerationRequest({
        origin: { latitude: 39.9087, longitude: 116.3975 },
        radiusMeters: 3000,
        targetType: "power",
      }),
    /Invalid option/,
  );
});

test("batch count maps standard and fine modes", () => {
  assert.equal(batchCountForMode("standard"), 2);
  assert.equal(batchCountForMode("fine"), 4);
});

test("uint16 values convert to bounded sample points", () => {
  const origin = { latitude: 39.9087, longitude: 116.3975 };
  const points = valuesToSamplePoints([0, 0, 65_535, 16_384], origin, 3000);

  assert.equal(points.length, 2);
  assert.ok(points.every((point) => distanceMeters(origin, point) <= 3001));
});

test("standard target generation endpoint requests two 1024-value batches", async () => {
  const seenQueries: RandomQuery[] = [];
  const app = buildApp({
    fetcher: async (query) => {
      seenQueries.push(query);
      return {
        source: query.provider === "debug" ? "debug" : "ANU Quantum Numbers",
        type: query.type,
        length: query.length,
        values: deterministicValues(query.length),
      };
    },
  });

  try {
    const response = await app.inject({
      method: "POST",
      url: "/v1/targets/generate",
      payload: {
        origin: { latitude: 39.9087, longitude: 116.3975 },
        radiusMeters: 3000,
        mode: "standard",
        targetType: "attractor",
        provider: "debug",
        heatGridSize: 16,
        pointSampleLimit: 64,
      },
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.mode, "standard");
    assert.equal(body.targetType, "attractor");
    assert.equal(body.batchCount, 2);
    assert.equal(body.randomValueCount, 2048);
    assert.equal(body.samplePointCount, 1024);
    assert.equal(body.samplePoints.length, 64);
    assert.equal(body.heatGrid.size, 16);
    assert.equal(body.target.role, "attractor");
    assert.deepEqual(
      seenQueries,
      [
        { type: "uint16", length: 1024, provider: "debug" },
        { type: "uint16", length: 1024, provider: "debug" },
      ],
    );
  } finally {
    await app.close();
  }
});

test("fine target generation endpoint requests four 1024-value batches", async () => {
  const seenQueries: RandomQuery[] = [];
  const app = buildApp({
    fetcher: async (query) => {
      seenQueries.push(query);
      return {
        source: "ANU Quantum Numbers",
        type: query.type,
        length: query.length,
        values: deterministicValues(query.length),
      };
    },
  });

  try {
    const response = await app.inject({
      method: "POST",
      url: "/v1/targets/generate",
      payload: {
        origin: { latitude: 39.9087, longitude: 116.3975 },
        radiusMeters: 3000,
        mode: "fine",
        targetType: "void",
        heatGridSize: 16,
        pointSampleLimit: 0,
      },
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.batchCount, 4);
    assert.equal(body.randomValueCount, 4096);
    assert.equal(body.samplePointCount, 2048);
    assert.equal(body.samplePoints.length, 0);
    assert.equal(body.target.role, "void");
    assert.equal(seenQueries.length, 4);
    assert.ok(seenQueries.every((query) => query.length === 1024));
  } finally {
    await app.close();
  }
});

test("void target avoids boundary-biased edge cells", async () => {
  const origin = { latitude: 39.9087, longitude: 116.3975 };
  const radiusMeters = 3000;
  const app = buildApp({
    fetcher: async (query) => ({
      source: "debug",
      type: query.type,
      length: query.length,
      values: deterministicValues(query.length),
    }),
  });

  try {
    const response = await app.inject({
      method: "POST",
      url: "/v1/targets/generate",
      payload: {
        origin,
        radiusMeters,
        mode: "standard",
        targetType: "void",
        provider: "debug",
        heatGridSize: 32,
      },
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.target.role, "void");
    assert.ok(distanceMeters(origin, body.target) <= radiusMeters * 0.85 + 1);
    assert.equal(
      body.heatGrid.cells.some(
        (cell: { latitude: number; longitude: number }) =>
          Math.abs(cell.latitude - body.target.latitude) < 1e-12 &&
          Math.abs(cell.longitude - body.target.longitude) < 1e-12,
      ),
      false,
    );
  } finally {
    await app.close();
  }
});

test("anomaly target generation chooses a density extremum", async () => {
  const app = buildApp({
    fetcher: async (query) => ({
      source: "debug",
      type: query.type,
      length: query.length,
      values: deterministicValues(query.length),
    }),
  });

  try {
    const response = await app.inject({
      method: "POST",
      url: "/v1/targets/generate",
      payload: {
        origin: { latitude: 39.9087, longitude: 116.3975 },
        radiusMeters: 3000,
        targetType: "anomaly",
        provider: "debug",
        heatGridSize: 16,
      },
    });

    assert.equal(response.statusCode, 200);
    const body = response.json();
    assert.equal(body.targetType, "anomaly");
    assert.ok(["attractor", "void"].includes(body.target.role));
    assert.ok(Number.isFinite(body.attractor.score));
    assert.ok(Number.isFinite(body.void.score));
  } finally {
    await app.close();
  }
});

test("target generation rejects debug provider when server config disables it", async () => {
  const app = buildApp({
    fetcher: async () => {
      throw new DebugRandomNotAllowedError();
    },
  });

  try {
    const response = await app.inject({
      method: "POST",
      url: "/v1/targets/generate",
      payload: {
        origin: { latitude: 39.9087, longitude: 116.3975 },
        radiusMeters: 3000,
        provider: "debug",
      },
    });

    assert.equal(response.statusCode, 403);
    assert.equal(response.json().error, "debug_random_disabled");
  } finally {
    await app.close();
  }
});

function deterministicValues(length: number): number[] {
  return Array.from({ length }, (_, index) => (index * 7919 + 12345) % 65_536);
}

function distanceMeters(a: { latitude: number; longitude: number }, b: { latitude: number; longitude: number }) {
  const latMeters = (a.latitude - b.latitude) * 111_320;
  const lngMeters = (a.longitude - b.longitude) * 111_320 * Math.cos((a.latitude * Math.PI) / 180);
  return Math.sqrt(latMeters ** 2 + lngMeters ** 2);
}
