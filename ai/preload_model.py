#!/usr/bin/env python3
"""
Скрипт для предзагрузки модели ИИ перед запуском приложения.
Проверяет наличие модели в кэше и загружает её, если отсутствует.
"""

import os
import sys
import time
import types
import importlib.abc
import importlib.util
import importlib.machinery
from pathlib import Path
import platform

# Флаг для подробного логирования
os.environ.setdefault("DISABLE_TRITON", "1")
# Заглушка для импортов triton и triton.* в моделях, когда DISABLE_TRITON=1
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
VERBOSE = os.getenv("AI_VERIFICATION_VERBOSE", "false").lower() == "true"

def log_info(message: str):
    """Выводит информационное сообщение"""
    print(f"[INFO] {message}", flush=True)

def log_debug(message: str):
    """Выводит отладочное сообщение"""
    if VERBOSE:
        print(f"[DEBUG] {message}", flush=True)

def check_model_exists(model_name: str) -> bool:
    """
    Проверяет, существует ли модель в кэше Hugging Face
    
    Args:
        model_name: Имя модели (например, "Qwen/Qwen2.5-0.5B-Instruct")
        
    Returns:
        True если модель существует, False иначе
    """
    try:
        # Получаем путь к кэшу
        cache_dir = os.getenv("HF_HOME") or os.getenv("TRANSFORMERS_CACHE")
        if cache_dir:
            # Если указан HF_HOME, проверяем, нужно ли добавить /hub
            cache_path = Path(cache_dir)
            if not (cache_path / "hub").exists() and cache_path.exists():
                # Если есть файлы напрямую в cache_dir, используем его
                cache_path = cache_path
            else:
                cache_path = cache_path / "hub"
        else:
            cache_path = Path.home() / ".cache" / "huggingface" / "hub"
        
        log_info(f"Checking cache directory: {cache_path}")
        
        # Преобразуем имя модели в путь кэша
        # Hugging Face использует формат: models--{org}--{model}
        cache_model_name = model_name.replace("/", "--")
        model_cache_path = cache_path / f"models--{cache_model_name}"
        
        log_info(f"Model cache path: {model_cache_path}")
        
        # Проверяем наличие директории модели
        if model_cache_path.exists():
            # Проверяем наличие файлов модели
            snapshots = list(model_cache_path.glob("snapshots/*"))
            if snapshots:
                # Проверяем наличие основных файлов
                has_config = any((model_cache_path / "snapshots").glob("*/config.json"))
                has_model = any((model_cache_path / "snapshots").glob("*/pytorch_model*.bin")) or \
                           any((model_cache_path / "snapshots").glob("*/model.safetensors")) or \
                           any((model_cache_path / "snapshots").glob("*/model*.safetensors"))
                
                if has_config and has_model:
                    log_info(f"Model {model_name} found in cache at {model_cache_path}")
                    return True
                else:
                    log_info(f"Model cache incomplete (config: {has_config}, model: {has_model})")
        
        log_info(f"Model {model_name} not found in cache")
        return False
        
    except Exception as e:
        log_info(f"Error checking model: {e}")
        import traceback
        log_debug(f"Traceback: {traceback.format_exc()}")
        return False

