from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SOURCE: Path | None = None
DEFAULT_DEST = PROJECT_ROOT / "data" / "knowledge" / "relationship-coach"

PRACTICAL_INCLUDE = {
    "万能吵架技巧：理性冲突处理指南.md",
    "万能夸人的话术技巧：真诚认可的实用指南.md",
    "为他人提供情绪价值：温暖且有效的回应指南.md",
    "主动表达、第一次见面与自然接触.md",
    "公开表达案例的伦理转译.md",
    "关系投入失衡：互惠判断、降级投入与退出决策.md",
}
PRACTICAL_INCLUDE |= {
    "化解尴尬：轻松救场的实用指南.md",
    "场景感、松弛感与社交校准：从接话到关系推进.md",
    "实战话术编排器：从一句回复到后续分支.md",
    "巧妙接话技巧：让沟通更流畅的实用指南.md",
    "废话文学回复指南：轻松应对各类场景.md",
    "提升表达逻辑性：从混乱到清晰的实用指南.md",
    "提高气场：从内到外的力量感塑造指南.md",
    "聊天化被动为主动：引导互动的实用指南.md",
    "自然流、内在状态与结构化互动：伦理能力转译.md",
    "高情商拒绝他人：体面护边界的实用指南.md",
}

EXCLUDE_REASON = {
    "00-导读与使用分级.md": "运行路由/导读，不属于事实知识",
    "ChatLab聊天记录分析适配.md": "工具适配规则，不属于知识库内容",
    "长期记忆与关系档案.md": "本地记忆操作规则，不应进入云知识库",
    "托人办事的高效话术指南.md": "偏通用办事沟通，与恋爱主题弱相关",
    "有效拓展人脉：从建立到维护的实用指南.md": "偏职场/人脉，与恋爱主题弱相关",
    "获得领导青睐：从价值匹配到信任建立的实用指南.md": "职场主题",
    "被孤立如何破局：从自我调适到建立连接的实用指南.md": "泛社交主题，优先控制知识库噪声",
}
LOCAL_LINK_RE = re.compile(r"\[([^\]]+)\]\((?:\.?\.?/)[^)]+\)")
HTML_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)


def git_commit(repo: Path) -> str:
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=repo, text=True
    ).strip()


def clean_markdown(text: str, source: str, commit: str, kind: str) -> str:
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = HTML_COMMENT_RE.sub("", text)
    text = LOCAL_LINK_RE.sub(r"\1", text)
    lines = text.splitlines()
    output: list[str] = []
    seen_h1: set[str] = set()
    inserted_meta = False

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("# "):
            title = stripped[2:].strip()
            if title in seen_h1:
                continue
            seen_h1.add(title)
            output.append(line.rstrip())
            if not inserted_meta:
                output.extend([
                    "",
                    f"> 来源：goutoujunshi {source}，commit {commit}，MIT License。",
                    f"> 语料类型：{kind}。用于关系教育与决策支持；不替代医疗、心理治疗、法律或紧急服务。",
                ])
                inserted_meta = True
            continue
        output.append(line.rstrip())

    cleaned: list[str] = []
    blank = False
    for line in output:
        if line.strip():
            cleaned.append(line)
            blank = False
        elif not blank:
            cleaned.append("")
            blank = True
    return "\n".join(cleaned).strip() + "\n"


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()
def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source",
        type=Path,
        default=DEFAULT_SOURCE,
        help="Path to a checked-out goutoujunshi source repository.",
    )
    parser.add_argument("--dest", type=Path, default=DEFAULT_DEST)
    args = parser.parse_args()

    if args.source is None:
        raise SystemExit(
            "No source repository configured. "
            "Clone goutoujunshi when you need to rebuild the vendored corpus, "
            "then pass --source <path>."
        )

    source = args.source.resolve()
    dest = args.dest.resolve()
    if not (source / "references" / "knowledge").is_dir():
        raise SystemExit(f"Invalid goutoujunshi source: {source}")

    commit = git_commit(source)
    if dest.exists():
        shutil.rmtree(dest)
    dest.mkdir(parents=True, exist_ok=True)

    selected: list[dict[str, object]] = []
    excluded: list[dict[str, str]] = []

    knowledge_files = sorted((source / "references" / "knowledge").glob("*.md"))
    practical_files = sorted((source / "references" / "practical").glob("*.md"))

    for src in knowledge_files:
        rel = src.relative_to(source).as_posix()
        text = clean_markdown(
            src.read_text(encoding="utf-8"), rel, commit, "核心知识"
        )
        out = dest / ("knowledge-" + src.name)
        out.write_text(text, encoding="utf-8")
        selected.append({
            "source": rel,
            "output": out.name,
            "kind": "knowledge",
            "chars": len(text),
            "sha256": sha256_text(text),
        })

    for src in practical_files:
        rel = src.relative_to(source).as_posix()
        if src.name not in PRACTICAL_INCLUDE:
            excluded.append({
                "source": rel,
                "reason": EXCLUDE_REASON.get(
                    src.name, "未进入恋爱大师核心实战白名单"
                ),
            })
            continue
        text = clean_markdown(
            src.read_text(encoding="utf-8"), rel, commit, "实战沟通"
        )
        out = dest / ("practical-" + src.name)
        out.write_text(text, encoding="utf-8")
        selected.append({
            "source": rel,
            "output": out.name,
            "kind": "practical",
            "chars": len(text),
            "sha256": sha256_text(text),
        })

    license_text = (source / "LICENSE").read_text(encoding="utf-8")
    (dest / "LICENSE-goutoujunshi.txt").write_text(
        license_text, encoding="utf-8"
    )

    manifest = {
        "source_repository": "https://github.com/shengjidaguai-china/goutoujunshi",
        "source_commit": commit,
        "license": "MIT",
        "selection_policy": {
            "knowledge": "全部 references/knowledge/*.md",
            "practical": "仅恋爱、沟通、边界、互动直接相关白名单",
            "excluded": "运行规则、记忆、ChatLab、职场/人脉及泛社交噪声",
        },
        "selected_count": len(selected),
        "excluded_count": len(excluded),
        "selected": selected,
        "excluded": excluded,
    }
    (dest / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    total_chars = sum(int(item["chars"]) for item in selected)
    print(f"source_commit={commit}")
    print(f"selected={len(selected)}")
    print(f"excluded={len(excluded)}")
    print(f"total_chars={total_chars}")
    print(f"dest={dest}")


if __name__ == "__main__":
    main()
