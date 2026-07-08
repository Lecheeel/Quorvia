import { z } from "zod";
import type { QuantumNumbers, RandomQuery } from "./qrng.js";

const UINT16_MAX_EXCLUSIVE = 65_536;
const METERS_PER_LATITUDE_DEGREE = 111_320;
const DEFAULT_HEAT_GRID_SIZE = 32;
const DEFAULT_POINT_SAMPLE_LIMIT = 512;
const BATCH_LENGTH = 1024;

const geoPointSchema = z.object({
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
});

const targetRequestSchema = z.object({
  origin: geoPointSchema,
  radiusMeters: z.coerce.number().int().min(100).max(50_000),
  mode: z.enum(["standard", "fine"]).default("standard"),
  targetType: z.enum(["attractor", "void", "anomaly"]).default("attractor"),
  provider: z.enum(["aqn", "debug"]).default("aqn"),
  heatGridSize: z.coerce.number().int().min(16).max(48).default(DEFAULT_HEAT_GRID_SIZE),
  pointSampleLimit: z.coerce.number().int().min(0).max(2048).default(DEFAULT_POINT_SAMPLE_LIMIT),
});

export type GeoPoint = z.infer<typeof geoPointSchema>;
export type TargetGenerationRequest = z.infer<typeof targetRequestSchema>;
export type TargetGenerationMode = TargetGenerationRequest["mode"];
export type TargetType = TargetGenerationRequest["targetType"];

export type TargetPoint = GeoPoint & {
  role: "attractor" | "void";
  density: number;
  score: number;
};

export type HeatGridCell = GeoPoint & {
  value: number;
};

export type HeatGrid = {
  size: number;
  minLatitude: number;
  maxLatitude: number;
  minLongitude: number;
  maxLongitude: number;
  cells: HeatGridCell[];
};

export type TargetGenerationResponse = {
  source: QuantumNumbers["source"];
  provider: TargetGenerationRequest["provider"];
  mode: TargetGenerationMode;
  targetType: TargetType;
  randomType: "uint16";
  batchCount: number;
  randomValueCount: number;
  samplePointCount: number;
  origin: GeoPoint;
  radiusMeters: number;
  target: TargetPoint;
  attractor: TargetPoint;
  void: TargetPoint;
  heatGrid: HeatGrid;
  samplePoints: GeoPoint[];
};

export type QuantumNumberFetcher = (query: RandomQuery) => Promise<QuantumNumbers>;

type DensityCell = GeoPoint & {
  density: number;
  value: number;
  distanceMeters: number;
};

export function parseTargetGenerationRequest(input: unknown): TargetGenerationRequest {
  return targetRequestSchema.parse(input);
}

export async function generateTarget(
  request: TargetGenerationRequest,
  fetcher: QuantumNumberFetcher,
): Promise<TargetGenerationResponse> {
  const batchCount = batchCountForMode(request.mode);
  const batches = await Promise.all(
    Array.from({ length: batchCount }, () =>
      fetcher({
        type: "uint16",
        length: BATCH_LENGTH,
        provider: request.provider,
      }),
    ),
  );
  const values = batches.flatMap((batch) => batch.values);
  if (!values.every((value): value is number => Number.isInteger(value))) {
    throw new Error("Target generation requires integer uint16 values");
  }

  const samplePoints = valuesToSamplePoints(values, request.origin, request.radiusMeters);
  const densityGrid = calculateDensityGrid(samplePoints, request.origin, request.radiusMeters, request.heatGridSize);
  const attractorCell = maxBy(densityGrid.validCells, (cell) => cell.density);
  const voidCells = densityGrid.validCells.filter((cell) => cell.distanceMeters <= request.radiusMeters * 0.9);
  const voidCell = minBy(voidCells.length > 0 ? voidCells : densityGrid.validCells, (cell) => cell.density);
  const stats = densityStats(densityGrid.validCells);

  const attractor = cellToTarget(attractorCell, "attractor", stats);
  const voidPoint = cellToTarget(voidCell, "void", stats);
  const target = selectTarget(request.targetType, attractor, voidPoint);

  return {
    source: batches[0]?.source ?? "ANU Quantum Numbers",
    provider: request.provider,
    mode: request.mode,
    targetType: request.targetType,
    randomType: "uint16",
    batchCount,
    randomValueCount: values.length,
    samplePointCount: samplePoints.length,
    origin: request.origin,
    radiusMeters: request.radiusMeters,
    target,
    attractor,
    void: voidPoint,
    heatGrid: {
      size: request.heatGridSize,
      minLatitude: densityGrid.minLatitude,
      maxLatitude: densityGrid.maxLatitude,
      minLongitude: densityGrid.minLongitude,
      maxLongitude: densityGrid.maxLongitude,
      cells: densityGrid.cells.map(({ latitude, longitude, value }) => ({
        latitude,
        longitude,
        value,
      })),
    },
    samplePoints: downsamplePoints(samplePoints, request.pointSampleLimit),
  };
}

