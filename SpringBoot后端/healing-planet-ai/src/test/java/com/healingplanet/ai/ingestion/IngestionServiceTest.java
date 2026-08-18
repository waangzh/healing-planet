package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.retrieval.PlantEntityResolver;
import com.healingplanet.ai.retrieval.SparseIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionServiceTest {

    @Test
    void plantIndexShouldRebuildEntityCollectionAndRefreshCatalog() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        when(repository.findPlantEntities()).thenReturn(List.of(
                new KnowledgeRepository.PlantEntityRow("1", "Epipremnum aureum", "绿萝")));
        when(repository.findPlants()).thenReturn(List.of(
                new KnowledgeRepository.PlantRow("1", "Epipremnum aureum", "绿萝",
                        null, null, null, null, null, null)));

        SparseIndexService sparseIndex = mock(SparseIndexService.class);
        when(sparseIndex.ids(any())).thenReturn(Set.of());
        VectorStore plantStore = mock(VectorStore.class);
        VectorStore entityStore = mock(VectorStore.class);
        VectorStore communityStore = mock(VectorStore.class);
        VectorStore diseaseStore = mock(VectorStore.class);
        PlantEntityResolver resolver = mock(PlantEntityResolver.class);

        IngestionService service = new IngestionService(repository, new KnowledgeDocumentConverter(),
                new PlantEntityDocumentConverter(), resolver, sparseIndex, plantStore, entityStore,
                communityStore, diseaseStore, mock(DiseaseKnowledgeRepository.class),
                mock(DiseaseKnowledgeConverter.class));

        service.indexPlants();

        verify(entityStore).add(anyList());
        verify(sparseIndex).replaceAll(eq(KnowledgeSource.PLANT_ENTITY), anyList());
        verify(resolver).refreshCatalog();
    }
}
