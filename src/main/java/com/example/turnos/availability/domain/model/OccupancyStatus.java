package com.example.turnos.availability.domain.model;

// Los dos orígenes de una ocupación (REF §8). Si un slot aparece en los dos, la cátedra informa
// CONFIRMED.
public enum OccupancyStatus {
	CONFIRMED,
	HELD
}
