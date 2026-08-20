package com.healingplanet.ai.retrieval;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ngram.NGramTokenizer;

public class ChineseNgramAnalyzer extends Analyzer {
    private final int minNgram;
    private final int maxNgram;

    public ChineseNgramAnalyzer(int minNgram, int maxNgram) {
        if (minNgram < 1 || maxNgram < minNgram) {
            throw new IllegalArgumentException("N-gram range must satisfy 1 <= minNgram <= maxNgram");
        }
        this.minNgram = minNgram;
        this.maxNgram = maxNgram;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new NGramTokenizer(minNgram, maxNgram);
        return new TokenStreamComponents(tokenizer);
    }
}
