package com.yanxai.tracefolio.resume;

import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.jd.JdRunView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private final ResumeCompiler compiler;
    private final JdRunRepository repository;

    public ResumeController(ResumeCompiler compiler, JdRunRepository repository) {
        this.compiler = compiler;
        this.repository = repository;
    }

    @PostMapping("/compile")
    public ResumeCompileResponse compile(@RequestBody ResumeCompileRequest request) {
        List<EvidenceMatrixRow> matrix = request.matrix();
        if ((matrix == null || matrix.isEmpty()) && request.runId() != null) {
            JdRunView run = repository.find(request.runId());
            matrix = run.evidenceMatrix();
        }
        if (matrix == null || matrix.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先运行 JD evidence match，再生成简历。");
        }

        ResumeCompileResponse response = compiler.compile(request.runId(), matrix);
        if (request.runId() != null) {
            repository.updateResumeDraft(request.runId(), response.draft());
        }
        return response;
    }
}