export function batchCountForMode(mode: TargetGenerationMode): number {
  return mode === "fine" ? 4 : 2;
}

export function valuesToSamplePoints(values: number[], origin: GeoPoint, radiusMeters: number): GeoPoint[] {
  const points: GeoPoint[] = [];
  for (let index = 0; index + 1 < values.length; index += 2) {
    points.push(randomLocation(origin, radiusMeters, normalizeUInt16(values[index]), normalizeUInt16(values[index + 1])));
  }
  return points;
}

function randomLocation(origin: GeoPoint, radiusMeters: number, randomA: number, randomB: number): GeoPoint {
  const distanceMeters = Math.sqrt(randomA) * radiusMeters;
  const angle = randomB * 2 * Math.PI;
  const deltaX = distanceMeters * Math.cos(angle);
  const deltaY = distanceMeters * Math.sin(angle);
  const longitudeScale = METERS_PER_LATITUDE_DEGREE * Math.cos((origin.latitude * Math.PI) / 180);

  return {
    latitude: origin.latitude + deltaY / METERS_PER_LATITUDE_DEGREE,
    longitude: origin.longitude + (longitudeScale === 0 ? 0 : deltaX / longitudeScale),
  };
}

function normalizeUInt16(value: number): number {
  return Math.min(Math.max(value, 0), UINT16_MAX_EXCLUSIVE - 1) / UINT16_MAX_EXCLUSIVE;
}

function calculateDensityGrid(samplePoints: GeoPoint[], origin: GeoPoint, radiusMeters: number, size: number) {
  const latDelta = radiusMeters / METERS_PER_LATITUDE_DEGREE;
  const longitudeScale = METERS_PER_LATITUDE_DEGREE * Math.cos((origin.latitude * Math.PI) / 180);
  const lonDelta = longitudeScale === 0 ? 0 : radiusMeters / longitudeScale;
  const minLatitude = origin.latitude - latDelta;
  const maxLatitude = origin.latitude + latDelta;
  const minLongitude = origin.longitude - lonDelta;
  const maxLongitude = origin.longitude + lonDelta;
  const bandwidthMeters = Math.max(radiusMeters / 8, 75);
  const cells: DensityCell[] = [];
  const validCells: DensityCell[] = [];

  for (let row = 0; row < size; row += 1) {
    for (let col = 0; col < size; col += 1) {
      const latitude = interpolate(minLatitude, maxLatitude, (row + 0.5) / size);
      const longitude = interpolate(minLongitude, maxLongitude, (col + 0.5) / size);
      const distanceMeters = distanceFromOriginMeters(origin, { latitude, longitude });
      const insideRadius = distanceMeters <= radiusMeters;
      const density = insideRadius ? estimateDensity({ latitude, longitude }, samplePoints, origin, bandwidthMeters) : 0;
      const cell: DensityCell = {
        latitude,
        longitude,
        density,
        value: 0,
        distanceMeters,
      };
      cells.push(cell);
      if (insideRadius) {
        validCells.push(cell);
      }
    }
  }

  const minDensity = Math.min(...validCells.map((cell) => cell.density));
  const maxDensity = Math.max(...validCells.map((cell) => cell.density));
  const span = maxDensity - minDensity;
  for (const cell of cells) {
    cell.value = span <= Number.EPSILON ? 0 : (cell.density - minDensity) / span;
  }

  return {
    cells,
    validCells,
    minLatitude,
    maxLatitude,
    minLongitude,
    maxLongitude,
  };
}

