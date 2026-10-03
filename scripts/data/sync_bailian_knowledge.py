from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = PROJECT_ROOT / ".env.local"
CORPUS_DIR = PROJECT_ROOT / "data" / "knowledge" / "relationship-coach"
STATE_FILE = CORPUS_DIR / "cloud-state.json"

DEFAULT_NAME = "恋爱大师"
DEFAULT_DESCRIPTION = "关系科学、沟通、边界、安全与恋爱实战知识库"
DEFAULT_QUERIES = [
    "暧昧期对方回复变慢，我应该怎么判断是不是投入失衡？",
    "第一次约会怎样主动表达好感，同时尊重边界和同意？",
    "发生争吵后，怎样进行有效修复而不是继续升级冲突？",
]
def load_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def save_env_value(path: Path, key: str, value: str) -> None:
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    prefix = key + "="
    replaced = False
    result: list[str] = []
    for line in lines:
        if line.startswith(prefix):
            result.append(prefix + value)
            replaced = True
        else:
            result.append(line)
    if not replaced:
        result.append(prefix + value)
    path.write_text("\n".join(result).rstrip() + "\n", encoding="utf-8")


def env_value(values: dict[str, str], key: str) -> str:
    return os.environ.get(key, values.get(key, "")).strip()
class BailianRagClient:
    def __init__(self, api_key: str, workspace_id: str) -> None:
        self.api_key = api_key
        self.workspace_id = workspace_id
        self.base_url = (
            f"https://{workspace_id}.cn-beijing.maas.aliyuncs.com"
        )
    def _check_data(
            self, data: dict[str, Any], http_status: int) -> dict[str, Any]:
        code = str(data.get("code", ""))
        status = data.get("status")
        success = data.get("success")
        status_code = data.get("status_code", data.get("statusCode"))
        business_ok = (
            http_status < 400
            and code in {"", "Success"}
            and status not in {400, "FAILED"}
            and status_code not in {400, "400"}
            and success is not False
        )
        if not business_ok:
            raise RuntimeError(
                f"Bailian API failed: http={http_status}, code={code}, "
                f"status={status}, message={data.get('message', '')}"
            )
        return data

    def _curl_json(
            self, method: str, path: str,
            payload: dict[str, Any] | None = None,
            params: dict[str, Any] | None = None,
            timeout: int = 60) -> dict[str, Any]:
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        with tempfile.TemporaryDirectory() as td:
            td_path = Path(td)
            headers = td_path / "headers.txt"
            headers.write_text(
                "Authorization: Bearer " + self.api_key
                + "\nContent-Type: application/json\n",
                encoding="utf-8",
            )
            args = [
                "curl.exe", "-sS", "--connect-timeout", "15",
                "--max-time", str(timeout), "-X", method,
                "-H", "@" + str(headers),
            ]
            if payload is not None:
                body = td_path / "body.json"
                body.write_text(
                    json.dumps(payload, ensure_ascii=False), encoding="utf-8"
                )
                args += ["--data-binary", "@" + str(body)]
            args += ["-w", "\n__HTTP__%{http_code}", url]
            proc = subprocess.run(
                args, capture_output=True, text=True, encoding="utf-8",
                errors="replace",
            )
        if proc.returncode != 0:
            raise RuntimeError("curl failed: " + proc.stderr.strip())
        raw, marker, code = proc.stdout.rpartition("\n__HTTP__")
        if not marker:
            raise RuntimeError("curl response missing HTTP status marker")
        data = json.loads(raw)
        return self._check_data(data, int(code.strip()))

    def post(self, path: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self._curl_json("POST", path, payload=payload)

    def get(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        return self._curl_json("GET", path, params=params)

    def upload_file(self, file_path: Path) -> str:
        data = file_path.read_bytes()
        md5_hex = hashlib.md5(data).hexdigest()
        lease = self.post(
            "/api/v1/connector/dash/applyFileUploadLease",
            {
                "category": "default",
                "fileName": file_path.name,
                "sizeBytes": str(len(data)),
                "contentMd5": md5_hex,
                "categoryType": "UNSTRUCTURED",
            },
        )["data"]
        lease_id = (
            lease.get("leaseId")
            or lease.get("fileUploadLeaseId")
            or lease.get("file_upload_lease_id")
        )
        if not lease_id:
            raise RuntimeError("Bailian upload lease response missing lease ID")

        with tempfile.TemporaryDirectory() as td:
            headers_file = Path(td) / "oss-headers.txt"
            header_lines = [
                f"{key}: {value}"
                for key, value in lease["param"].get("headers", {}).items()
            ]
            headers_file.write_text(
                "\n".join(header_lines) + "\n", encoding="utf-8"
            )
            proc = subprocess.run(
                [
                    "curl.exe", "-sS", "--fail-with-body",
                    "--connect-timeout", "15", "--max-time", "120",
                    "-X", "PUT", "-H", "@" + str(headers_file),
                    "--data-binary", "@" + str(file_path),
                    lease["param"]["url"],
                ],
                capture_output=True, text=True, encoding="utf-8",
                errors="replace",
            )
        if proc.returncode != 0:
            raise RuntimeError("OSS upload failed: " + proc.stderr.strip())

        registered = self.post(
            "/api/v1/connector/dash/addFile",
            {
                "leaseId": lease_id,
                "category": "default",
                "categoryType": "UNSTRUCTURED",
                "parser": "AUTO_SELECT",
                "tags": ["恋爱大师", "goutoujunshi"],
            },
        )["data"]
        file_id = registered.get("fileId") or registered.get("FileId")
        if not file_id:
            raise RuntimeError("Bailian addFile response missing fileId")
        return str(file_id)
    def list_indices(self, name: str) -> list[dict[str, Any]]:
        response = self.get(
            "/api/v1/indices/rag/index/list",
            {"page_number": 1, "page_size": 100, "pipeline_name": name},
        )
        rows = (response.get("data") or {}).get("rows") or []
        return [row for row in rows if row.get("name") == name]

    def create_index(
            self, name: str, description: str,
            doc_ids: list[str]) -> tuple[str, str]:
        response = self.post(
            "/api/v1/indices/rag/index/create_v2",
            {
                "name": name,
                "description": description,
                "structureType": "unstructured",
                "sinkType": "DEFAULT",
                "sourceType": "DATA_CENTER_FILE",
                "embeddingModelName": "text-embedding-v4",
                "chunkSize": 600,
                "docIds": doc_ids,
                "dataSources": [{"sourceType": "DATA_CENTER_FILE"}],
            },
        )
        data = response["data"]
        return str(data["pipelineId"]), str(data["ingestionId"])

    def submit_import(
            self, index_id: str, doc_ids: list[str]) -> str:
        response = self.post(
            "/api/v1/indices/rag/index/job/create",
            {
                "indexId": index_id,
                "sourceType": "DATA_CENTER_FILE",
                "docIds": doc_ids,
            },
        )
        return str(response["data"]["ingestionId"])
    def wait_for_job(
            self, index_id: str, job_id: str,
            poll_seconds: int = 3, timeout_seconds: int = 900
    ) -> dict[str, Any]:
        deadline = time.time() + timeout_seconds
        last_status = None
        while time.time() < deadline:
            response = self.get(
                "/api/v1/indices/rag/index_job/status",
                {"index_id": index_id, "job_id": job_id,
                 "page_number": 1, "page_size": 100},
            )
            data = response["data"]
            status = str(data.get("ingestion_status", ""))
            if status != last_status:
                print("ingestion_status=" + status, flush=True)
                last_status = status
            if status == "COMPLETED":
                failed_rows = [
                    row for row in (data.get("rows") or [])
                    if str(row.get("status", "")).upper() not in {"FINISH", "COMPLETED"}
                    and str(row.get("code", "")).upper() not in {"FINISH", "SUCCESS"}
                ]
                if failed_rows:
                    raise RuntimeError(
                        "Ingestion completed with failed documents: "
                        + ", ".join(str(row.get("doc_name")) for row in failed_rows)
                    )
                return data
            if status in {"FAILED", "CANCELLED"}:
                raise RuntimeError("Bailian ingestion failed: " + status)
            time.sleep(poll_seconds)
        raise TimeoutError("Timed out waiting for Bailian ingestion job")

    def retrieve(
            self, index_id: str, query: str, top_k: int = 5
    ) -> list[dict[str, Any]]:
        response = self.post(
            "/api/v1/indices/rag/index/retrieve",
            {"index_id": index_id, "query": query, "top_k": top_k},
        )
        return (response.get("data") or {}).get("nodes") or []


def load_state() -> dict[str, Any]:
    if not STATE_FILE.exists():
        return {}
    return json.loads(STATE_FILE.read_text(encoding="utf-8"))


def save_state(state: dict[str, Any]) -> None:
    state["updated_at"] = datetime.now(timezone.utc).isoformat()
    STATE_FILE.write_text(
        json.dumps(state, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def load_manifest() -> dict[str, Any]:
    path = CORPUS_DIR / "manifest.json"
    if not path.exists():
        raise SystemExit(
            "Corpus manifest not found. Run prepare_relationship_corpus.py first."
        )
    return json.loads(path.read_text(encoding="utf-8"))


def corpus_files(manifest: dict[str, Any]) -> list[tuple[Path, str]]:
    result: list[tuple[Path, str]] = []
    for item in manifest.get("selected", []):
        path = CORPUS_DIR / str(item["output"])
        if not path.exists():
            raise RuntimeError("Corpus file missing: " + str(path))
        actual = hashlib.sha256(
            path.read_text(encoding="utf-8").encode("utf-8")
        ).hexdigest()
        expected = str(item["sha256"])
        if actual != expected:
            raise RuntimeError("Corpus checksum mismatch: " + path.name)
        result.append((path, actual))
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--name", default=DEFAULT_NAME)
    parser.add_argument("--description", default=DEFAULT_DESCRIPTION)
    parser.add_argument("--timeout", type=int, default=900)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()

    env = load_env_file(ENV_FILE)
    api_key = env_value(env, "DASHSCOPE_API_KEY")
    workspace_id = env_value(env, "BAILIAN_WORKSPACE_ID")
    if not api_key or not workspace_id:
        raise SystemExit(
            "DASHSCOPE_API_KEY and BAILIAN_WORKSPACE_ID are required in .env.local"
        )

    manifest = load_manifest()
    files = corpus_files(manifest)
    client = BailianRagClient(api_key, workspace_id)

    matches = client.list_indices(args.name)
    print("existing_exact_name_matches=" + str(len(matches)), flush=True)
    if args.check_only:
        print("workspace_access=OK")
        print("corpus_files=" + str(len(files)))
        return

    state = load_state()
    if state.get("source_commit") not in {None, manifest.get("source_commit")}:
        raise RuntimeError(
            "Existing cloud-state belongs to a different source commit. "
            "Review it before syncing a new corpus."
        )
    state.setdefault("source_commit", manifest.get("source_commit"))
    state.setdefault("uploaded", {})
    uploaded: dict[str, Any] = state["uploaded"]
    file_ids: list[str] = []
    total = len(files)
    for idx, (path, digest) in enumerate(files, start=1):
        previous = uploaded.get(path.name) or {}
        if previous.get("sha256") == digest and previous.get("file_id"):
            file_id = str(previous["file_id"])
            print(f"upload[{idx}/{total}] reuse {path.name}", flush=True)
        else:
            print(f"upload[{idx}/{total}] {path.name}", flush=True)
            file_id = client.upload_file(path)
            uploaded[path.name] = {
                "sha256": digest,
                "file_id": file_id,
            }
            save_state(state)
            time.sleep(0.2)
        file_ids.append(file_id)

    index_id = env_value(env, "BAILIAN_INDEX_ID") or str(
        state.get("index_id") or ""
    )
    job_id = ""

    if index_id:
        print("knowledge_base=reuse_existing_index", flush=True)
        if not state.get("ingestion_completed"):
            previous_job_id = str(state.get("ingestion_id") or "")
            if previous_job_id:
                job_id = previous_job_id
                print("ingestion=resume_existing_job", flush=True)
            else:
                job_id = client.submit_import(index_id, file_ids)
    else:
        if len(matches) > 1:
            raise RuntimeError(
                "Multiple exact-name knowledge bases exist; set BAILIAN_INDEX_ID explicitly."
            )
        if len(matches) == 1:
            index_id = str(matches[0]["id"])
            print("knowledge_base=found_existing_exact_name", flush=True)
            job_id = client.submit_import(index_id, file_ids)
        else:
            print("knowledge_base=creating", flush=True)
            index_id, job_id = client.create_index(
                args.name, args.description, file_ids
            )
        state["index_id"] = index_id
        save_env_value(ENV_FILE, "BAILIAN_INDEX_ID", index_id)
        save_state(state)

    if job_id:
        state["ingestion_id"] = job_id
        save_state(state)
        job_data = client.wait_for_job(
            index_id, job_id, timeout_seconds=args.timeout
        )
        state["ingestion_completed"] = True
        state["ingestion_total_count"] = job_data.get("total_count")
        save_state(state)
    retrieval_checks: list[dict[str, Any]] = []
    for query in DEFAULT_QUERIES:
        try:
            nodes = client.retrieve(index_id, query, top_k=5)
        except RuntimeError as exc:
            state["retrieval_error"] = str(exc)
            save_state(state)
            if "Index.BailianIndexServiceNotOpen" in str(exc):
                save_env_value(ENV_FILE, "BAILIAN_RAG_ENABLED", "false")
                print("service_activation_required=true")
                print("BAILIAN_RAG_ENABLED=false")
                return
            raise
        if not nodes:
            raise RuntimeError("Retrieval validation returned no nodes: " + query)
        top = nodes[0]
        metadata = top.get("metadata") or {}
        retrieval_checks.append({
            "query": query,
            "count": len(nodes),
            "top_score": top.get("score"),
            "top_doc": metadata.get("doc_name") or metadata.get("doc_id"),
        })
        print(
            "retrieval=OK|count=" + str(len(nodes))
            + "|top_doc=" + str(
                metadata.get("doc_name") or metadata.get("doc_id") or ""
            ),
            flush=True,
        )

    state.pop("retrieval_error", None)
    state["retrieval_checks"] = retrieval_checks
    state["validated_at"] = datetime.now(timezone.utc).isoformat()
    save_state(state)
    save_env_value(ENV_FILE, "BAILIAN_RAG_ENABLED", "true")

    print("knowledge_base_id=" + index_id)
    print("documents=" + str(len(file_ids)))
    print("retrieval_checks=" + str(len(retrieval_checks)))
    print("BAILIAN_RAG_ENABLED=true")


if __name__ == "__main__":
    main()
