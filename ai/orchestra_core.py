"""
orchestra_ai.py

Минимальная, но практичная реализация:
- парсер OpenAPI (JSON)
- парсер BPMN (XML)
- сопоставление задач -> эндпоинты
- rule-based генерация сценариев и данных
- optional: seq2seq generator (HuggingFace) for scenario polishing (fine-tuneable)
- runner: выполняет сценарий и валидирует ответы

Запуск примера в конце файла демонстрирует работу на упрощённом "покупка билетов" процессе.
"""

import json
import os
from dotenv import load_dotenv; load_dotenv()
import xml.etree.ElementTree as ET
import random
import time
from typing import Dict, List, Any, Tuple
from copy import deepcopy
import re
from orchestra_ai_data import transform_generation

# Optional ML deps — подключаем только при наличии
try:
    from transformers import AutoTokenizer, AutoModelForSeq2SeqLM, pipeline
    HF_AVAILABLE = True
except Exception:
    HF_AVAILABLE = False

import requests  # для runner'а (в реальной системе можно mock-обертку)

# ----------------------------
# Utilities
# ----------------------------
def load_openapi_from_file(path: str) -> Dict[str, Any]:
    with open(path, 'r', encoding='utf-8') as f:
        text = f.read()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        print("Ошибка декодирования JSON!")

def load_bpmn_from_file(path: str) -> ET.ElementTree:
    if path.endswith(".json"):
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        # Преобразуем JSON в объект, похожий на BPMN
        tasks = []
        for variant in data.get("variants", []):
            for task in variant:
                tasks.append({
                    "id": task.get("taskId"),
                    "type": "serviceTask",
                    "name": task.get("taskName")
                })
        return {"tasks": tasks, "sequence": []}
    else:
        return ET.parse(path)

