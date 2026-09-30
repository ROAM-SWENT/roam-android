package com.github.roamswent.roam.model.map

interface LocationRepository {
  suspend fun search(query: String): List<Location>
}
