package com.heartnote.app.data

import kotlinx.serialization.Serializable

/** notes/<id>/meta.json (TRD § DATA MODEL), plus summary fields the Library needs. */
@Serializable
data class NoteMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val beatCount: Int = 0,
    val beatSource: String = "rate",
    val smileCount: Int = 0,
)

/** Schema of notes/<id>/signals.json (TRD § DATA MODEL). */
@Serializable
data class SignalsFile(
    val version: Int = 1,
    val durationMs: Long,
    val audioStartEpochMs: Long,
    // Filled by the Day 3 processing pipeline:
    val beatSource: String? = null,
    val beatsMs: List<Long> = emptyList(),
    val breathing: List<CurvePoint> = emptyList(),
    val pulseRate: List<PulsePoint> = emptyList(),
    val smiles: List<SmileSegment> = emptyList(),
    val raw: RawSignals = RawSignals(),
    /** Received notes only: display columns from the shared payload (no raw trace). */
    val dotColumns: List<Float> = emptyList(),
)

@Serializable
data class CurvePoint(val tMs: Long, val v: Float)

@Serializable
data class PulsePoint(val tMs: Long, val bpm: Float)

@Serializable
data class SmileSegment(val startMs: Long, val endMs: Long)

/**
 * All aligned samples, unfiltered — trace samples with tMs < 0 are kept as
 * pre-recording context so beat peaks can be verified offline.
 */
@Serializable
data class RawSignals(
    val bpTrace: List<RawSample> = emptyList(),
    val pulseRate: List<RawSample> = emptyList(),
    val brUpperTrace: List<RawSample> = emptyList(),
    val brRate: List<RawSample> = emptyList(),
    val expressions: List<RawExpression> = emptyList(),
    val talking: List<RawTalking> = emptyList(),
)

@Serializable
data class RawSample(val tMs: Long, val v: Float, val c: Float? = null, val stable: Boolean? = null)

@Serializable
data class RawExpression(val tMs: Long, val type: String, val c: Float)

@Serializable
data class RawTalking(val tMs: Long, val detected: Boolean)
