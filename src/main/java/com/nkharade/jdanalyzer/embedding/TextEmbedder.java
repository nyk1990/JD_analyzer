package com.nkharade.jdanalyzer.embedding;

import java.util.List;

/** Turns text into embedding vectors. One vector per input, same order. */
public interface TextEmbedder {

    List<float[]> embed(List<String> texts);

    int dimensions();
}
