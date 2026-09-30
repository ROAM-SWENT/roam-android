package com.github.se.bootcamp.model.map

interface LocationRepository {
  suspend fun search(query: String): List<Location>
}
