package com.github.roamswent.roam.model.location

interface LocationProvider {

  /**
   * Returns the current position, or null when location permission is denied, GPS is off, or no fix
   * is available. A null result means the location is omitted from the request.
   */
  suspend fun currentCoordinates(): Coordinates?
}
