package com.yanxai.tracefolio.gaps;

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
@RequestMapping("/api/gaps")
public class GapController {

    private final GitHubProjectScout scout;
    private final JdRunRepository repository;

    public GapController(GitHubProjectScout scout, JdRunRepository repository) {
        this.scout = scout;
        this.repository = repository;
    }

    @PostMapping("/projects")
    public GapProjectResponse projects(@RequestBody GapProjectRequest request) {
        List<GapInput> gaps = request.gaps();
        if ((gaps == null || gaps.isEmpty()) && request.runId() != null) {
            JdRunView run = repository.find(request.runId());
            gaps = scout.gapsFromMatrix(run.evidenceMatrix());
        }
        if (gaps == null || gaps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "没有可规划的缺口能力。");
        }

        List<GapProjectPlan> projects = scout.planProjects(gaps, request.targetDays() == null ? 5 : request.targetDays());
        if (request.runId() != null) {
            repository.updateGapProjects(request.runId(), projects);
        }
        return new GapProjectResponse(request.runId(), projects);
    }
}
