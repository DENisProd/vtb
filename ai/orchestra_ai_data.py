import json
import sys
import random
import re
from copy import deepcopy
from typing import Dict, Any, List

# Optional HF
try:
    from transformers import pipeline, AutoTokenizer, AutoModelForSeq2SeqLM
    HF_AVAILABLE = True
except Exception:
    HF_AVAILABLE = False

# ========== Настройки ==========
HF_MODEL_NAME = "google/flan-t5-small"  # рекомендую: "google/flan-t5-small" или "google/flan-t5-base"
USE_HF = True  # менять если вы точно не хотите ML-часть
# ==============================

# ---- Утилиты простого rule-based генератора ----
def _rand_alpha(n=6):
    return ''.join(random.choice('abcdefghijklmnopqrstuvwxyz') for _ in range(n))

def _rand_digits(n=8):
    return ''.join(random.choice('0123456789') for _ in range(n))

def make_value_for_param(name: str, param_spec: Dict[str, Any]=None):
    """Простая эвристика для генерации значения по имени параметра."""
    name = (name or "").lower()
    if 'id' in name and not name.startswith('client_'):
        return f"{_rand_alpha(3)}-{_rand_digits(6)}" #978-348384
    if 'account' in name:
        return f"acc-{_rand_digits(6)}" # acc-123456
    if 'card' in name:
        return f"{_rand_digits(16)}" # 1234567890123456
    if 'phone' in name:
        return f"+7{_rand_digits(10)}"
    if 'date' in name:
        return "2025-11-01"
    if 'time' in name:
        return "12:00:00"
    if 'amount' in name or 'sum' in name or 'price' in name:
        return round(random.uniform(1, 1000), 2)
    if 'email' in name:
        return f"{_rand_alpha(6)}@example.com"
    if 'passport' in name:
        return "1234 567890"
    if param_spec:
        t = param_spec.get('type') or param_spec.get('schema', {}).get('type')
        if t == 'integer':
            mn = param_spec.get('minimum', 1) if isinstance(param_spec.get('minimum', None), int) else 1
            return random.randint(mn, mn + 1000)
        if t == 'boolean':
            return random.choice([True, False])
    # default string
    return _rand_alpha(8)

def fill_request_data_task(task: Dict[str, Any]) -> Dict[str, Any]:
    """
    Создаёт requestData на основе task.requiredFields (параметры + body),
    или по heuristics, если requiredFields пуст.
    Формат requestData: { "query": {...}, "path": {...}, "headers": {...}, "body": {...} }
    """
    out = {"query": {}, "path": {}, "headers": {}, "body": None}
    rf = task.get('requiredFields') or {}
    params = rf.get('parameters') or []

    for p in params:
        name = p.get('name')
        location = (p.get('in') or 'query').lower()
        val = make_value_for_param(name, p)
        if location == 'query':
            out['query'][name] = val
        elif location == 'path':
            out['path'][name] = val
        elif location == 'header':
            out['headers'][name] = val
        elif location == 'body':
            out['body'] = val

    # Если body отсутствует и задача метод POST/PUT — попробуем сгенерировать простой body
    method = (task.get('method') or '').upper()
    if out['body'] is None and method in ('POST', 'PUT', 'PATCH'):
        # придумать небольшой object
        out['body'] = {"id": _rand_alpha(3) + "-" + _rand_digits(4), "note": "auto-generated"}
    return out

