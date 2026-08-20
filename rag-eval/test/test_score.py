import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent.parent / "score.py"
SPEC = importlib.util.spec_from_file_location("score", MODULE_PATH)
score = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = score
SPEC.loader.exec_module(score)


def golden(case_id, outcome="ANSWER", evidence_types=None, refs=None):
    return {
        "id": case_id,
        "query": "测试问题",
        "expected_outcome": outcome,
        "expected_evidence_types": evidence_types or [],
        "gold_evidence_refs": refs or [],
    }


def raw(case_id, answer, evidence=None, latency=100):
    return {
        "case_id": case_id,
        "answer": answer,
        "evidence": evidence or [],
        "latency_ms": latency,
    }


class ScoreTest(unittest.TestCase):
    def test_scores_retrieval_citations_and_latency(self):
        evidence = [{
            "type": "CARE_GUIDE",
            "sourceId": "1",
            "metadata": {"knowledgeType": "LIGHT"},
        }]
        summary = score.score_cases(
            [golden("care", refs=[{"source_id": "1", "knowledge_type": "LIGHT"}], evidence_types=["CARE_GUIDE"])],
            [raw("care", "结论 [E1] 另一个错误引用 [E2]", evidence, latency=100)],
        )
        metrics = summary["metrics"]
        self.assertEqual(metrics["knowledge_recall_at_6"]["value"], 1.0)
        self.assertEqual(metrics["precision_at_6"],
                         {"value": 0.166667, "numerator": 1, "denominator": 6, "eligible_case_count": 1})
        self.assertEqual(metrics["required_evidence_type_hit"]["value"], 1.0)
        self.assertEqual(metrics["citation_index_validity"], {"value": 0.5, "numerator": 1, "denominator": 2})
        self.assertEqual(metrics["latency_ms"]["p50"], 100.0)

    def test_matches_qualified_evidence_type(self):
        evidence = [{
            "type": "CARE_GUIDE",
            "metadata": {"knowledgeType": "WATERING"},
        }]
        summary = score.score_cases(
            [golden("care", evidence_types=["CARE_GUIDE:WATERING"])],
            [raw("care", "回答", evidence)],
        )
        self.assertEqual(summary["metrics"]["required_evidence_type_hit"]["value"], 1.0)

    def test_precision_at_6_uses_fixed_cutoff_unique_refs_and_eligible_cases(self):
        case = golden("precision", refs=[
            {"source_id": "relevant-1", "knowledge_type": "LIGHT"},
            {"source_id": "relevant-2", "knowledge_type": "WATERING"},
        ])
        evidence = [
            {"type": "LIVE_STATE"},
            {"type": "CARE_GUIDE", "sourceId": "relevant-1", "metadata": {"knowledgeType": "LIGHT"}},
            {"type": "CARE_GUIDE", "sourceId": "relevant-1", "metadata": {"knowledgeType": "LIGHT"}},
            *[
                {"type": "CARE_GUIDE", "sourceId": f"irrelevant-{index}",
                 "metadata": {"knowledgeType": "GENERAL_CARE"}}
                for index in range(4)
            ],
            {"type": "CARE_GUIDE", "sourceId": "relevant-2", "metadata": {"knowledgeType": "WATERING"}},
        ]
        no_gold_case = golden("no-gold", outcome="INSUFFICIENT_KNOWLEDGE")

        summary = score.score_cases(
            [case, no_gold_case],
            [raw("precision", "回答", evidence), raw("no-gold", "当前知识库中没有足够证据回答这个问题。")],
        )

        self.assertEqual(summary["metrics"]["knowledge_recall_at_6"]["value"], 0.5)
        self.assertEqual(summary["metrics"]["precision_at_6"],
                         {"value": 0.166667, "numerator": 1, "denominator": 6,
                          "eligible_case_count": 1})
        self.assertEqual(summary["case_results"][0]["matched_precision_evidence_count"], 1)
        self.assertIsNone(summary["case_results"][1]["matched_precision_evidence_count"])

    def test_scores_evidence_selection_constraints(self):
        case = golden("selection", evidence_types=["CARE_GUIDE:WATERING"])
        case["selection_expectations"] = {
            "max_selected_per_source_knowledge_type": 1,
            "min_distinct_plant_knowledge_types": 2,
            "required_plant_knowledge_types": ["WATERING"],
            "forbidden_plant_knowledge_types": ["LIGHT"],
            "min_community_sources": 2,
            "max_community_sources": 2,
            "ranked_group_minimums": [{"source_id": "1", "knowledge_type": "GENERAL_CARE", "min": 3}],
        }
        evidence = [
            {"type": "CARE_GUIDE", "sourceId": "1", "metadata": {"knowledgeType": "WATERING"}},
            {"type": "CARE_GUIDE", "sourceId": "1", "metadata": {"knowledgeType": "HUMIDITY"}},
            {"type": "COMMUNITY_POST", "sourceId": "post-a", "metadata": {"knowledgeType": "COMMUNITY_EXPERIENCE"}},
            {"type": "COMMUNITY_POST", "sourceId": "post-b", "metadata": {"knowledgeType": "COMMUNITY_EXPERIENCE"}},
        ]
        result = raw("selection", "回答", evidence)
        result["retrieval_trace"] = {"rerankAfter": [
            {"sourceId": "1", "knowledgeType": "GENERAL_CARE"},
            {"sourceId": "1", "knowledgeType": "GENERAL_CARE"},
            {"sourceId": "1", "knowledgeType": "GENERAL_CARE"},
        ]}

        summary = score.score_cases([case], [result])

        self.assertEqual(summary["metrics"]["selection_constraint_hit"],
                         {"value": 1.0, "numerator": 1, "denominator": 1})
        self.assertTrue(summary["case_results"][0]["selection_constraints"]["passed"])

    def test_safe_outcomes_and_false_refusal(self):
        cases = [
            golden("user", "REQUIRE_USER_ID"),
            golden("plant", "REQUIRE_PLANT_INSTANCE"),
            golden("state", "STATE_UNAVAILABLE"),
            golden("empty", "INSUFFICIENT_KNOWLEDGE"),
            golden("answer"),
        ]
        results = [
            raw("user", "个体化状态分析需要 userId，用于校验植物归属。"),
            raw("plant", "个体化状态分析需要 plantInstanceId，请先选择要分析的植物。"),
            raw("state", "暂时无法获取这盆植物的最新状态，因此不能可靠判断当前是否需要处理。"),
            raw("empty", "当前知识库中没有足够证据回答这个问题。"),
            raw("answer", "当前知识库中没有足够证据回答这个问题。"),
        ]
        metrics = score.score_cases(cases, results)["metrics"]
        self.assertEqual(metrics["safe_outcome_accuracy"]["value"], 1.0)
        self.assertEqual(metrics["false_refusal_count"], 1)

    def test_classifies_structured_entity_resolution_failure_as_fixed_denominator_miss(self):
        cases = [
            golden("failed", refs=[{"source_id": "1", "knowledge_type": "LIGHT"}],
                   evidence_types=["CARE_GUIDE:LIGHT"]),
            golden("answered", refs=[{"source_id": "2", "knowledge_type": "LIGHT"}],
                   evidence_types=["CARE_GUIDE:LIGHT"]),
        ]
        results = [
            {**raw("failed", "植物名称识别服务暂时不可用，请稍后重试。"),
             "rejectionReason": "llm_disambiguation_failed"},
            raw("answered", "回答", [{
                "type": "CARE_GUIDE", "sourceId": "2", "metadata": {"knowledgeType": "LIGHT"},
            }]),
        ]

        summary = score.score_cases(cases, results)
        metrics = summary["metrics"]

        self.assertEqual(summary["case_results"][0]["predicted_outcome"], "ENTITY_RESOLUTION_UNAVAILABLE")
        self.assertEqual(metrics["dependency_failure_count"], 1)
        self.assertEqual(metrics["answer_availability"], {"value": 0.5, "numerator": 1, "denominator": 2})
        self.assertEqual(metrics["knowledge_recall_at_6"], {"value": 0.5, "numerator": 1, "denominator": 2})
        self.assertEqual(metrics["precision_at_6"],
                         {"value": 0.083333, "numerator": 1, "denominator": 12, "eligible_case_count": 2})
        self.assertEqual(metrics["required_evidence_type_hit"], {"value": 0.5, "numerator": 1, "denominator": 2})
        self.assertEqual(metrics["false_refusal_count"], 1)

    def test_reads_nested_entity_resolution_failure_before_answer_text(self):
        result = raw("failed", "看起来像正常回答")
        result["response"] = {"entityResolution": {"rejectionReason": "llm_disambiguation_circuit_open"}}

        self.assertEqual(score.predict_outcome(result), "ENTITY_RESOLUTION_UNAVAILABLE")

    def test_missing_case_is_reported(self):
        summary = score.score_cases([golden("one"), golden("two")], [raw("one", "回答")])
        self.assertEqual(summary["coverage"]["missing_case_ids"], ["two"])

    def test_aggregates_two_judge_outputs(self):
        case = golden("care")
        result = raw("care", "回答")
        judgments = [{
            "case_id": "care",
            "source_fingerprint": score.source_fingerprint(case, result),
            "status": "ok",
            "correctness": {"score": 2, "missing_claims": [], "wrong_claims": [], "reason": "完整"},
            "faithfulness": {"claims": [
                {"claim": "有依据", "supported": True, "evidence_ids": ["E1"]},
                {"claim": "无依据", "supported": False, "evidence_ids": []},
            ]},
        }]
        summary = score.score_cases([case], [result], judgments)
        metrics = summary["metrics"]
        self.assertEqual(metrics["answer_correctness"]["value"], 1.0)
        self.assertEqual(metrics["faithfulness"]["value"], 0.5)
        self.assertEqual(metrics["hallucination_rate"], 0.5)

    def test_excludes_system_policy_claims_from_faithfulness_metric(self):
        case = golden("mixed")
        result = raw("mixed", "正式指南优先")
        judgments = [{
            "case_id": "mixed",
            "source_fingerprint": score.source_fingerprint(case, result),
            "status": "ok",
            "correctness": {"score": 2},
            "faithfulness": {"claims": [
                {"claim": "正式指南优先", "claim_type": "SYSTEM_POLICY", "supported": True,
                 "evidence_ids": []},
                {"claim": "耐阴所以不能暴晒", "claim_type": "EVIDENCE_FACT", "supported": False,
                 "evidence_ids": []},
            ]},
        }]

        metrics = score.score_cases([case], [result], judgments)["metrics"]

        self.assertEqual(metrics["faithfulness"]["claim_count"], 1)
        self.assertEqual(metrics["faithfulness"]["value"], 0.0)

    def test_validates_faithfulness_claim_type(self):
        normalized = score.validate_faithfulness({"claims": [{
            "claim": "正式指南优先", "claim_type": "SYSTEM_POLICY", "supported": True,
            "evidence_ids": [],
        }]})

        self.assertEqual(normalized["claims"][0]["claim_type"], "SYSTEM_POLICY")
        with self.assertRaises(ValueError):
            score.validate_faithfulness({"claims": [{
                "claim": "错误类型", "claim_type": "OTHER", "supported": True,
            }]})

    def test_faithfulness_prompt_keeps_policy_separate_from_unsupported_inference(self):
        prompt = score.prompt_text("faithfulness-judge.txt")

        self.assertIn('"claim_type": "EVIDENCE_FACT"', prompt)
        self.assertIn("正式指南优先", prompt)
        self.assertIn("耐阴所以不能一直晒大太阳", prompt)
        self.assertIn("由一条或多条已引用 Evidence", prompt)
        self.assertIn("需要浇水/建议补水", prompt)

    def test_calls_openai_compatible_judge_with_temperature_zero(self):
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc_value, traceback):
                return False

            def read(self):
                return json.dumps({"choices": [{"message": {"content": '{"score": 2}'}}]}).encode("utf-8")

        settings = score.JudgeSettings("https://judge.example/v1/chat/completions", "secret", "judge-model", 30, 0)
        with patch.object(score, "urlopen", return_value=Response()) as mocked_urlopen:
            result = score.call_judge("评测提示词", settings)

        request = mocked_urlopen.call_args.args[0]
        payload = json.loads(request.data.decode("utf-8"))
        self.assertEqual(result, {"score": 2})
        self.assertEqual(payload["model"], "judge-model")
        self.assertEqual(payload["temperature"], 0.0)

    def test_builds_system_policy_context_for_mixed_sources_and_state(self):
        case = golden("mixed", evidence_types=["CARE_GUIDE", "COMMUNITY_POST"])
        result = raw("mixed", "回答", [
            {"type": "CARE_GUIDE"},
            {"type": "COMMUNITY_POST"},
            {"type": "LIVE_STATE"},
            {"type": "SENSOR_HISTORY"},
        ])

        policy = score.system_policy_context(case, result)

        self.assertIn("以正式指南为准", policy)
        self.assertIn("COMMUNITY_POST", policy)
        self.assertIn("LIVE_STATE", policy)
        self.assertIn("需要浇水或建议补水", policy)
        self.assertIn("不建议仅凭该读数立即重复处理", policy)
        self.assertIn("SENSOR_HISTORY", policy)

    def test_faithfulness_prompt_receives_system_policy_context(self):
        case = golden("mixed", evidence_types=["CARE_GUIDE", "COMMUNITY_POST"])
        result = raw("mixed", "回答", [{"type": "COMMUNITY_POST", "title": "帖子", "content": "内容"}])
        settings = score.JudgeSettings("https://judge.example/v1/chat/completions", "secret", "judge-model", 30, 0)
        existing = []
        prompts = []

        def fake_call_judge(prompt, _settings):
            prompts.append(prompt)
            if "Correctness Judge" in prompt:
                return {"score": 2, "missing_claims": [], "wrong_claims": [], "reason": ""}
            if "System Policy Context:" in prompt:
                return {"claims": []}
            return {"score": 2}

        with patch.object(score, "call_judge", side_effect=fake_call_judge):
            with patch.object(score, "prompt_text", side_effect=[
                "Correctness Judge\nQuestion:\n{{QUESTION}}\nGenerated Answer:\n{{GENERATED_ANSWER}}",
                "Faithfulness Judge\nSystem Policy Context:\n{{SYSTEM_POLICY}}\nEvidence:\n{{EVIDENCE}}\nGenerated Answer:\n{{GENERATED_ANSWER}}",
            ]):
                judgments, failures = score.run_judges([case], [result], settings, existing, refresh=False)

        self.assertEqual(failures, 0)
        self.assertEqual(judgments[0]["status"], "ok")
        self.assertTrue(any("COMMUNITY_POST 的证据属于社区用户或帖子作者的个人经验" in prompt for prompt in prompts))

    def test_checkpoints_after_each_completed_case(self):
        cases = [golden("first"), golden("second")]
        results = [raw("first", "第一个回答"), raw("second", "第二个回答")]
        settings = score.JudgeSettings("https://judge.example/v1/chat/completions", "secret", "judge-model", 30, 0)
        with tempfile.TemporaryDirectory() as directory:
            checkpoint_path = Path(directory) / "judgments.jsonl"
            with patch.object(score, "call_judge", side_effect=[
                {"score": 2, "missing_claims": [], "wrong_claims": [], "reason": ""}, {"claims": []},
                KeyboardInterrupt(),
            ]):
                with self.assertRaises(KeyboardInterrupt):
                    score.run_judges(
                        cases, results, settings, [], refresh=False,
                        on_case_complete=lambda rows: score.write_jsonl(checkpoint_path, rows),
                    )

            checkpoints = score.load_judgments(checkpoint_path)
            self.assertEqual([item["case_id"] for item in checkpoints], ["first"])
            self.assertEqual(checkpoints[0]["status"], "ok")

    def test_retries_after_incomplete_chunked_response(self):
        class Response:
            def __init__(self, payload=None, error=None):
                self.payload = payload
                self.error = error

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc_value, traceback):
                return False

            def read(self):
                if self.error:
                    raise self.error
                return self.payload

        incomplete = score.IncompleteRead(b"", 7)
        responses = [
            Response(error=incomplete),
            Response(payload=json.dumps({"choices": [{"message": {"content": '{"score": 2}'}}]}).encode("utf-8")),
        ]
        settings = score.JudgeSettings("https://judge.example/v1/chat/completions", "secret", "judge-model", 30, 1)
        with patch.object(score, "urlopen", side_effect=responses) as mocked_urlopen:
            result = score.call_judge("评测提示词", settings)

        self.assertEqual(result, {"score": 2})
        self.assertEqual(mocked_urlopen.call_count, 2)

    def test_does_not_apply_judgment_cache_to_changed_answer(self):
        case = golden("care")
        old_result = raw("care", "旧回答")
        changed_result = raw("care", "新回答")
        judgments = [{
            "case_id": "care",
            "source_fingerprint": score.source_fingerprint(case, old_result),
            "status": "ok",
            "correctness": {"score": 2},
            "faithfulness": {"claims": []},
        }]

        metrics = score.score_cases([case], [changed_result], judgments)["metrics"]

        self.assertIsNone(metrics["answer_correctness"]["value"])
        self.assertEqual(metrics["answer_correctness"]["judged_case_count"], 0)

    def test_reads_judge_local_config_without_api_key(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            config_path = Path(temp_dir) / "judge.local.json"
            config_path.write_text(json.dumps({
                "url": "https://judge.example/v1/chat/completions",
                "model": "judge-model",
                "api_key_env": "TEST_JUDGE_KEY",
                "timeout": 12,
                "retries": 0,
            }), encoding="utf-8")
            previous = os.environ.get("TEST_JUDGE_KEY")
            os.environ["TEST_JUDGE_KEY"] = "test-key"
            try:
                args = score.argparse.Namespace(
                    judge_config=config_path, judge_url=None, judge_model=None, judge_api_key_env=None,
                    judge_timeout=None, judge_retries=None,
                )
                settings = score.judge_settings(args)
            finally:
                if previous is None:
                    del os.environ["TEST_JUDGE_KEY"]
                else:
                    os.environ["TEST_JUDGE_KEY"] = previous

        self.assertEqual(settings.url, "https://judge.example/v1/chat/completions")
        self.assertEqual(settings.model, "judge-model")
        self.assertEqual(settings.timeout, 12.0)


if __name__ == "__main__":
    unittest.main()
