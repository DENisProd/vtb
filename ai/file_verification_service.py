"""
Сервис для проверки файлов OpenAPI и BPMN с помощью ИИ модели Qwen/Qwen3-4B-Thinking-2507-FP8
"""

import json
import sys
import os
import time
import types
import importlib.abc
import importlib.machinery
from typing import Dict, Any, List, Optional
from pathlib import Path
import requests
import os
import time

# Флаг для подробного логирования (можно установить через переменную окружения)
VERBOSE = os.getenv("AI_VERIFICATION_VERBOSE", "false").lower() == "true"

# Явно отключаем Triton при отсутствии поддержки в окружении
os.environ.setdefault("DISABLE_TRITON", "1")

# Заглушка для импортов triton в моделях, когда DISABLE_TRITON=1
if os.getenv("DISABLE_TRITON") == "1":
    class _DummyTritonLoader(importlib.abc.Loader):
        def create_module(self, spec):
            mod = sys.modules.get(spec.name) or types.ModuleType(spec.name)
            mod.__spec__ = spec
            mod.__version__ = "0.0.0"
            if spec.name == "triton":
                setattr(mod, "__path__", [])
                setattr(mod, "__package__", "triton")
            else:
                setattr(mod, "__package__", "triton")
            return mod
        def exec_module(self, module):
            return None

    class _DummyTritonFinder(importlib.abc.MetaPathFinder):
        def find_spec(self, fullname, path, target=None):
            if fullname == "triton":
                return importlib.util.spec_from_loader(fullname, _DummyTritonLoader(), is_package=True)
            if fullname.startswith("triton."):
                return importlib.util.spec_from_loader(fullname, _DummyTritonLoader(), is_package=False)
            return None

    sys.meta_path.insert(0, _DummyTritonFinder())

def log_debug(message: str):
    """Выводит отладочное сообщение в stderr (не мешает JSON выводу)"""
    if VERBOSE:
        print(f"[DEBUG] {message}", file=sys.stderr, flush=True)

def log_info(message: str):
    """Выводит информационное сообщение в stderr"""
    print(f"[INFO] {message}", file=sys.stderr, flush=True)

# Добавляем путь к корню проекта
sys.path.insert(0, str(Path(__file__).parent))

try:
    from transformers import AutoTokenizer, AutoModelForCausalLM, pipeline
    import torch
    AI_AVAILABLE = True
except ImportError:
    AI_AVAILABLE = False
    print("Warning: transformers not available, AI verification will be disabled")

try:
    from optimum.onnxruntime import ORTModelForCausalLM
    import onnxruntime as ort
    ORT_AVAILABLE = True
except Exception:
    ORT_AVAILABLE = False

# Модель для проверки
# Попытка использовать Qwen/Qwen3-4B-Thinking-2507-FP8, если недоступна - используем альтернативу
MODEL_NAME = "Qwen/Qwen2.5-0.5B-Instruct"
# Для использования Qwen/Qwen3-4B-Thinking-2507-FP8 установите переменную окружения:
# export QWEN_MODEL_NAME="Qwen/Qwen3-4B-Thinking-2507-FP8"
# или замените MODEL_NAME на нужную модель

# Модель загружается автоматически из Hugging Face Hub при первом запуске
# и кэшируется локально в ~/.cache/huggingface/hub/
# Для изменения места кэширования установите переменную окружения:
# export HF_HOME="/path/to/cache"
# или
# export TRANSFORMERS_CACHE="/path/to/cache"