function estimateDensity(point: GeoPoint, samplePoints: GeoPoint[], origin: GeoPoint, bandwidthMeters: number): number {
  let sum = 0;
  const bandwidthSquared = bandwidthMeters * bandwidthMeters;
  for (const sample of samplePoints) {
    const distanceSquared = squaredDistanceMeters(point, sample, origin.latitude);
    sum += Math.exp(-distanceSquared / (2 * bandwidthSquared));
  }
  return sum / samplePoints.length;
}

function squaredDistanceMeters(a: GeoPoint, b: GeoPoint, referenceLatitude: number): number {
  const dy = (a.latitude - b.latitude) * METERS_PER_LATITUDE_DEGREE;
  const dx =
    (a.longitude - b.longitude) *
    METERS_PER_LATITUDE_DEGREE *
    Math.cos((referenceLatitude * Math.PI) / 180);
  return dx * dx + dy * dy;
}

function distanceFromOriginMeters(origin: GeoPoint, point: GeoPoint): number {
  return Math.sqrt(squaredDistanceMeters(origin, point, origin.latitude));
}

function densityStats(cells: DensityCell[]) {
  const mean = cells.reduce((sum, cell) => sum + cell.density, 0) / cells.length;
  const variance = cells.reduce((sum, cell) => sum + (cell.density - mean) ** 2, 0) / cells.length;
  const stddev = Math.sqrt(variance);
  return { mean, stddev };
}

function cellToTarget(cell: DensityCell, role: TargetPoint["role"], stats: { mean: number; stddev: number }): TargetPoint {
  const signedScore = stats.stddev <= Number.EPSILON ? 0 : (cell.density - stats.mean) / stats.stddev;
  return {
    latitude: cell.latitude,
    longitude: cell.longitude,
    role,
    density: cell.density,
    score: role === "void" ? Math.abs(Math.min(signedScore, 0)) : Math.abs(Math.max(signedScore, 0)),
  };
}

function selectTarget(targetType: TargetType, attractor: TargetPoint, voidPoint: TargetPoint): TargetPoint {
  switch (targetType) {
    case "attractor":
      return attractor;
    case "void":
      return voidPoint;
    case "anomaly":
      return attractor.score >= voidPoint.score ? attractor : voidPoint;
  }
}

function downsamplePoints(points: GeoPoint[], limit: number): GeoPoint[] {
  if (limit <= 0) {
    return [];
  }
  if (points.length <= limit) {
    return points;
  }
  const step = points.length / limit;
  return Array.from({ length: limit }, (_, index) => points[Math.floor(index * step)]);
}

function interpolate(min: number, max: number, fraction: number): number {
  return min + (max - min) * fraction;
}

function maxBy<T>(items: T[], score: (item: T) => number): T {
  if (items.length === 0) {
    throw new Error("Cannot select from an empty list");
  }
  return items.reduce((best, item) => (score(item) > score(best) ? item : best));
}

function minBy<T>(items: T[], score: (item: T) => number): T {
  if (items.length === 0) {
    throw new Error("Cannot select from an empty list");
  }
  return items.reduce((best, item) => (score(item) < score(best) ? item : best));
}
