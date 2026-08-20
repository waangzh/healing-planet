import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import run_eval


class PartialStreamResponse:
    status = 200

    def __init__(self, lines):
        self.lines = iter(lines)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def readline(self):
        value = next(self.lines)
        if isinstance(value, Exception):
            raise value
        return value


class RunEvalTest(unittest.TestCase):

    def test_stream_timeout_preserves_received_retrieval_trace(self):
        trace = {"rrfCandidates": [{"id": "guide-1"}]}
        response = PartialStreamResponse([
            b"event: evidence\n",
            b'data: [{"id":"guide-1"}]\n',
            b"\n",
            b"event: retrieval_trace\n",
            ("data: " + json.dumps(trace) + "\n").encode(),
            b"\n",
            TimeoutError("timed out"),
        ])

        with patch.object(run_eval, "urlopen", return_value=response):
            status, result = run_eval.request_chat_stream("http://localhost:8010", {"query": "test"}, 1)

        self.assertEqual(200, status)
        self.assertEqual(trace, result["retrievalTrace"])
        self.assertEqual("TimeoutError", result["transportError"]["type"])


if __name__ == "__main__":
    unittest.main()
