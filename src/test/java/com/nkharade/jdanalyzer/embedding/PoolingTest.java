package com.nkharade.jdanalyzer.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PoolingTest {

    @Test
    void meanPoolIgnoresMaskedTokens() {
        float[][] tokens = {{1, 2}, {3, 4}, {100, 100}};
        long[] mask = {1, 1, 0};

        assertThat(OnnxTextEmbedder.meanPool(tokens, mask)).containsExactly(2f, 3f);
    }

    @Test
    void normalizeGivesUnitLength() {
        float[] v = OnnxTextEmbedder.normalize(new float[]{3, 4});

        assertThat(v[0]).isCloseTo(0.6f, within(1e-6f));
        assertThat(v[1]).isCloseTo(0.8f, within(1e-6f));
    }
}
