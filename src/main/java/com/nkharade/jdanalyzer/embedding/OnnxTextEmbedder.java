package com.nkharade.jdanalyzer.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local sentence embeddings with all-MiniLM-L6-v2 (384 dims) on ONNX Runtime.
 *
 * Replaces Spring AI's TransformersEmbeddingModel, whose mean-pooling step needs DJL's PyTorch engine.
 * PyTorch no longer ships native builds for Intel Macs, so this class does tokenization (DJL HuggingFace
 * tokenizers), inference (ONNX Runtime) and mean pooling + L2 normalisation (plain Java) itself.
 * Same model, same vectors, no PyTorch.
 */
@Component
public class OnnxTextEmbedder implements TextEmbedder {

    private static final Logger log = LoggerFactory.getLogger(OnnxTextEmbedder.class);
    private static final int DIMENSIONS = 384;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;

    public OnnxTextEmbedder(
            @Value("${app.embedding.model-uri}") String modelUri,
            @Value("${app.embedding.tokenizer-uri}") String tokenizerUri,
            @Value("${app.embedding.cache-dir}") String cacheDir) throws IOException, OrtException {

        Path dir = Path.of(cacheDir.replaceFirst("^~", System.getProperty("user.home")));
        Path modelPath = downloadIfMissing(modelUri, dir.resolve("model.onnx"));
        Path tokenizerPath = downloadIfMissing(tokenizerUri, dir.resolve("tokenizer.json"));

        Map<String, String> options = new HashMap<>();
        options.put("truncation", "true");
        options.put("maxLength", "256");
        options.put("padding", "false");
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath, options);

        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(modelPath.toString(), new OrtSession.SessionOptions());
        log.info("Loaded embedding model {} (inputs {})", modelPath, session.getInputNames());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String text : texts) {
            out.add(embedOne(text));
        }
        return out;
    }

    /** One text at a time: no padding needed, and inputs here are short sentences. */
    private float[] embedOne(String text) {
        Encoding enc = tokenizer.encode(text);
        long[] ids = enc.getIds();
        long[] mask = enc.getAttentionMask();
        long[] types = enc.getTypeIds();

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            putIfUsed(inputs, "input_ids", ids);
            putIfUsed(inputs, "attention_mask", mask);
            putIfUsed(inputs, "token_type_ids", types);

            try (OrtSession.Result result = session.run(inputs)) {
                OnnxValue hidden = result.get("last_hidden_state")
                        .orElseGet(() -> result.get(0));
                float[][][] tokens = (float[][][]) hidden.getValue(); // [1][seqLen][384]
                return normalize(meanPool(tokens[0], mask));
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Embedding failed", e);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private void putIfUsed(Map<String, OnnxTensor> inputs, String name, long[] values) throws OrtException {
        if (session.getInputNames().contains(name)) {
            inputs.put(name, OnnxTensor.createTensor(env, new long[][]{values}));
        }
    }

    /** Average of token vectors, counting only real tokens (attention mask = 1). */
    static float[] meanPool(float[][] tokenVectors, long[] mask) {
        int dims = tokenVectors[0].length;
        float[] sum = new float[dims];
        int count = 0;
        for (int t = 0; t < tokenVectors.length; t++) {
            if (mask[t] == 0) continue;
            count++;
            for (int d = 0; d < dims; d++) sum[d] += tokenVectors[t][d];
        }
        if (count > 0) {
            for (int d = 0; d < dims; d++) sum[d] /= count;
        }
        return sum;
    }

    /** Scale to unit length, so cosine similarity equals the dot product. */
    static float[] normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm == 0) return v;
        for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    private static Path downloadIfMissing(String uri, Path target) throws IOException {
        if (Files.exists(target) && Files.size(target) > 0) return target;

        Files.createDirectories(target.getParent());
        log.info("Downloading {} -> {} (one-time)", uri, target);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri)).GET().build();
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download failed with HTTP " + response.statusCode() + ": " + uri);
            }
            try (InputStream in = response.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + uri, e);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return target;
    }

    @PreDestroy
    void close() throws OrtException {
        session.close();
        tokenizer.close();
    }
}
