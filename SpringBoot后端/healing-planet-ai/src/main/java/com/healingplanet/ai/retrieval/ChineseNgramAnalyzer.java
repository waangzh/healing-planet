package com.healingplanet.ai.retrieval;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.ngram.NGramTokenizer;

public class ChineseNgramAnalyzer extends Analyzer {
    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new NGramTokenizer(1, 3);
        return new TokenStreamComponents(tokenizer);
    }
}