class FileVerificationService:
    """Сервис для проверки файлов OpenAPI и BPMN с помощью ИИ"""
    
    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.pipeline = None
        self.remote_enabled = os.getenv("USE_REMOTE_INFERENCE", "0") == "1"
        self.remote_model_id = os.getenv("REMOTE_MODEL_ID")
        self.remote_api_token = os.getenv("HF_TOKEN")
        self.use_ort = os.getenv("USE_ORT", "0") == "1"
        self._initialize_model()
    
    def _initialize_model(self):
        """Инициализирует модель ИИ"""
        if not AI_AVAILABLE and not self.remote_enabled:
            log_info("AI libraries not available, using fallback verification")
            return
        
        model_name = os.getenv("QWEN_MODEL_NAME", MODEL_NAME)
        cpu_only_1b = os.getenv("CPU_MODEL_1B", "Qwen/Qwen2.5-0.5B-Instruct")
        if (not torch.cuda.is_available()) or os.getenv("CPU_ONLY") == "1":
            model_name = os.getenv("CPU_MODEL_1B", cpu_only_1b)
            if ORT_AVAILABLE:
                self.use_ort = True
        elif os.getenv("DISABLE_TRITON") == "1":
            if ("FP8" in model_name) or ("Thinking" in model_name):
                model_name = os.getenv("QWEN_CPU_MODEL", "Qwen/Qwen2.5-0.5B-Instruct")
        
        # Проверяем, указан ли локальный путь к модели
        # Если model_name начинается с "/" или "./", это локальный путь
        is_local_path = model_name.startswith("/") or model_name.startswith("./") or model_name.startswith("../")
        
        if is_local_path:
            log_info(f"Loading model from local path: {model_name}")
            if not os.path.exists(model_name):
                log_info(f"Local model path does not exist: {model_name}")
                log_info("Falling back to rule-based verification")
                self.model = None
                self.pipeline = None
                return
        else:
            # Модель будет загружена из Hugging Face Hub
            cache_dir = os.getenv("HF_HOME") or os.getenv("TRANSFORMERS_CACHE")
            if cache_dir:
                # Если указан HF_HOME, добавляем /hub если нужно
                cache_path = Path(cache_dir)
                # Проверяем, есть ли уже /hub в пути или нужно добавить
                if cache_path.name != "hub":
                    hub_path = cache_path / "hub"
                    if hub_path.exists() or str(cache_dir).endswith("/hub"):
                        cache_path = hub_path
                log_info(f"Using cache directory: {cache_path}")
            else:
                # Показываем стандартный путь кэша
                default_cache = os.path.expanduser("~/.cache/huggingface/hub")
                log_info(f"Using default cache directory: {default_cache}")
                log_info(f"Loading model {model_name} from Hugging Face Hub...")
                log_info(f"Model will be cached in: {default_cache}")
        
        try:
            log_info(f"Loading model {model_name}...")
            start_time = time.time()
            
            log_debug("Loading tokenizer...")
            tokenizer_kwargs = {"trust_remote_code": True}
            hf_token = os.getenv("HF_TOKEN")
            if hf_token:
                tokenizer_kwargs["token"] = hf_token
            if cache_dir:
                # Используем тот же путь кэша, что был определен выше
                cache_path_str = str(cache_path) if isinstance(cache_path, Path) else cache_dir
                tokenizer_kwargs["cache_dir"] = cache_path_str
            self.tokenizer = AutoTokenizer.from_pretrained(model_name, **tokenizer_kwargs)
            log_debug(f"Tokenizer loaded in {time.time() - start_time:.2f}s")
            
            log_debug("Loading model...")
            model_start = time.time()
            if self.use_ort and ORT_AVAILABLE:
                providers = ["CPUExecutionProvider"]
                try:
                    avail = ort.get_available_providers()
                    if "DmlExecutionProvider" in avail:
                        providers = ["DmlExecutionProvider", "CPUExecutionProvider"]
                    elif "CUDAExecutionProvider" in avail:
                        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
                except Exception:
                    pass
                ort_id = os.getenv("ORT_MODEL_ID") or model_name
                try:
                    self.model = ORTModelForCausalLM.from_pretrained(ort_id, provider=providers[0])
                    log_debug(f"Model loaded in {time.time() - model_start:.2f}s")
                except Exception as e:
                    err_str = str(e)
                    log_info(f"Error loading ORT model {ort_id}: {err_str}")
                    self.use_ort = False
            if not self.use_ort:
                model_kwargs = {
                    "trust_remote_code": True,
                }
                if torch.cuda.is_available() and os.getenv("DISABLE_TRITON") != "1" and os.getenv("CPU_ONLY") != "1":
                    model_kwargs["dtype"] = torch.float16
                    model_kwargs["device_map"] = "auto"
                    model_kwargs["attn_implementation"] = "sdpa"
                else:
                    model_kwargs["dtype"] = torch.float32
                    model_kwargs["attn_implementation"] = "eager"
                if cache_dir:
                    cache_path_str = str(cache_path) if isinstance(cache_path, Path) else cache_dir
                    model_kwargs["cache_dir"] = cache_path_str
                if hf_token:
                    model_kwargs["token"] = hf_token
                try:
                    self.model = AutoModelForCausalLM.from_pretrained(model_name, **model_kwargs)
                    log_debug(f"Model loaded in {time.time() - model_start:.2f}s")
                except Exception as e:
                    err_str = str(e)
                    log_info(f"Error loading model {model_name}: {err_str}")
                    env_fallback = os.getenv("QWEN_FALLBACK_MODEL")
                    candidates: List[str] = []
                    if env_fallback:
                        candidates.append(env_fallback)
                    candidates.extend([
                        os.getenv("CPU_MODEL_1B", "TinyLlama/TinyLlama-1.1B-Chat-v1.0"),
                        "Qwen/Qwen2.5-1.5B-Instruct",
                    ])
                    last_error: Optional[Exception] = None
                    for fb in candidates:
                        try:
                            log_info(f"Falling back to {fb} with CPU settings")
                            fb_kwargs = {
                                "trust_remote_code": True,
                                "dtype": torch.float32,
                                "attn_implementation": "eager",
                            }
                            if cache_dir:
                                fb_kwargs["cache_dir"] = cache_path_str
                            if hf_token:
                                fb_kwargs["token"] = hf_token
                            self.model = AutoModelForCausalLM.from_pretrained(fb, **fb_kwargs)
                            model_name = fb
                            last_error = None
                            break
                        except Exception as fb_err:
                            last_error = fb_err
                            log_info(f"Fallback {fb} failed: {fb_err}")
                            continue
                    if last_error is not None:
                        raise last_error
            
            log_debug("Creating pipeline...")
            # Select device based on actual model device
            model_device = getattr(getattr(self.model, "device", None), "type", None)
            if model_device == "cuda":
                pipeline_device = 0
            else:
                pipeline_device = -1
            if self.model is not None and self.tokenizer is not None:
                self.pipeline = pipeline(
                    "text-generation",
                    model=self.model,
                    tokenizer=self.tokenizer,
                    device=pipeline_device,
                )
            
            total_time = time.time() - start_time
            log_info(f"Model {model_name} loaded successfully in {total_time:.2f}s")
            device_type = getattr(getattr(self.model, "device", None), "type", None)
            log_debug(f"Device: {device_type or ('CUDA' if torch.cuda.is_available() else 'CPU')}" )
        except Exception as e:
            log_info(f"Error loading model {model_name}: {e}")
            self.model = None
            self.pipeline = None
            if not self.remote_enabled:
                log_info("Falling back to rule-based verification")

    def _generate_text(self, prompt: str, max_new_tokens: int = 48) -> str:
        if self.remote_enabled and self.remote_model_id:
            url = f"https://api-inference.huggingface.co/models/{self.remote_model_id}"
            headers = {"Authorization": f"Bearer {self.remote_api_token}"} if self.remote_api_token else {}
            payload = {"inputs": prompt, "parameters": {"max_new_tokens": max_new_tokens, "temperature": 0}}
            try:
                resp = requests.post(url, headers=headers or None, json=payload, timeout=60)
                if resp.ok:
                    data = resp.json()
                    if isinstance(data, list) and data and isinstance(data[0], dict) and "generated_text" in data[0]:
                        return data[0]["generated_text"]
                    if isinstance(data, dict) and "generated_text" in data:
                        return data["generated_text"]
            except Exception:
                pass
        if self.pipeline:
            result = self.pipeline(
                prompt,
                return_full_text=False,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                use_cache=True,
                num_beams=1,
                max_time=45,
            )
            try:
                if isinstance(result, list) and result:
                    first = result[0]
                    if isinstance(first, dict):
                        return first.get("generated_text") or first.get("text") or ""
                    if isinstance(first, str):
                        return first
                return ""
            except Exception:
                return ""
        return ""

    def _clean_model_output(self, generated_text: str) -> str:
        s = (generated_text or "").strip()
        s = s.replace("```json", "```")
        s = s.replace("```", "")
        start = s.find("{")
        if start < 0:
            return "{}"
        depth = 0
        in_str = False
        esc = False
        end_idx = -1
        for i in range(start, len(s)):
            c = s[i]
            if in_str:
                if esc:
                    esc = False
                elif c == "\\":
                    esc = True
                elif c == '"':
                    in_str = False
            else:
                if c == '"':
                    in_str = True
                elif c == "{":
                    depth += 1
                elif c == "}":
                    depth -= 1
                    if depth == 0:
                        end_idx = i
                        break
        if end_idx >= start:
            s = s[start:end_idx + 1]
        return s
    def _quick_rule_check(self, kind: str, content: str) -> Dict[str, Any]:
        errs: List[str] = []
        warns: List[str] = []
        suggs: List[str] = []
        text = (content or "")
        if kind == "openapi":
            try:
                obj = json.loads(text)
                if not isinstance(obj, dict) or "paths" not in obj:
                    warns.append("Отсутствует раздел paths")
                else:
                    if not obj.get("paths"):
                        warns.append("paths пуст")
            except Exception:
                errs.append("OpenAPI JSON невалиден")
        elif kind == "bpmn":
            if "<bpmn" not in text or "</bpmn:definitions>" not in text:
                errs.append("BPMN XML невалиден")
        elif kind == "puml":
            if "@startuml" not in text:
                warns.append("PUML диаграмма не найдена")
        status = "error" if errs else ("warning" if warns else "ok")
        summary = self._generate_summary(errs, warns, suggs)
        return {"status": status, "errors": errs, "warnings": warns, "suggestions": suggs, "summary": summary}

    def _analyze_single(self, kind: str, content: str) -> Dict[str, Any]:
        if not (self.pipeline or (self.remote_enabled and self.remote_model_id)):
            return self._quick_rule_check(kind, content)
        trim = (content or "")[:2000]
        prompt = (
            ("Проанализируй только OpenAPI спецификацию.\n" if kind == "openapi" else "") +
            ("Проанализируй только BPMN модель.\n" if kind == "bpmn" else "") +
            ("Проанализируй только PUML диаграмму.\n" if kind == "puml" else "") +
            "Верни JSON с ключами errors, warnings, suggestions. Если ошибок и предупреждений нет, обязательно добавь минимум 3 практические рекомендации.\n\n---\n\n" + trim + "\n---\n"
        )
        log_info(f"Generating analysis for {kind} (len={len(trim)})")
        out = self._generate_text(prompt, max_new_tokens=128)
        log_info(f"Generated text len={len(out)} for {kind}")
        try:
            cleaned = self._clean_model_output(out)
            data = json.loads(cleaned)
            errs = data.get("errors") or []
            warns = data.get("warnings") or []
            suggs = data.get("suggestions") or []
            if not errs and not warns and not suggs:
                suggs = [
                    "Проверить единообразие наименований и описаний",
                    "Добавить строгие схемы и примеры для ключевых сущностей",
                    "Уточнить обработку ошибок и статусные коды",
                ]
            status = "error" if len(errs) > 0 else ("warning" if len(warns) > 0 else "ok")
            summary = self._generate_summary(errs, warns, suggs)
            return {"status": status, "errors": errs, "warnings": warns, "suggestions": suggs, "summary": summary}
        except Exception:
            return self._quick_rule_check(kind, content)

    def analyze_split(self, bpmn_content: str, openapi_content: str, puml_content: str) -> Dict[str, Any]:
        o = self._analyze_single("openapi", openapi_content or "")
        b = self._analyze_single("bpmn", bpmn_content or "")
        total_errors = len(o.get("errors") or []) + len(b.get("errors") or [])
        total_warnings = len(o.get("warnings") or []) + len(b.get("warnings") or [])
        total_suggestions = len(o.get("suggestions") or []) + len(b.get("suggestions") or [])
        overall = "error" if total_errors > 0 else ("warning" if total_warnings > 0 else "ok")
        # snake_case для совместимости с Jackson SNAKE_CASE
        return {
            "openapi": o,
            "bpmn": b,
            "overall_status": overall,
            "total_errors": total_errors,
            "total_warnings": total_warnings,
            "total_suggestions": total_suggestions,
        }

    def analyze_legacy(self, bpmn_content: str, openapi_content: str, puml_content: str) -> Dict[str, Any]:
        bpmn_trim = (bpmn_content or "")[:3000]
        openapi_trim = (openapi_content or "")[:3000]
        puml_trim = (puml_content or "")[:3000]
        prompt = (
            "Проанализируй BPMN, OpenAPI и PUML совместно. Выяви ошибки, предупреждения, рекомендации и несоответствия между артефактами.\n"
            "Если нет ошибок и предупреждений, обязательно верни минимум 3 содержательные рекомендации по улучшению.\n\n"
            "Верни JSON формата:\n"
            "{\n  \"openapi\": { \"errors\": [], \"warnings\": [], \"suggestions\": [], \"status\": \"ok|warning|error\", \"summary\": \"...\" },\n"
            "  \"bpmn\": { \"errors\": [], \"warnings\": [], \"suggestions\": [], \"status\": \"ok|warning|error\", \"summary\": \"...\" }\n}\n\n"
            "BPMN:\n---\n" + bpmn_trim + "\n---\n\n"
            "OpenAPI:\n---\n" + openapi_trim + "\n---\n\n"
            "PUML:\n---\n" + puml_trim + "\n---\n"
        )
        out = self._generate_text(prompt, max_new_tokens=256)
        try:
            cleaned = self._clean_model_output(out)
            data = json.loads(cleaned)
            o = data.get("openapi") or {}
            b = data.get("bpmn") or {}
            for sect in (o, b):
                sect.setdefault("errors", [])
                sect.setdefault("warnings", [])
                sect.setdefault("suggestions", [])
                # обязательный набор рекомендаций, если всё пусто
                if not sect.get("errors") and not sect.get("warnings") and not sect.get("suggestions"):
                    sect["suggestions"] = [
                        "Проверить единообразие наименований и описаний",
                        "Добавить примеры и строгие схемы для ключевых сущностей",
                        "Уточнить обработку ошибок и статусные коды",
                    ]
                # вычисляем статус
                if sect.get("errors"):
                    sect["status"] = "error"
                elif sect.get("warnings"):
                    sect["status"] = "warning"
                else:
                    sect["status"] = "ok"
                # формируем summary
                if not sect.get("summary"):
                    e = len(sect.get("errors") or [])
                    w = len(sect.get("warnings") or [])
                    s = len(sect.get("suggestions") or [])
                    sect["summary"] = f"{e} ошибок, {w} предупреждений, {s} рекомендаций"
            total_errors = len(o["errors"]) + len(b["errors"])
            total_warnings = len(o["warnings"]) + len(b["warnings"])
            total_suggestions = len(o["suggestions"]) + len(b["suggestions"])
            overall = "error" if total_errors > 0 else ("warning" if total_warnings > 0 else "ok")
            return {
                "openapi": o,
                "bpmn": b,
                "overall_status": overall,
                "total_errors": total_errors,
                "total_warnings": total_warnings,
                "total_suggestions": total_suggestions,
            }
        except Exception:
            return self.analyze_split(bpmn_content, openapi_content, puml_content)
    
    
    def _generate_summary(self, errors: List[str], warnings: List[str], suggestions: List[str]) -> str:
        """Генерирует краткое резюме проверки"""
        parts = []
        if errors:
            parts.append(f"Найдено {len(errors)} ошибок")
        if warnings:
            parts.append(f"{len(warnings)} предупреждений")
        if suggestions:
            parts.append(f"{len(suggestions)} рекомендаций")
        
        if not parts:
            return "Проблем не обнаружено"
        
        return ", ".join(parts)
    
    


def main():
    if len(sys.argv) < 2:
        print("Usage: python file_verification_service.py <openapi_file> [bpmn_file] [puml_file]")
        sys.exit(1)
    service = FileVerificationService()
    args = sys.argv[1:]
    openapi_content = None
    bpmn_content = None
    puml_content = None
    if args:
        with open(args[0], 'r', encoding='utf-8') as f:
            openapi_content = f.read()
    if len(args) > 1:
        with open(args[1], 'r', encoding='utf-8') as f:
            bpmn_content = f.read()
    if len(args) > 2:
        with open(args[2], 'r', encoding='utf-8') as f:
            puml_content = f.read()
    if openapi_content is None:
        print(json.dumps({"errors":["OpenAPI не указан"],"warnings":[],"suggestions":[],"cross_consistency_issues":[]}, ensure_ascii=False))
        sys.exit(1)
    profile = os.getenv("AI_VERIFICATION_PROFILE", "legacy").lower()
    if profile == "legacy":
        out = service.analyze_legacy(bpmn_content or "", openapi_content or "", puml_content or "")
    else:
        out = service.analyze_split(bpmn_content or "", openapi_content or "", puml_content or "")
    print(json.dumps(out, ensure_ascii=False))


if __name__ == "__main__":
    main()