# ----------------------------
# Simple OpenAPI parser
# ----------------------------
def extract_endpoints(openapi: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    Возвращает список эндпоинтов вида:
    { 'path': '/orders', 'method': 'post', 'operationId': 'createOrder', 'summary': '...', 'requestBody': {...}, 'responses': {...} }
    """
    paths = openapi.get('paths', {})
    out = []
    for path, methods in paths.items():
        for method, info in methods.items():
            if not isinstance(info, dict):
                continue
            out.append({
                'path': path,
                'method': method.lower(),
                'operationId': info.get('operationId'),
                'summary': info.get('summary') or info.get('description'),
                'requestBody': info.get('requestBody'),
                'responses': info.get('responses'),
                'parameters': info.get('parameters', []),
                'raw': info
            })
    return out

# ----------------------------
# BPMN parser (very simple)
# ----------------------------
def extract_bpmn_tasks(tree: ET.ElementTree) -> List[Dict[str, Any]]:
    """
    Возвращает задачи и последовательность (sequenceFlow).
    Собираем элементы: userTask, serviceTask, startEvent, endEvent.
    """
    ns = {'bpmn': 'http://www.omg.org/spec/BPMN/20100524/MODEL'}
    root = tree.getroot()
    tasks = []
    # collect tasks by tag local name ignoring namespace
    for elem in root.iter():
        tag = elem.tag
        if '}' in tag:
            tag_local = tag.split('}', 1)[1]
        else:
            tag_local = tag
        if tag_local in ('userTask', 'serviceTask', 'startEvent', 'endEvent', 'task'):
            tasks.append({
                'id': elem.attrib.get('id'),
                'type': tag_local,
                'name': elem.attrib.get('name') or '',
                'raw': elem
            })
    # sequence flows: map sourceRef -> targetRef
    seq = []
    for sf in root.findall('.//{http://www.omg.org/spec/BPMN/20100524/MODEL}sequenceFlow'):
        seq.append({'id': sf.attrib.get('id'), 'source': sf.attrib.get('sourceRef'), 'target': sf.attrib.get('targetRef')})
    return {'tasks': tasks, 'sequence': seq}

# ----------------------------
# Matching BPMN tasks <-> OpenAPI endpoints
# ----------------------------
def match_tasks_to_endpoints(tasks: List[Dict[str, Any]], endpoints: List[Dict[str, Any]]) -> Dict[str, Any]:
    """
    Для каждой BPMN-задачи пытаемся найти подходящий endpoint по имени (fuzzy), operationId или summary.
    Возвращаем mapping task_id -> endpoint or candidates
    """
    mapping = {}
    for t in tasks:
        name = (t.get('name') or '').lower()
        candidates = []
        for ep in endpoints:
            text = ' '.join(filter(None, [ep.get('operationId') or '', ep.get('summary') or '', ep.get('path') or ''])).lower()
            # simple substring match or keywords
            if name and (name in text or any(word in text for word in name.split())):
                candidates.append(ep)
        # fallback: if no matches by name, try by HTTP-verbs heuristic
        if not candidates:
            for ep in endpoints:
                if any(k in (ep.get('operationId') or '').lower() for k in name.split()):
                    candidates.append(ep)
        mapping[t['id']] = {'task': t, 'candidates': candidates}
    return mapping

# ----------------------------
# Rule-based data generator from JSON Schema-ish fragment
# ----------------------------
def sample_from_schema(schema: Dict[str, Any], context: Dict[str, Any]=None) -> Any:
    """
    Простая генерация значений из JSON schema-like dict.
    Поддерживает типы: string, integer, boolean, object, array, enum, format: date, date-time
    Context используется для подстановки зависимых значений.
    """
    if context is None:
        context = {}
    if schema is None:
        return None
    t = schema.get('type')
    if 'enum' in schema:
        return random.choice(schema['enum'])
    if t == 'string' or (not t and 'properties' not in schema):
        fmt = schema.get('format','')
        if fmt == 'date':
            return "2025-01-01"
        if fmt == 'date-time':
            return "2025-01-01T12:00:00Z"
        pattern = schema.get('pattern')
        if pattern:
            # naive numeric pattern handling
            digits = re.findall(r'\\d\{(\d+)\}', pattern)
            if digits:
                n = int(digits[0])
                return ''.join(str(random.randrange(10)) for _ in range(n))
        # lengthhint
        minlen = schema.get('minLength', 3)
        # if name suggests passport/phone/etc
        title = (schema.get('title') or '').lower()
        if 'passport' in title or 'passport' in schema.get('description','').lower():
            return "1234 567890"
        return ''.join(random.choice('abcdefghijklmnopqrstuvwxyz') for _ in range(min(minlen,8)))
    if t == 'integer':
        mn = schema.get('minimum', 0)
        mx = schema.get('maximum', mn + 1000)
        return random.randint(mn, mx)
    if t == 'number':
        mn = schema.get('minimum', 0.0)
        mx = schema.get('maximum', mn + 1000.0)
        return round(random.uniform(mn, mx), 2)
    if t == 'boolean':
        return random.choice([True, False])
    if t == 'object' or 'properties' in schema:
        res = {}
        for k, v in (schema.get('properties') or {}).items():
            # required - more likely to include
            res[k] = sample_from_schema(v, context)
            # if property name looks like id, and context has something -> relay
            if k.lower().endswith('id') and k in context:
                res[k] = context[k]
        return res
    if t == 'array':
        it = schema.get('items', {})
        n = schema.get('minItems', 1)
        return [sample_from_schema(it, context) for _ in range(n)]
    # fallback
    return None

# ----------------------------
# Scenario generator (rule-based + optional ML polish)
# ----------------------------
class ScenarioGenerator:
    def __init__(self, use_ml=False, hf_model_name: str=None):
        self.use_ml = use_ml and HF_AVAILABLE and hf_model_name is not None
        self.hf_model_name = hf_model_name
        self.hf_pipe = None
        if self.use_ml:
            tokenizer = AutoTokenizer.from_pretrained(hf_model_name)
            model = AutoModelForSeq2SeqLM.from_pretrained(hf_model_name)
            self.hf_pipe = pipeline("text2text-generation", model=model, tokenizer=tokenizer)

    def build_prompt_from_process(self, bpmn_tasks: List[Dict[str, Any]], mapping: Dict[str, Any]) -> str:
        """
        Собираем контекстный текст (prompt) для ML-модуля: задачи процесса + candidate endpoints (описания).
        """
        parts = []
        parts.append("Process tasks:")
        for t in bpmn_tasks:
            parts.append(f"- {t.get('id')}: {t.get('name')}")
            cand = mapping[t['id']]['candidates']
            if cand:
                parts.append("  candidates:")
                for c in cand:
                    parts.append(f"    * {c.get('method').upper()} {c.get('path')} : {c.get('operationId')} - {c.get('summary')}")
        return "\n".join(parts)

    def generate_scenario(self, process_name: str, bpmn_tasks: List[Dict[str, Any]], mapping: Dict[str, Any], openapi_endpoints: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Возвращает сценарий: список шагов с endpoint, request payload (sampled), expected checks.
        """
        # rule-based base scenario
        scenario = {'name': process_name, 'steps': []}
        shared_context = {}  # place to store ids etc.

        # iterate over tasks in order as given
        for t in bpmn_tasks:
            info = mapping.get(t['id'], {})
            cands = info.get('candidates', [])
            chosen = cands[0] if cands else None
            step = {'taskId': t['id'], 'taskName': t['name'], 'endpoint': None, 'request': None, 'expected': None}
            if chosen:
                step['endpoint'] = {
                    'path': chosen['path'], 
                    'method': chosen['method'], 
                    'operationId': chosen.get('operationId')
                    }
                # create request payload from schema if present
                input_data = {
                    "variants": [[{
                        "taskId": t['id'],
                        "taskName": t['name'],
                        "method": chosen['method'].upper(),
                        "path": chosen['path'],
                        "requiredFields": {
                            "parameters": chosen.get('parameters', []),
                            "body": chosen.get('requestBody')
                        }
                    }]]
                }
                enriched = transform_generation(input_data, use_hf=True)
                # результат — массив variants с заполненным requestData
                step['request'] = enriched['variants'][0][0]['requestData']['body']
                step['headers'] = enriched['variants'][0][0]['requestData']['headers']
                step['query'] = enriched['variants'][0][0]['requestData']['query']

                # остальное без изменений
                step['expected'] = {'status': 200}

            scenario['steps'].append(step)
        return scenario

# ----------------------------
# Runner: executes scenario (synchronous) and validates
# ----------------------------
class ScenarioRunner:
    def __init__(self, base_url_map: Dict[str, str] = None, timeout=10):
        """
        base_url_map: mapping service base path -> actual base URL
        """
        self.base_url_map = base_url_map or {}
        self.timeout = timeout
        self.context = {}  # здесь будем хранить токен и другие переменные

    def resolve_url(self, path: str) -> str:
        if path.startswith('http'):
            return path
        seg = path.strip('/').split('/')[0] if path.strip('/') else ''
        base = self.base_url_map.get(seg) or self.base_url_map.get('default') or 'http://localhost:8080'
        return base.rstrip('/') + path

    def run(self, scenario: Dict[str, Any]) -> Dict[str, Any]:
        logs = {'scenario': scenario.get('name'), 'steps': []}
        for step in scenario.get('steps', []):
            ep = step.get('endpoint')
            log = {'taskId': step.get('taskId'), 'taskName': step.get('taskName'), 'ok': False}
            if not ep:
                log['error'] = 'no endpoint'
                logs['steps'].append(log)
                continue

            url = self.resolve_url(ep['path'])
            method = ep['method'].lower()
            payload = step.get('request') or {}
            headers = {}
            params = {}

            # 🔹 если это шаг авторизации
            if "/auth/bank-token" in url:
                params["client_id"] = os.getenv("CLIENT_ID")  # можно вынести в .env
                params["client_secret"] = os.getenv("CLIENT_SECRET")
                print("🔑 Performing authentication...")

            # 🔹 если токен уже получен, добавляем его в заголовок
            if "access_token" in self.context:
                headers["Authorization"] = f"Bearer {self.context['access_token']}"

            try:
                # выполнение запроса
                if method == 'get':
                    r = requests.get(url, params=params or payload, headers=headers, timeout=self.timeout)
                else:
                    r = requests.request(method, url, params=params, json=payload, headers=headers, timeout=self.timeout)

                log['status_code'] = r.status_code
                try:
                    log['response'] = r.json()
                except Exception:
                    log['response'] = r.text[:200]

                # 🔹 если это авторизация — сохраняем токен
                if "/auth/bank-token" in url and r.status_code == 200:
                    token_data = r.json()
                    access_token = token_data.get("access_token")
                    if access_token:
                        self.context["access_token"] = access_token
                        print("✅ Token received and saved to context.")

                # базовая проверка статуса
                expected = step.get('expected', {})
                if expected and 'status' in expected:
                    log['expected_status'] = expected['status']
                    log['ok'] = (r.status_code == expected['status'])
                else:
                    log['ok'] = 200 <= r.status_code < 300

                # сохраняем контекстные id
                if isinstance(log['response'], dict):
                    for k, v in log['response'].items():
                        if k.lower().endswith('id'):
                            self.context[k] = v

            except Exception as e:
                log['error'] = str(e)

            logs['steps'].append(log)

        logs['context'] = self.context
        return logs



# ----------------------------
# If run as main — demo
# ----------------------------
if __name__ == "__main__":
    import argparse, json, requests
    parser = argparse.ArgumentParser(description="VTB Orchestra AI tester")
    parser.add_argument("--openapi-url", required=True, help="URL to OpenAPI spec")
    parser.add_argument("--bpmn-file", required=True, help="Path to BPMN file")
    args = parser.parse_args()

    openapi = requests.get(args.openapi_url).json()
    bpmn_tree = load_bpmn_from_file(args.bpmn_file)

    endpoints = extract_endpoints(openapi)
    bpmn_data = extract_bpmn_tasks(bpmn_tree)
    mapping = match_tasks_to_endpoints(bpmn_data["tasks"], endpoints)

    gen = ScenarioGenerator(use_ml=False)
    scenario = gen.generate_scenario("VTB Orchestra Process", bpmn_data["tasks"], mapping, endpoints)
    runner = ScenarioRunner(base_url_map={"default": "https://abank.open.bankingapi.ru"})
    result = runner.run(scenario)

    with open("report.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print("✅ Test completed. Report saved to report.json")
