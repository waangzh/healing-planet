package com.healingplanet.ai.retrieval;

import java.util.Set;

record PlantCatalogEntry(String canonicalPlantId, String canonicalPlantName, Set<String> names, Set<String> aliases) { }