def preload_model(model_name: str, verify: bool | None = None) -> bool:
    """
    Загружает модель в кэш, если её нет
    
    Args:
        model_name: Имя модели для загрузки
        
    Returns:
        True если модель успешно загружена или уже существует, False при ошибке
    """
    try:
        from transformers import AutoTokenizer, AutoModelForCausalLM
        import torch
        
        log_info(f"Preloading model: {model_name}")
        
        # Проверяем, есть ли модель уже в кэше
        exists_in_cache = check_model_exists(model_name)
        if exists_in_cache:
            log_info(f"Model {model_name} already exists in cache, will verify and warm up")
        
        # Получаем путь к кэшу для сохранения
        cache_dir = os.getenv("HF_HOME") or os.getenv("TRANSFORMERS_CACHE")
        if cache_dir:
            cache_path = Path(cache_dir)
            # Если HF_HOME указывает на корень, добавляем /hub
            if not cache_path.name == "hub":
                cache_path = cache_path / "hub"
            cache_path_str = str(cache_path)
        else:
            cache_path_str = None  # Используем стандартный кэш
        
        if not exists_in_cache:
            log_info(f"Model {model_name} not found, downloading from Hugging Face Hub...")
            log_info("This may take several minutes depending on model size and internet speed...")
            if cache_path_str:
                log_info(f"Model will be cached in: {cache_path_str}")
        
        start_time = time.time()
        
        # Загружаем токенизатор
        log_info("Downloading tokenizer...")
        tokenizer_start = time.time()
        tokenizer_kwargs = {"trust_remote_code": True}
        if cache_path_str:
            tokenizer_kwargs["cache_dir"] = cache_path_str
        if exists_in_cache:
            tokenizer_kwargs["local_files_only"] = True
        tokenizer = AutoTokenizer.from_pretrained(model_name, **tokenizer_kwargs)
        log_info(f"Tokenizer downloaded in {time.time() - tokenizer_start:.2f}s")
        
        # Загружаем модель
        log_info("Downloading model (this may take a while)...")
        model_start = time.time()
        model_kwargs = {
            "trust_remote_code": True,
        }
        hf_token = os.getenv("HF_TOKEN")

        # Определяем необходимость форсировать CPU на Windows/несовместимой CUDA
        force_cpu_env = os.getenv("CPU_ONLY") == "1" or os.getenv("FORCE_CPU") == "1"
        is_windows = platform.system().lower() == "windows"
        cuda_supported_caps = {50, 60, 61, 70, 75, 80, 86, 90}
        force_cpu_cap = False
        try:
            if torch.cuda.is_available():
                major, minor = torch.cuda.get_device_capability(0)
                sm = major * 10 + minor
                if sm not in cuda_supported_caps:
                    force_cpu_cap = True
                    log_info(f"CUDA sm_{sm} not supported by current PyTorch build; forcing CPU mode")
        except Exception:
            pass

        force_cpu = force_cpu_env or is_windows or force_cpu_cap
        if torch.cuda.is_available() and not force_cpu and os.getenv("DISABLE_TRITON") != "1":
            model_kwargs["dtype"] = torch.float16
            model_kwargs["device_map"] = "cuda"
            model_kwargs["attn_implementation"] = "sdpa"
        else:
            model_kwargs["dtype"] = torch.float32
            model_kwargs["device_map"] = "cpu"
            model_kwargs["attn_implementation"] = "eager"
        if cache_path_str:
            model_kwargs["cache_dir"] = cache_path_str
        if hf_token:
            model_kwargs["token"] = hf_token
        if exists_in_cache:
            model_kwargs["local_files_only"] = True
        try:
            model = AutoModelForCausalLM.from_pretrained(model_name, **model_kwargs)
        except Exception as e:
            err_str = str(e)
            log_info(f"Error loading model {model_name}: {err_str}")
            # Fallback: if FP8/Thinking or missing triton/CUDA issues, try 4B Instruct on CPU
            if ("triton" in err_str) or ("CUDA" in err_str) or ("FP8" in model_name or "Thinking" in model_name) or force_cpu:
                fallback_name = os.getenv("QWEN_CPU_MODEL") or os.getenv("CPU_MODEL_1B", "TinyLlama/TinyLlama-1.1B-Chat-v1.0")
                log_info(f"Falling back to {fallback_name} with CPU settings")
                fallback_kwargs = {
                    "trust_remote_code": True,
                    "dtype": torch.float32,
                    "attn_implementation": "eager",
                    "device_map": "cpu",
                }
                if cache_path_str:
                    fallback_kwargs["cache_dir"] = cache_path_str
                if hf_token:
                    fallback_kwargs["token"] = hf_token
                model = AutoModelForCausalLM.from_pretrained(fallback_name, **fallback_kwargs)
                model_name = fallback_name
            else:
                raise
        log_info(f"Model downloaded in {time.time() - model_start:.2f}s")
        
        total_time = time.time() - start_time
        log_info(f"Model {model_name} successfully preloaded in {total_time:.2f}s")
        
        # Проверяем, что модель действительно сохранена
        if check_model_exists(model_name):
            log_info("Model verified in cache")
        else:
            log_info("Warning: Model may not be properly cached")
        
        # Небольшая проверка инференса по желанию
        try:
            do_verify = verify if verify is not None else (os.getenv("VERIFY_INFERENCE", "true").lower() == "true")
            if do_verify:
                log_info("Verifying inference with a short prompt...")
                prompt = os.getenv("PRELOAD_TEST_PROMPT", "Hello, how are you?")
                inputs = tokenizer(prompt, return_tensors="pt")
                if torch.cuda.is_available() and os.getenv("CPU_ONLY") != "1" and os.getenv("DISABLE_TRITON") != "1":
                    inputs = {k: v.to("cuda") for k, v in inputs.items()}
                gen = model.generate(**inputs, max_new_tokens=16, do_sample=False)
                _out = tokenizer.decode(gen[0], skip_special_tokens=True)
                log_debug(f"Inference output: {_out[:200]}")
                log_info("Inference verification passed")
        except Exception as e:
            log_info(f"Inference verification failed: {e}")
            import traceback
            log_debug(f"Traceback: {traceback.format_exc()}")

        # Освобождаем память
        try:
            del model
            del tokenizer
            import gc
            gc.collect()
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
        except Exception:
            pass
        
        return True
        
    except ImportError:
        log_info("transformers library not available, cannot preload model")
        log_info("Install dependencies: pip install -r requirements.txt")
        return False
    except Exception as e:
        log_info(f"Error preloading model {model_name}: {e}")
        import traceback
        log_debug(f"Traceback: {traceback.format_exc()}")
        return False

