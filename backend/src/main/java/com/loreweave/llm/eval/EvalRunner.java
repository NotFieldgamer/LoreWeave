package com.loreweave.llm.eval;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The evaluation command (BUILD_GUIDE M4). Run:
 * {@code java -jar loreweave-backend.jar --eval} (needs GEMINI_API_KEY set) → scores the contradiction
 * guard on the labeled set, prints precision/recall/F1, then exits. A normal boot (no {@code --eval})
 * skips this entirely, so the flag never affects the running server.
 */
@Component
public class EvalRunner implements ApplicationRunner {

    private final ContradictionEvaluator evaluator;
    private final ApplicationContext ctx;

    public EvalRunner(ContradictionEvaluator evaluator, ApplicationContext ctx) {
        this.evaluator = evaluator;
        this.ctx = ctx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("eval")) return;
        System.out.println();
        System.out.println(evaluator.evaluate().report());
        System.out.println();
        System.exit(SpringApplication.exit(ctx, () -> 0));
    }
}
