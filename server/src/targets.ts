import { z } from "zod";
import type { QuantumNumbers, RandomQuery } from "./qrng.js";

const UINT16_MAX_EXCLUSIVE = 65_536;
const METERS_PER_LATITUDE_DEGREE = 111_320;
const DEFAULT_HEAT_GRID_SIZE = 32;
const DEFAULT_POINT_SAMPLE_LIMIT = 512;
const BATCH_LENGTH = 1024;
const KDE_BOUNDARY_CORRECTION_EXTENT = 3;
const KDE_BOUNDARY_CORRECTION_STEPS = 17;
const KDE_MIN_BOUNDARY_MASS = 0.25;
const VOID_CANDIDATE_RADIUS_FRACTION = 0.85;
const VOID_LOW_DENSITY_PERCENTILE = 0.15;
const VOID_MIN_REGION_CELLS = 4;

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
  row: number;
  col: number;
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
  const voidCell = selectVoidRegionCell(densityGrid.validCells, request.origin, request.radiusMeters, request.heatGridSize);
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
  const correctionOffsets = boundaryCorrectionOffsets(bandwidthMeters);
  const cells: DensityCell[] = [];
  const validCells: DensityCell[] = [];

  for (let row = 0; row < size; row += 1) {
    for (let col = 0; col < size; col += 1) {
      const latitude = interpolate(minLatitude, maxLatitude, (row + 0.5) / size);
      const longitude = interpolate(minLongitude, maxLongitude, (col + 0.5) / size);
      const distanceMeters = distanceFromOriginMeters(origin, { latitude, longitude });
      const insideRadius = distanceMeters <= radiusMeters;
      const density = insideRadius
        ? estimateDensity({ latitude, longitude }, samplePoints, origin, radiusMeters, bandwidthMeters, correctionOffsets)
        : 0;
      const cell: DensityCell = {
        latitude,
        longitude,
        density,
        value: 0,
        distanceMeters,
        row,
        col,
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

function selectVoidRegionCell(
  validCells: DensityCell[],
  origin: GeoPoint,
  radiusMeters: number,
  size: number,
): DensityCell {
  const candidateCells = validCells.filter(
    (cell) => cell.distanceMeters <= radiusMeters * VOID_CANDIDATE_RADIUS_FRACTION,
  );
  const candidates = candidateCells.length > 0 ? candidateCells : validCells;
  const threshold = percentile(
    candidates.map((cell) => cell.density),
    VOID_LOW_DENSITY_PERCENTILE,
  );
  const lowestDensity = Math.min(...candidates.map((cell) => cell.density));
  const densitySpan = Math.max(threshold - lowestDensity, Number.EPSILON);
  const lowCellKeys = new Set(candidates.filter((cell) => cell.density <= threshold).map(cellKey));
  const cellByKey = new Map(candidates.map((cell) => [cellKey(cell), cell]));
  const visited = new Set<string>();
  const regions: DensityCell[][] = [];

  for (const cell of candidates) {
    const key = cellKey(cell);
    if (!lowCellKeys.has(key) || visited.has(key)) {
      continue;
    }
    const region: DensityCell[] = [];
    const queue = [cell];
    visited.add(key);
    for (let index = 0; index < queue.length; index += 1) {
      const current = queue[index];
      region.push(current);
      for (const neighbor of neighboringCells(current, size, cellByKey)) {
        const neighborKey = cellKey(neighbor);
        if (lowCellKeys.has(neighborKey) && !visited.has(neighborKey)) {
          visited.add(neighborKey);
          queue.push(neighbor);
        }
      }
    }
    if (region.length >= VOID_MIN_REGION_CELLS) {
      regions.push(region);
    }
  }

  if (regions.length === 0) {
    return minBy(candidates, (cell) => cell.density);
  }

  const selectedRegion = maxBy(regions, (region) => voidRegionScore(region, lowestDensity, densitySpan, radiusMeters));
  return voidRegionCentroid(selectedRegion, origin, threshold);
}

function cellKey(cell: Pick<DensityCell, "row" | "col">): string {
  return `${cell.row}:${cell.col}`;
}

function neighboringCells(cell: DensityCell, size: number, cellByKey: Map<string, DensityCell>): DensityCell[] {
  const neighbors: DensityCell[] = [];
  for (let rowOffset = -1; rowOffset <= 1; rowOffset += 1) {
    for (let colOffset = -1; colOffset <= 1; colOffset += 1) {
      if (rowOffset === 0 && colOffset === 0) {
        continue;
      }
      const row = cell.row + rowOffset;
      const col = cell.col + colOffset;
      if (row < 0 || row >= size || col < 0 || col >= size) {
        continue;
      }
      const neighbor = cellByKey.get(`${row}:${col}`);
      if (neighbor) {
        neighbors.push(neighbor);
      }
    }
  }
  return neighbors;
}

function voidRegionScore(region: DensityCell[], lowestDensity: number, densitySpan: number, radiusMeters: number): number {
  const averageDensity = region.reduce((sum, cell) => sum + cell.density, 0) / region.length;
  const lowDensityScore = 1 - (averageDensity - lowestDensity) / densitySpan;
  const centroidDistance = region.reduce((sum, cell) => sum + cell.distanceMeters, 0) / region.length;
  const centerComfort = 1 - 0.35 * (centroidDistance / (radiusMeters * VOID_CANDIDATE_RADIUS_FRACTION)) ** 2;
  const areaScore = Math.sqrt(region.length);
  return Math.max(lowDensityScore, 0) * Math.max(centerComfort, 0.4) * areaScore;
}

function voidRegionCentroid(region: DensityCell[], origin: GeoPoint, threshold: number): DensityCell {
  let totalWeight = 0;
  let latitudeSum = 0;
  let longitudeSum = 0;
  let densitySum = 0;
  for (const cell of region) {
    const weight = Math.max(threshold - cell.density, 0) + Number.EPSILON;
    totalWeight += weight;
    latitudeSum += cell.latitude * weight;
    longitudeSum += cell.longitude * weight;
    densitySum += cell.density * weight;
  }
  const latitude = latitudeSum / totalWeight;
  const longitude = longitudeSum / totalWeight;
  return {
    latitude,
    longitude,
    density: densitySum / totalWeight,
    value: 0,
    distanceMeters: distanceFromOriginMeters(origin, { latitude, longitude }),
    row: Math.round(region.reduce((sum, cell) => sum + cell.row, 0) / region.length),
    col: Math.round(region.reduce((sum, cell) => sum + cell.col, 0) / region.length),
  };
}

function estimateDensity(
  point: GeoPoint,
  samplePoints: GeoPoint[],
  origin: GeoPoint,
  radiusMeters: number,
  bandwidthMeters: number,
  correctionOffsets: BoundaryCorrectionOffset[],
): number {
  let sum = 0;
  const bandwidthSquared = bandwidthMeters * bandwidthMeters;
  for (const sample of samplePoints) {
    const distanceSquared = squaredDistanceMeters(point, sample, origin.latitude);
    sum += Math.exp(-distanceSquared / (2 * bandwidthSquared));
  }
  const boundaryMass = kernelMassInsideSearchCircle(point, origin, radiusMeters, correctionOffsets);
  return sum / samplePoints.length / Math.max(boundaryMass, KDE_MIN_BOUNDARY_MASS);
}

type BoundaryCorrectionOffset = {
  dxMeters: number;
  dyMeters: number;
  weight: number;
};

function boundaryCorrectionOffsets(bandwidthMeters: number): BoundaryCorrectionOffset[] {
  const offsets: BoundaryCorrectionOffset[] = [];
  const stepCount = KDE_BOUNDARY_CORRECTION_STEPS - 1;
  for (let yIndex = 0; yIndex < KDE_BOUNDARY_CORRECTION_STEPS; yIndex += 1) {
    const unitY = -KDE_BOUNDARY_CORRECTION_EXTENT + (2 * KDE_BOUNDARY_CORRECTION_EXTENT * yIndex) / stepCount;
    for (let xIndex = 0; xIndex < KDE_BOUNDARY_CORRECTION_STEPS; xIndex += 1) {
      const unitX = -KDE_BOUNDARY_CORRECTION_EXTENT + (2 * KDE_BOUNDARY_CORRECTION_EXTENT * xIndex) / stepCount;
      offsets.push({
        dxMeters: unitX * bandwidthMeters,
        dyMeters: unitY * bandwidthMeters,
        weight: Math.exp(-(unitX * unitX + unitY * unitY) / 2),
      });
    }
  }
  return offsets;
}

function kernelMassInsideSearchCircle(
  point: GeoPoint,
  origin: GeoPoint,
  radiusMeters: number,
  offsets: BoundaryCorrectionOffset[],
): number {
  const pointDyMeters = (point.latitude - origin.latitude) * METERS_PER_LATITUDE_DEGREE;
  const pointDxMeters =
    (point.longitude - origin.longitude) *
    METERS_PER_LATITUDE_DEGREE *
    Math.cos((origin.latitude * Math.PI) / 180);
  const radiusSquared = radiusMeters * radiusMeters;
  let insideWeight = 0;
  let totalWeight = 0;

  for (const offset of offsets) {
    totalWeight += offset.weight;
    const dxMeters = pointDxMeters + offset.dxMeters;
    const dyMeters = pointDyMeters + offset.dyMeters;
    if (dxMeters * dxMeters + dyMeters * dyMeters <= radiusSquared) {
      insideWeight += offset.weight;
    }
  }

  return totalWeight <= Number.EPSILON ? 1 : insideWeight / totalWeight;
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

function percentile(values: number[], fraction: number): number {
  if (values.length === 0) {
    throw new Error("Cannot calculate percentile of an empty list");
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.round((sorted.length - 1) * fraction);
  return sorted[index];
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
