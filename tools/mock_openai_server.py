"""Local OpenAI-compatible server used only for Android end-to-end tests."""

import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.rstrip("/") in {"/models", "/v1/models"}:
            self._json(
                200,
                {
                    "object": "list",
                    "data": [
                        {"id": "deepseek-v4-flash", "object": "model", "owned_by": "mock-deepseek"},
                        {"id": "deepseek-v4-pro", "object": "model", "owned_by": "mock-deepseek"},
                    ],
                },
            )
            return
        self._json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        if self.path.rstrip("/") not in {"/chat/completions", "/v1/chat/completions"}:
            self._json(404, {"error": {"message": "not found"}})
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        request = json.loads(raw.decode("utf-8"))
        messages = request.get("messages", [])
        if any("正在扮演小说" in message.get("content", "") for message in messages):
            self._json(
                200,
                {
                    "id": "chatcmpl-character-test",
                    "object": "chat.completion",
                    "model": request.get("model", "mock-model"),
                    "choices": [
                        {
                            "index": 0,
                            "message": {"role": "assistant", "content": "我会依据已经发生的故事回答你。"},
                            "finish_reason": "stop",
                        }
                    ],
                },
            )
            return
        analysis = {
            "characters": [
                {
                    "name": "测试角色甲",
                    "aliases": ["甲"],
                    "gender": "UNKNOWN",
                    "personality": "沉着",
                    "firstChapterIndex": 0,
                    "lastChapterIndex": 14,
                    "confidence": 0.95,
                },
                {
                    "name": "测试角色乙",
                    "aliases": ["乙"],
                    "gender": "UNKNOWN",
                    "personality": "勇敢",
                    "firstChapterIndex": 0,
                    "lastChapterIndex": 14,
                    "confidence": 0.93,
                },
            ],
            "relations": [
                {
                    "from": "测试角色甲",
                    "to": "测试角色乙",
                    "type": "PROTECTS",
                    "strength": 0.9,
                    "startChapterIndex": 0,
                    "endChapterIndex": None,
                    "evidence": "端到端测试证据",
                    "confidence": 0.98,
                }
            ],
            "plotNodes": [
                {
                    "title": "测试剧情节点",
                    "summary": "验证LLM结果能够写入故事大脑。",
                    "startChapterIndex": 0,
                    "endChapterIndex": 14,
                    "parentTitles": [],
                    "location": "测试地点",
                    "confidence": 0.97,
                }
            ],
        }
        self._json(
            200,
            {
                "id": "chatcmpl-storybrain-test",
                "object": "chat.completion",
                "model": "deepseek-v4-flash",
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": json.dumps(analysis, ensure_ascii=False)},
                        "finish_reason": "stop",
                    }
                ],
            },
        )

    def log_message(self, _format, *_args):
        return

    def _json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8765), Handler).serve_forever()
