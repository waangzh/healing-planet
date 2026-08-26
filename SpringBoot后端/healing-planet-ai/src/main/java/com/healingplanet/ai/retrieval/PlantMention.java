package com.healingplanet.ai.retrieval;

import java.util.List;

record PlantMention(String text, int start, int end, List<PlantNameBinding> bindings) { }