def main():
    """Главная функция"""
    # Получаем имя модели из переменной окружения или определяем автоматически
    # Приоритет выбора модели из окружения:
    # 1. REMOTE_MODEL_ID (передаётся из бекенда при выборе модели на клиенте)
    # 2. QWEN_MODEL_NAME (GPU модель)
    # 3. QWEN_CPU_MODEL (CPU модель)
    env_model = os.getenv("REMOTE_MODEL_ID") or os.getenv("QWEN_MODEL_NAME") or os.getenv("QWEN_CPU_MODEL")
    
    log_info("=" * 60)
    log_info("AI Model Preloader")
    log_info("=" * 60)
    
    # Проверяем наличие библиотек
    try:
        import transformers
        import torch
        log_info(f"transformers version: {transformers.__version__}")
        log_info(f"torch version: {torch.__version__}")
        log_info(f"CUDA available: {torch.cuda.is_available()}")
    except ImportError as e:
        log_info(f"Required libraries not available: {e}")
        log_info("Install dependencies: pip install -r requirements.txt")
        sys.exit(1)
    
    # Определяем модель с учётом доступности GPU
    def _select_model_name(env_model_value: str | None) -> str:
        try:
            import torch
            has_cuda = torch.cuda.is_available()
        except Exception:
            has_cuda = False

        if env_model_value:
            name = env_model_value
            if ("FP8" in name or "Thinking" in name) and not has_cuda:
                log_info("CUDA недоступна: переключаюсь на Qwen/Qwen2.5-0.5B-Instruct")
                return "Qwen/Qwen2.5-0.5B-Instruct"
            return name

        return "Qwen/Qwen2.5-0.5B-Instruct"

    model_name = _select_model_name(env_model)

    log_info(f"Target model: {model_name}")

    # Предзагружаем модель
    success = preload_model(model_name)
    
    if success:
        log_info("=" * 60)
        log_info("Model preload completed successfully")
        log_info("=" * 60)
        sys.exit(0)
    else:
        log_info("=" * 60)
        log_info("Model preload failed")
        log_info("=" * 60)
        sys.exit(1)

if __name__ == "__main__":
    main()
