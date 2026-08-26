package com.healingplanet.ai.retrieval;

import java.util.Map;
import java.util.Set;

record PlantCatalogEntry(String canonicalPlantId, String canonicalPlantName, Set<String> names,
                         Set<String> aliases, Map<String, PlantNameType> nameTypes) { }
