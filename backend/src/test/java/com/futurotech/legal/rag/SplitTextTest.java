package com.futurotech.legal.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.futurotech.legal.infrastructure.adapter.out.rag.PgVectorKnowledgeBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class SplitTextTest {

    @Test
    void splitsLongTextIntoChunks() {
        String text = ("Párrafo uno con contenido suficiente.\n"
                + "Párrafo dos con más contenido.\n"
                + "Párrafo tres distinto.\n").repeat(20);

        List<String> chunks = PgVectorKnowledgeBase.splitText(text, 200, 40);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(c -> assertThat(c).isNotBlank());
    }

    @Test
    void shortTextProducesSingleChunk() {
        List<String> chunks = PgVectorKnowledgeBase.splitText("Texto corto.", 1500, 200);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("Texto corto.");
    }

    @Test
    void blankTextProducesNoChunks() {
        List<String> chunks = PgVectorKnowledgeBase.splitText("\n\n   \n", 1500, 200);
        assertThat(chunks).isEmpty();
    }
}
