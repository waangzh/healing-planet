package com.healingplanet.ai.retrieval;

import java.util.Set;

record PlantCatalogEntry(String canonicalPlantId, Set<String> names, Set<String> aliases) { }
