# Bolt's Journal

Critical performance learnings and codebase-specific patterns.

## 2026-11-10 - High-Frequency GPS Ring Buffers: ArrayDeque vs List Shifts
**Learning:** In GPS tracking loops running 1–5 Hz, maintaining rolling time-series history (e.g. 600 samples) via `toMutableList()` and `removeAt(0)` causes continuous array reallocations and O(N) arraycopy element shifts on every location tick.
**Action:** Use an internal `ArrayDeque` with O(1) `addLast` and `removeFirst` to back the rolling window, copying out to immutable `toList()` only for StateFlow consumption.
