package com.nkharade.jdanalyzer.api;

import com.nkharade.jdanalyzer.analysis.AnalysisResult;
import com.nkharade.jdanalyzer.analysis.AnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    public record AnalyzeRequest(
            @NotBlank @Size(max = 50_000) String resume,
            @NotBlank @Size(max = 50_000) String jobDescription
    ) {
    }

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AnalysisResult> analyze(@Valid @RequestBody AnalyzeRequest request) {
        AnalysisResult result = service.analyze(request.resume(), request.jobDescription());
        return ResponseEntity.created(URI.create("/api/analyses/" + result.analysisId())).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResult> get(@PathVariable UUID id) {
        return ResponseEntity.of(service.get(id));
    }
}