# ---- HF wrapper ----
class HFPolisher:
    def __init__(self, model_name=HF_MODEL_NAME):
        if not HF_AVAILABLE:
            raise RuntimeError("transformers not available")
        # Build pipeline
        # Using text2text-generation; we'll give строго отформатированный prompt и требовать JSON
        self.model_name = model_name
        self.pipe = pipeline("text2text-generation", model=model_name, tokenizer=model_name)

    def polish_variant(self, raw_variant: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Просим модель сгенерировать requestData для каждого таска.
        Мы склеиваем задачи в промпт и просим вернуть JSON-список с полем requestData для каждой записи.
        Возвращаем либо разобранный JSON, либо оригинал (если парсинг не прошёл).
        """
        # формируем input для модели: кратко описать задачи и нужный выход
        prompt_parts = ["Generate JSON array. For each input task produce field 'requestData' with structure {query, path, headers, body}."]
        prompt_parts.append("Input tasks:")
        for t in raw_variant:
            prompt_parts.append(json.dumps({
                "taskId": t.get("taskId"),
                "taskName": t.get("taskName"),
                "method": t.get("method"),
                "path": t.get("path"),
                "requiredFields": t.get("requiredFields")
            }, ensure_ascii=False))
        prompt_parts.append("Output only valid JSON. Example element: {\"taskId\":\"...\",\"requestData\":{\"query\":{},\"path\":{},\"headers\":{},\"body\":{}}}")
        prompt = "\n".join(prompt_parts)

        gen = self.pipe(prompt, max_length=1024, do_sample=False, num_return_sequences=1)[0]['generated_text']
        # Попробуем найти JSON внутри сгенерированного текста (на случай, если модель добавит пояснения)
        json_text = None
        # try direct parse
        try:
            parsed = json.loads(gen)
            return parsed
        except Exception:
            # try to extract first {...}.. possibly an array
            m = re.search(r'(\[.*\])', gen, flags=re.DOTALL)
            if m:
                candidate = m.group(1)
                try:
                    parsed = json.loads(candidate)
                    return parsed
                except Exception:
                    pass
        # если не получилось - вернём None (caller сделает fallback)
        return None

# ---- Main transform pipeline ----
def transform_generation(input_data: Dict[str, Any], use_hf=True) -> Dict[str, Any]:
    out = deepcopy(input_data)
    # remove requiredFields and add requestData
    variants = input_data.get('variants') or []
    new_variants = []
    # prepare HF polisher
    hf_polisher = None
    if use_hf and HF_AVAILABLE:
        try:
            hf_polisher = HFPolisher()
        except Exception as e:
            print("HF initialization failed:", e)
            hf_polisher = None

    for variant in variants:
        # variant is a list of tasks
        new_variant = []
        # First build rule-based requestData for each task
        for task in variant:
            task_copy = deepcopy(task)
            # remove requiredFields
            if 'requiredFields' in task_copy:
                task_copy.pop('requiredFields', None)
            # add placeholder requestData
            task_copy['requestData'] = fill_request_data_task(task)
            new_variant.append(task_copy)

        # Try ML polish per-variant if available
        if hf_polisher:
            polished = None
            try:
                polished = hf_polisher.polish_variant(variant)
            except Exception as e:
                print("HF polishing error:", e)
                polished = None
            if isinstance(polished, list) and len(polished) == len(new_variant):
                # merge polished requestData into new_variant when possible
                merged = []
                for base_task, polished_task in zip(new_variant, polished):
                    merged_task = deepcopy(base_task)
                    rd = polished_task.get('requestData') or {}
                    # sanitize: ensure keys exist
                    merged_task['requestData'] = {
                        "query": rd.get('query') or merged_task['requestData'].get('query') or {},
                        "path": rd.get('path') or merged_task['requestData'].get('path') or {},
                        "headers": rd.get('headers') or merged_task['requestData'].get('headers') or {},
                        "body": rd.get('body') if 'body' in rd else merged_task['requestData'].get('body')
                    }
                    merged.append(merged_task)
                new_variants.append(merged)
                continue  # next variant

        # no HF or HF failed -> use rule-based result
        new_variants.append(new_variant)

    out['variants'] = new_variants
    # remove other top-level requiredFields if exist
    if 'requiredFields' in out:
        out.pop('requiredFields', None)
    return out

# ---- CLI ----
def main():
    if len(sys.argv) < 2:
        print("Usage: python orchestra_ai_nn.py input.json [output.json]")
        sys.exit(1)
    in_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) >=3 else "generation-with-requests.json"
    with open(in_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    use_hf = USE_HF and HF_AVAILABLE
    if USE_HF and not HF_AVAILABLE:
        print("transformers not installed or import failed — falling back to rule-based generator.")
    if use_hf:
        print(f"HF available — will try to run model {HF_MODEL_NAME}")

    result = transform_generation(data, use_hf=use_hf)

    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print("Saved to", out_path)

if __name__ == "__main__":
    main()
