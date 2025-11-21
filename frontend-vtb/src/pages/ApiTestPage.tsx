import { useState, useMemo } from "react";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
  Select,
  SelectItem,
  Spinner,
  Tabs,
  Tab,
  Accordion,
  AccordionItem,
} from "@heroui/react";
import {
  PlayCircleIcon,
  CheckCircleIcon,
  XCircleIcon,
  ExclamationTriangleIcon,
  ClockIcon,
} from "@heroicons/react/24/outline";

import { useTestFlowStore } from "@/stores/testflow-store";
import { executeTest } from "@/lib/testflow-api";
import type { TestExecutionResult, TestExecutionStepResult, ExecutionProblem, TestDataGenerationResult } from "@/types/testflow";

const ApiTestPage = () => {
  const mappingResult = useTestFlowStore((state) => state.mappingResult);
  const templates = useTestFlowStore((state) => state.templates);
  const activeTemplateId = useTestFlowStore((state) => state.activeTemplateId);
  const openApiJson = useTestFlowStore((state) => state.openApiJson);
  const bpmnXml = useTestFlowStore((state) => state.bpmnXml);
  const scenarios = useTestFlowStore((state) => state.scenarios);

  const [baseUrl, setBaseUrl] = useState("http://localhost:8080");
  const [variantIndex, setVariantIndex] = useState(0);
  const [stopOnFirstError, setStopOnFirstError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TestExecutionResult | null>(null);

  const activeTemplate = useMemo(
    () =>
      templates.find((template) => template.id === activeTemplateId) ??
      templates[0],
    [templates, activeTemplateId],
  );

  const handleExecute = async () => {
    if (!mappingResult || !activeTemplate || !openApiJson || !bpmnXml) {
      setError("Необходимы маппинг, тестовые данные, OpenAPI и BPMN");
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      // Преобразуем template в формат TestDataGenerationResult
      // Создаем варианты данных из контекстов шаблона
      const stepDataMap = new Map<string, {
        taskId: string;
        taskName: string;
        requestData?: Record<string, unknown>;
        queryParams?: Record<string, unknown>;
        responseData?: Record<string, unknown>;
      }>();

      // Группируем поля по шагам
      for (const ctx of activeTemplate.contexts) {
        if (ctx.scope === "step" && ctx.relatedStepId) {
          const existing = stepDataMap.get(ctx.relatedStepId) || {
            taskId: ctx.relatedStepId,
            taskName: ctx.label,
          };

          const requestDataField = ctx.fields.find((f) => f.key === "requestData");
          if (requestDataField?.value) {
            existing.requestData = requestDataField.value as Record<string, unknown>;
          }

          const queryParamsField = ctx.fields.find((f) => f.key === "queryParams");
          if (queryParamsField?.value) {
            existing.queryParams = queryParamsField.value as Record<string, unknown>;
          }

          const responseDataField = ctx.fields.find((f) => f.key === "responseData");
          if (responseDataField?.value) {
            existing.responseData = responseDataField.value as Record<string, unknown>;
          }

          stepDataMap.set(ctx.relatedStepId, existing);
        }
      }

      const testData: TestDataGenerationResult = {
        generationType: "CLASSIC",
        scenario: "positive",
        variants: [Array.from(stepDataMap.values())],
      };

      const testDataJson = JSON.stringify(testData);
      const mappingResultJson = JSON.stringify(mappingResult);

      const executionResult = await executeTest({
        bpmnXml,
        openApiJson,
        testDataJson,
        mappingResultJson,
        baseUrl,
        variantIndex,
        stopOnFirstError,
      });

      setResult(executionResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Ошибка выполнения теста");
    } finally {
      setLoading(false);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "SUCCESS":
        return "success";
      case "FAILED":
        return "danger";
      case "PARTIAL":
        return "warning";
      default:
        return "default";
    }
  };

  const getStepStatusColor = (status: string) => {
    switch (status) {
      case "SUCCESS":
        return "success";
      case "FAILED":
        return "danger";
      case "SKIPPED":
        return "default";
      default:
        return "default";
    }
  };

  return (
    <div className="space-y-5">
      <Card className="border border-primary/30 bg-primary/5">
        <CardHeader className="items-start gap-3">
          <div>
            <div className="text-sm font-semibold text-white">
              Автотестирование API
            </div>
            <div className="text-xs text-slate-400">
              Выполнение тестовых сценариев с валидацией ответов
            </div>
          </div>
        </CardHeader>
        <CardBody className="space-y-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Input
              label="Base URL"
              placeholder="https://vbank.open.bankingapi.ru/"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
            />
            <Input
              label="Индекс варианта данных"
              type="number"
              value={variantIndex.toString()}
              onChange={(e) => setVariantIndex(Number(e.target.value) || 0)}
            />
            <Select
              label="Остановка при ошибке"
              selectedKeys={[stopOnFirstError ? "true" : "false"]}
              onSelectionChange={(keys) => {
                const selected = Array.from(keys)[0] === "true";
                setStopOnFirstError(selected);
              }}
            >
              <SelectItem key="false">
                Нет
              </SelectItem>
              <SelectItem key="true">
                Да
              </SelectItem>
            </Select>
          </div>
          <Button
            color="primary"
            isDisabled={loading || !mappingResult || !activeTemplate || !bpmnXml}
            isLoading={loading}
            startContent={loading ? undefined : <PlayCircleIcon className="h-4 w-4" />}
            onPress={handleExecute}
          >
            {loading ? "Выполнение..." : "Запустить тесты"}
          </Button>
        </CardBody>
      </Card>

      {error && (
        <Card className="border border-danger/30 bg-danger/5">
          <CardBody>
            <div className="text-sm text-danger">{error}</div>
          </CardBody>
        </Card>
      )}

      {result && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
          <Card className="border border-white/10 lg:col-span-2">
            <CardHeader>
              <div>
                <div className="text-sm font-semibold text-white">
                  Результаты выполнения
                </div>
                <div className="text-xs text-slate-400">
                  {result.processName || "Тестовый процесс"}
                </div>
              </div>
              <Chip color={getStatusColor(result.status)} size="sm">
                {result.status}
              </Chip>
            </CardHeader>
            <CardBody>
              <Tabs>
                <Tab key="steps" title="Шаги">
                  <div className="mt-4 space-y-3">
                    {result.steps.map((step, index) => (
                      <StepResultCard key={step.taskId || index} step={step} />
                    ))}
                  </div>
                </Tab>
                <Tab key="problems" title={`Проблемы (${result.problems?.length || 0})`}>
                  <div className="mt-4 space-y-3">
                    {result.problems && result.problems.length > 0 ? (
                      result.problems.map((problem, index) => (
                        <ProblemCard key={index} problem={problem} />
                      ))
                    ) : (
                      <div className="text-sm text-slate-400">
                        Проблем не обнаружено
                      </div>
                    )}
                  </div>
                </Tab>
                <Tab key="statistics" title="Статистика">
                  {result.statistics && (
                    <div className="mt-4 space-y-3">
                      <div className="grid grid-cols-2 gap-4">
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-slate-400">Всего шагов</div>
                          <div className="text-lg font-semibold text-white">
                            {result.statistics.totalSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-slate-400">Успешных</div>
                          <div className="text-lg font-semibold text-success">
                            {result.statistics.successfulSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-slate-400">Ошибок</div>
                          <div className="text-lg font-semibold text-danger">
                            {result.statistics.failedSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-slate-400">Пропущено</div>
                          <div className="text-lg font-semibold text-slate-400">
                            {result.statistics.skippedSteps}
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-slate-400">Среднее время</div>
                          <div className="text-lg font-semibold text-white">
                            {Math.round(result.statistics.averageStepDurationMs)}ms
                          </div>
                        </div>
                        <div className="rounded-lg border border-white/10 p-3">
                          <div className="text-xs text-slate-400">Всего запросов</div>
                          <div className="text-lg font-semibold text-white">
                            {result.statistics.totalRequests}
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                </Tab>
              </Tabs>
            </CardBody>
          </Card>

          <Card className="border border-white/10">
            <CardHeader>
              <div className="text-sm font-semibold text-white">Общая информация</div>
            </CardHeader>
            <CardBody className="space-y-3">
              <div>
                <div className="text-xs text-slate-400">Статус</div>
                <Chip color={getStatusColor(result.status)} size="sm" className="mt-1">
                  {result.status}
                </Chip>
              </div>
              <div>
                <div className="text-xs text-slate-400">Длительность</div>
                <div className="text-sm font-semibold text-white">
                  {result.totalDurationMs}ms
                </div>
              </div>
              <div>
                <div className="text-xs text-slate-400">Начало</div>
                <div className="text-sm text-white">
                  {new Date(result.startTime).toLocaleString()}
                </div>
              </div>
              {result.endTime && (
                <div>
                  <div className="text-xs text-slate-400">Окончание</div>
                  <div className="text-sm text-white">
                    {new Date(result.endTime).toLocaleString()}
                  </div>
                </div>
              )}
            </CardBody>
          </Card>
        </div>
      )}
    </div>
  );
};

const StepResultCard = ({ step }: { step: TestExecutionStepResult }) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="rounded-lg border border-white/10 p-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Chip
            color={
              step.status === "SUCCESS"
                ? "success"
                : step.status === "FAILED"
                  ? "danger"
                  : "default"
            }
            size="sm"
          >
            {step.status}
          </Chip>
          <div>
            <div className="text-sm font-semibold text-white">
              {step.taskName || step.taskId}
            </div>
            <div className="text-xs text-slate-400">
              {step.durationMs}ms
            </div>
          </div>
        </div>
        <Button
          size="sm"
          variant="light"
          onPress={() => setExpanded(!expanded)}
        >
          {expanded ? "Свернуть" : "Детали"}
        </Button>
      </div>
      {expanded && (
        <div className="mt-3 space-y-2 border-t border-white/10 pt-3">
          {step.request && (
            <div>
              <div className="text-xs text-slate-400 mb-1">Запрос</div>
              <div className="text-xs text-white font-mono bg-black/40 p-2 rounded">
                <div>{step.request.method} {step.request.url}</div>
                {step.request.body && (
                  <pre className="mt-1 text-xs overflow-auto">
                    {step.request.body}
                  </pre>
                )}
              </div>
            </div>
          )}
          {step.response && (
            <div>
              <div className="text-xs text-slate-400 mb-1">Ответ</div>
              <div className="text-xs text-white font-mono bg-black/40 p-2 rounded">
                <div>Status: {step.response.statusCode} ({step.response.responseTimeMs}ms)</div>
                {step.response.body && (
                  <pre className="mt-1 text-xs overflow-auto">
                    {step.response.body}
                  </pre>
                )}
              </div>
            </div>
          )}
          {step.validation && (
            <div>
              <div className="text-xs text-slate-400 mb-1">Валидация</div>
              <div className="text-xs">
                <Chip
                  color={step.validation.isValid ? "success" : "danger"}
                  size="sm"
                >
                  {step.validation.isValid ? "Валидно" : "Ошибки"}
                </Chip>
                {step.validation.errors && step.validation.errors.length > 0 && (
                  <div className="mt-2 space-y-1">
                    {step.validation.errors.map((err, idx) => (
                      <div key={idx} className="text-danger text-xs">
                        • {err}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
          {step.errorMessage && (
            <div>
              <div className="text-xs text-danger">{step.errorMessage}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

const ProblemCard = ({ problem }: { problem: ExecutionProblem }) => {
  return (
    <div className="rounded-lg border border-danger/30 bg-danger/5 p-3">
      <div className="flex items-center gap-2 mb-2">
        <Chip
          color={problem.severity === "ERROR" ? "danger" : "warning"}
          size="sm"
        >
          {problem.severity}
        </Chip>
        <Chip size="sm" variant="flat">
          {problem.type}
        </Chip>
      </div>
      <div className="text-sm font-semibold text-white mb-1">
        {problem.stepName || problem.stepId}
      </div>
      <div className="text-xs text-slate-300 mb-2">{problem.message}</div>
      {problem.details && (
        <div className="text-xs text-slate-400 font-mono bg-black/40 p-2 rounded">
          {problem.details}
        </div>
      )}
      {problem.requestUrl && (
        <div className="text-xs text-slate-400 mt-2">
          {problem.requestMethod} {problem.requestUrl}
        </div>
      )}
    </div>
  );
};

export default ApiTestPage;

